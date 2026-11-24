package net.runelite.client.plugins.microbot.delveauto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveAutoState;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * Priority arbiter for the Doom of Mokhaiotl.
 *
 * <p>Every tick runs the same ordered list rather than one exclusive state machine, because the
 * mechanics overlap: rock throw lands during shockwave setup, prayers must continue while moving,
 * and eating cannot wait for a cast to finish.</p>
 *
 * <pre>
 * 1. prayers                 (never skipped)
 * 2. eat / prayer potion
 * 3. rock throw dodge        (last ticks of the fuse only)
 * 4. shockwave (volatile earth + earthen shield)
 * 5. beam melee punish       (swap weapon, hit, swap back)
 * 6. acid tile escape
 * 7. demonic larvae          (style swap, explode on the boss)
 * 8. Arceuus utility         (thrall upkeep, Death Charge)
 * 9. attack / keep distance
 * </pre>
 */
@Slf4j
public class DelveAutoScript extends Script
{
	private static final int[] BOSS_NPC_IDS = {
		NpcID.DOM_BOSS,
		NpcID.DOM_BOSS_SHIELDED,
		NpcID.DOM_BOSS_BURROWED
	};

	private static final long ATTACK_REARM_MS = 600;
	private static final long POST_FIGHT_DELAY_MS = 3000;
	private static final long BOSS_LOST_GRACE_MS = 3000;
	private static final long DESCEND_ACTION_THROTTLE_MS = 1200;
	private static final long PUNISH_ATTACK_RETRY_MS = 600;
	private static final long PUNISH_MAX_MS = 6000;
	private static final int ROCK_DODGE_TICKS = 2;
	private static final int MIN_BOSS_DISTANCE = 3;

	private static final int[] DESCEND_HOLE_IDS = {
		ObjectID1.DOM_DESCEND_HOLE,
		ObjectID1.DOM_DESCEND_HOLE_UNIQUE
	};

	private static final String DESCEND_CONFIRM_TEXT = "Are you sure you want to descend";

	private final DelveAutoConfig config;
	private final DelvePrayerService prayerService;

	@Getter
	private final DelveHazardTracker hazardTracker = new DelveHazardTracker();

	@Getter
	private final DelveMovement movement;

	@Getter
	private final DelveWeaponService weaponService = new DelveWeaponService();

	@Getter
	private final DelveMagicUtilityService magicUtilityService = new DelveMagicUtilityService();

	@Getter
	private final DelveLarvaService larvaService;

	@Getter
	private volatile DelveAutoState state = DelveAutoState.IDLE;

	@Getter
	private volatile int floorsCleared;

	private boolean sawBoss;
	private boolean postFightPending;
	private boolean descendPending;
	private boolean pausedAfterFight;
	private boolean punishingBeam;
	private boolean punishWarned;
	private boolean punishSpecialResolved;
	private long bossGoneSince;
	private long lastAttackTime;
	private long lastDescendAction;
	private long punishStartedAt;
	private long lastPunishAttackAt;

	@Inject
	public DelveAutoScript(DelveAutoConfig config, DelvePrayerService prayerService)
	{
		this.config = config;
		this.prayerService = prayerService;
		this.movement = new DelveMovement(config, hazardTracker);
		this.larvaService = new DelveLarvaService(movement, weaponService);
	}

	public boolean run()
	{
		reset();

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			try
			{
				if (!Microbot.isLoggedIn())
				{
					return;
				}
				if (!super.run())
				{
					return;
				}

				tick();
			}
			catch (Exception ex)
			{
				log.trace("Delve Auto tick failed", ex);
			}
		}, 0, 50, TimeUnit.MILLISECONDS);

		return true;
	}

	private void tick()
	{
		Rs2NpcModel boss = findBoss();

		// Hazards must keep updating even when the boss is gone (acid pools linger, shield NPCs can
		// be the last thing to despawn).
		hazardTracker.update(boss);

		// Finish any deferred weapon restore or special-attack release before the next decision.
		// The beam may have ended between ticks, so this cannot wait for a fight boundary. The
		// shielded form uses a sustained beam that we do not punish, so keep the restore running.
		if (!hazardTracker.isBeamCharging() || boss == null || boss.getId() == NpcID.DOM_BOSS_SHIELDED)
		{
			weaponService.tick();
		}

		if (boss == null || boss.isDead())
		{
			long now = System.currentTimeMillis();

			// The boss briefly leaves the NPC cache when it changes form (shielded / burrowed).
			// Do not treat that as a kill: keep the current prayers up through the gap.
			if (sawBoss)
			{
				if (bossGoneSince == 0)
				{
					bossGoneSince = now;
				}

				if (now - bossGoneSince < BOSS_LOST_GRACE_MS)
				{
					prayerService.tick(null, config.offensivePrayer(), config.reassertProtection());
					return;
				}
			}

			handleNoBoss();
			return;
		}

		bossGoneSince = 0;

		if (!sawBoss)
		{
			sawBoss = true;
			postFightPending = false;
			descendPending = false;
			pausedAfterFight = false;
		}

		// 1. Prayers always run first, before any action that can block.
		prayerService.tick(boss, config.offensivePrayer(), config.reassertProtection());

		// 2. Consumables. While the beam is charging a single hit can be 60+, so eat earlier.
		int eatAt = config.eatAtPercent();
		if (hazardTracker.isBeamCharging() && eatAt > 0)
		{
			eatAt = Math.max(eatAt, config.beamEatAtPercent());
		}
		if (eatAt > 0 && Rs2Player.eatAt(eatAt))
		{
			setState(DelveAutoState.EATING);
			return;
		}

		if (config.prayerPotAtPoints() > 0 && Rs2Player.drinkPrayerPotionAt(config.prayerPotAtPoints()))
		{
			setState(DelveAutoState.DRINKING_PRAYER);
			return;
		}

		// 3. Rock throw dodge. This preempts the shockwave because the rock lands during the shield
		// setup, and only actually moves during the last couple of ticks of the fuse.
		if (handleRockThrow(boss))
		{
			return;
		}

		// 4. Shockwave setup takes priority over damage: the slam is the lethal part.
		if (config.handleShockwave() && handleShockwave())
		{
			return;
		}

		// 5. Beam charge. Never tank it: swap to the configured melee weapon, interrupt the charge
		// with a hit (and optionally a special attack), then restore the bow.
		if (handleBeamPunish(boss))
		{
			return;
		}

		// A swap is still in flight; do not run bow-distance logic with the melee weapon equipped.
		if (weaponService.isRestorePending())
		{
			setState(DelveAutoState.RESTORING_WEAPON);
			return;
		}

		// 6. Never stand in acid.
		if (handleAcid(boss))
		{
			return;
		}

		// 7. Demonic larvae. Killing one next to the boss explodes onto the boss (or its shield)
		// instead of the player. This is a higher priority than utility/DPS but never delays a
		// prayer, a heal, a dodge or the beam punish.
		if (larvaService.tick(boss, config))
		{
			setState(DelveAutoState.KILLING_LARVA);
			return;
		}

		// 8. Arceuus upkeep. Only reached when no defensive mechanic needs this tick, so a thrall or
		// Death Charge never delays a dodge, the shield or the beam punish.
		if (magicUtilityService.tick(config))
		{
			setState(DelveAutoState.CASTING_UTILITY);
			return;
		}

		// 9. Damage.
		attack(boss);
	}

	private Rs2NpcModel findBoss()
	{
		return Microbot.getRs2NpcCache().query()
			.withIds(BOSS_NPC_IDS)
			.where(npc -> !npc.isDead())
			.nearest();
	}

	/**
	 * Handles the Special Beam Cannon charge. The Doom prays ranged and magic, so arrows cannot
	 * interrupt it; only a melee hit does. Swap to the configured weapon, get inside its reach
	 * (halberds reach two tiles), optionally enable the special attack, hit once, and let
	 * {@link DelveWeaponService} put the bow back on when the charge ends.
	 *
	 * @return true when this tick was consumed by the punish
	 */
	private boolean handleBeamPunish(Rs2NpcModel boss)
	{
		// The L3 demonic shield is immune to non-demonbane damage and uses a sustained beam rather
		// than the interruptible charge, so do not waste a melee swap on it.
		if (!hazardTracker.isBeamCharging() || boss.getId() == NpcID.DOM_BOSS_SHIELDED)
		{
			if (punishingBeam)
			{
				finishBeamPunish();
			}
			return false;
		}

		String weapon = config.beamPunishWeapon();
		if (weapon == null || weapon.trim().isEmpty())
		{
			// Without a melee weapon the charge cannot be interrupted. The raised eat threshold in
			// the caller still applies, but do not pretend to punish.
			if (punishingBeam)
			{
				finishBeamPunish();
			}
			return false;
		}

		if (!punishingBeam)
		{
			punishingBeam = true;
			punishWarned = false;
			punishSpecialResolved = false;
			punishStartedAt = System.currentTimeMillis();
			lastPunishAttackAt = 0;
			// The beam has priority over larvae; keep the base weapon but stop the larva service
			// from retargeting while the punish owns the weapon slot.
			larvaService.releaseForBeam();
			weaponService.beginPunish(weapon);
		}

		setState(DelveAutoState.INTERRUPTING_BEAM);

		long now = System.currentTimeMillis();
		if (now - punishStartedAt > PUNISH_MAX_MS)
		{
			// Defensive escape hatch: never stay on the melee weapon forever if the animation gets
			// stuck. Restoring the bow matters more than one missed interrupt.
			finishBeamPunish();
			return false;
		}

		if (!weaponService.isPunishWeaponEquipped())
		{
			if (!weaponService.hasPunishWeapon())
			{
				if (!punishWarned)
				{
					punishWarned = true;
					Microbot.log("Delve Auto: melee punish weapon '" + weapon.trim()
						+ "' is neither equipped nor in the inventory; cannot interrupt the beam");
				}
				return false;
			}

			// Start the swap now; movement below still happens on this tick so closing the gap and
			// changing weapons overlap instead of costing a tick each.
			weaponService.equipPunishWeapon();
		}

		WorldPoint bossTile = boss.getWorldLocation();
		WorldPoint player = Rs2Player.getWorldLocation();
		if (bossTile == null || player == null)
		{
			return true;
		}

		int reach = DelveWeaponService.reachFor(weapon.trim(), config.beamPunishReach());
		boolean inReach = player.distanceTo(bossTile) <= reach;
		if (!inReach)
		{
			WorldPoint target = movement.findMeleeTile(bossTile, reach);
			if (target != null)
			{
				movement.walkTo(target);
			}

			// Arm the special while still walking; it does not need the attack to be available yet.
			if (weaponService.isPunishWeaponEquipped() && config.beamPunishUseSpecial() && !punishSpecialResolved)
			{
				punishSpecialResolved = true;
				weaponService.tryArmSpecial(config.beamPunishSpecialEnergy());
			}
			return true;
		}

		if (!weaponService.isPunishWeaponEquipped())
		{
			// Waiting for the wield click to land. Do not attack with the bow; it cannot interrupt.
			return true;
		}

		// Arm the special a tick before the hit so the toggle is registered when the attack lands.
		if (config.beamPunishUseSpecial() && !punishSpecialResolved)
		{
			punishSpecialResolved = true;
			if (weaponService.tryArmSpecial(config.beamPunishSpecialEnergy()))
			{
				return true;
			}
		}

		if (now - lastPunishAttackAt < PUNISH_ATTACK_RETRY_MS || Rs2Player.isAnimating(400))
		{
			return true;
		}

		lastPunishAttackAt = now;
		boss.click("Attack");
		weaponService.markSpecialUsed();
		return true;
	}

	private void finishBeamPunish()
	{
		punishingBeam = false;
		punishWarned = false;
		punishSpecialResolved = false;
		punishStartedAt = 0;
		lastPunishAttackAt = 0;
		weaponService.endPunish();
	}

	public boolean isPunishingBeam()
	{
		return punishingBeam;
	}

	private boolean handleShockwave()
	{
		if (!hazardTracker.isShockwaveActive())
		{
			return false;
		}

		// A spawned earthen shield is the only safe place during the slam. It moves, so follow its
		// current tile instead of the recorded destination.
		WorldPoint shieldTile = hazardTracker.getEarthenShieldTile();
		if (shieldTile != null)
		{
			setState(DelveAutoState.TAKING_SHIELD);
			movement.walkTo(shieldTile);
			return true;
		}

		// No shield yet: two volatile earth must be destroyed before it spawns. The first destroyed
		// determines the shield's destination, the second its spawn point, so both are mandatory.
		if (hazardTracker.getDestroyedNodes().size() < 2)
		{
			Rs2NpcModel node = Microbot.getRs2NpcCache().query()
				.withId(NpcID.DOM_SHOCKWAVE_PATH_NODE)
				.where(npc -> !npc.isDead())
				.nearest();

			if (node != null)
			{
				setState(DelveAutoState.DESTROYING_EARTH);
				if (!Rs2Player.isAnimating(400))
				{
					node.click("Attack");
				}
				return true;
			}
		}

		// Both earths are down (or none are left) and the shield is still travelling. Wait on the
		// destination tile so the shield passes over us.
		WorldPoint destination = hazardTracker.getShieldDestination();
		if (destination != null)
		{
			movement.walkTo(destination);
		}

		setState(DelveAutoState.TAKING_SHIELD);
		return true;
	}

	private boolean handleRockThrow(Rs2NpcModel boss)
	{
		if (!config.dodgeRockThrow() || !hazardTracker.isRockFuseActive())
		{
			return false;
		}

		if (hazardTracker.getTicksUntilRockImpact() > ROCK_DODGE_TICKS)
		{
			return false;
		}

		WorldPoint safe = movement.findDodgeTile(boss.getWorldLocation());
		if (safe != null)
		{
			movement.walkTo(safe);
		}

		setState(DelveAutoState.DODGING_ROCK);
		return true;
	}

	private boolean handleAcid(Rs2NpcModel boss)
	{
		if (!config.avoidAcid())
		{
			return false;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		if (!hazardTracker.isAcidTile(player))
		{
			return false;
		}

		WorldPoint safe = movement.findSafeTile(boss.getWorldLocation(), config.preferredDistance());
		if (safe != null)
		{
			movement.walkTo(safe);
		}

		setState(DelveAutoState.DODGING_ROCK);
		return true;
	}

	private void attack(Rs2NpcModel boss)
	{
		WorldPoint bossTile = boss.getWorldLocation();
		WorldPoint player = Rs2Player.getWorldLocation();

		// Never stand next to the boss: the tongue melee has no wind-up and hits for up to 40.
		if (bossTile != null && player != null && player.distanceTo(bossTile) < MIN_BOSS_DISTANCE)
		{
			WorldPoint safe = movement.findSafeTile(bossTile, config.preferredDistance());
			if (safe != null)
			{
				movement.walkTo(safe);
			}
			setState(DelveAutoState.DODGING_ROCK);
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastAttackTime < ATTACK_REARM_MS)
		{
			setState(DelveAutoState.ATTACKING);
			return;
		}

		if (Rs2Player.isAnimating(400))
		{
			setState(DelveAutoState.ATTACKING);
			return;
		}

		lastAttackTime = now;
		boss.click("Attack");
		setState(DelveAutoState.ATTACKING);
	}

	private void handleNoBoss()
	{
		if (sawBoss)
		{
			sawBoss = false;
			bossGoneSince = System.currentTimeMillis();
			finishBeamPunish();
			larvaService.abort();
			prayerService.endFight();
			hazardTracker.reset();
			movement.reset();
			floorsCleared++;

			if (config.stopAfterMaxLevel() && floorsCleared >= Math.max(1, config.maxLevel()))
			{
				log.info("Delve Auto cleared {} floor(s), stopping", floorsCleared);
				setState(DelveAutoState.FINISHED);
				shutdown();
				return;
			}

			if (config.pauseAfterFight())
			{
				// Let the player decide whether to take the rewards or walk to the hole. The script
				// resumes automatically once the next floor's boss appears, which only happens after
				// a manual descend.
				pausedAfterFight = true;
				Microbot.log("Delve Auto: floor " + floorsCleared
					+ " cleared. Paused - take your rewards or descend when ready.");
				setState(DelveAutoState.PAUSED);
				return;
			}

			postFightPending = true;
			setState(DelveAutoState.DESCENDING);
			return;
		}

		if (postFightPending)
		{
			if (System.currentTimeMillis() - bossGoneSince < POST_FIGHT_DELAY_MS)
			{
				return;
			}

			postFightPending = false;
			descendPending = config.autoDescend();
		}

		if (pausedAfterFight)
		{
			if (!config.pauseAfterFight())
			{
				// Turning the option off is the resume button: fall through to auto-descend.
				pausedAfterFight = false;
				descendPending = config.autoDescend();
			}
			else
			{
				setState(DelveAutoState.PAUSED);
				return;
			}
		}

		if (descendPending)
		{
			// Keep retrying until the next floor's boss appears; the hole may not be loaded yet.
			descend();
			return;
		}

		setState(DelveAutoState.WAITING_FOR_BOSS);
	}

	private void descend()
	{
		// The unique burrow hole opens a confirmation dialog before descending.
		if (handleDescendConfirmation())
		{
			return;
		}

		Rs2TileObjectModel hole = Microbot.getRs2TileObjectCache().query()
			.withIds(DESCEND_HOLE_IDS)
			.nearest();

		if (hole == null)
		{
			setState(DelveAutoState.WAITING_FOR_BOSS);
			return;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		WorldPoint target = hole.getWorldLocation();

		if (player == null || target == null)
		{
			return;
		}

		setState(DelveAutoState.DESCENDING);

		long now = System.currentTimeMillis();
		if (now - lastDescendAction < DESCEND_ACTION_THROTTLE_MS)
		{
			return;
		}
		lastDescendAction = now;

		if (player.distanceTo(target) > 1)
		{
			Rs2Walker.walkTo(target, 1);
			return;
		}

		if (!hole.click("Descend"))
		{
			hole.click();
		}
	}

	private boolean handleDescendConfirmation()
	{
		if (!Rs2Widget.hasWidget(DESCEND_CONFIRM_TEXT))
		{
			return false;
		}

		setState(DelveAutoState.DESCENDING);
		Rs2Widget.clickWidget("Yes", true);
		return true;
	}

	private void reset()
	{
		prayerService.reset();
		hazardTracker.reset();
		movement.reset();
		weaponService.reset();
		magicUtilityService.reset();
		larvaService.reset();
		sawBoss = false;
		postFightPending = false;
		descendPending = false;
		pausedAfterFight = false;
		punishingBeam = false;
		punishWarned = false;
		punishSpecialResolved = false;
		bossGoneSince = 0;
		lastAttackTime = 0;
		lastDescendAction = 0;
		punishStartedAt = 0;
		lastPunishAttackAt = 0;
		setState(DelveAutoState.IDLE);
	}

	private void setState(DelveAutoState newState)
	{
		if (state == newState)
		{
			return;
		}

		state = newState;
		Microbot.status = "Delve: " + newState.name();
	}

	@Override
	public void shutdown()
	{
		// Put the bow back and clear the special before the script stops, otherwise the player is
		// left holding the punish weapon with a queued special attack.
		finishBeamPunish();
		larvaService.abort();
		for (int i = 0; i < 4; i++)
		{
			weaponService.tick();
			if (!weaponService.isRestorePending())
			{
				break;
			}
		}

		prayerService.reset();
		hazardTracker.reset();
		movement.reset();
		weaponService.reset();
		magicUtilityService.reset();
		larvaService.reset();
		sawBoss = false;
		postFightPending = false;
		descendPending = false;
		pausedAfterFight = false;
		punishingBeam = false;
		punishWarned = false;
		punishSpecialResolved = false;
		bossGoneSince = 0;
		lastAttackTime = 0;
		lastDescendAction = 0;
		punishStartedAt = 0;
		lastPunishAttackAt = 0;
		setState(DelveAutoState.IDLE);
		super.shutdown();
	}
}
