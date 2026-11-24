package net.runelite.client.plugins.microbot.delveauto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveCombatStyle;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveLarvaSpell;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;
import java.util.List;

/**
 * Demonic larva handling.
 *
 * <p>Larvae walk from the player towards the Doom. If one reaches it the boss is healed and the
 * player is hurt, so they have to be killed. Killing one explodes a 3x3 area: the player takes up to
 * 21 damage, but if the explosion also covers the boss the player takes nothing and the boss (or its
 * shield) takes damage instead. The bot therefore waits until a larva is close to the boss before
 * attacking.</p>
 *
 * <p>Every larva carries protection prayers. Coloured larvae (delve 4+) dictate the style by colour:
 * green = ranged, blue = magic, red = melee (shield phase only). The neutral beige larvae in delves
 * 1-3 instead expose a single random overhead prayer, which is read and countered with a configured
 * weapon of another style. Weapon swaps share {@link DelveWeaponService} with the beam punish, so the
 * original bow is always restored.</p>
 */
@Slf4j
public class DelveLarvaService
{
	private static final int[] LARVA_IDS = {
		NpcID.DOM_DEMONIC_ENERGY,
		NpcID.DOM_DEMONIC_ENERGY_RANGE,
		NpcID.DOM_DEMONIC_ENERGY_MAGE,
		NpcID.DOM_DEMONIC_ENERGY_MELEE,
		NpcID.DOM_DEMONIC_ENERGY_GIANT_RANGE,
		NpcID.DOM_DEMONIC_ENERGY_GIANT_MAGE
	};

	private static final long ATTACK_REARM_MS = 600;
	private static final int ATTACK_ANIMATION_GUARD_MS = 400;

	/** Melee larvae need time to walk into reach, so start moving this many tiles earlier. */
	private static final int MELEE_APPROACH_LEAD = 2;

	private final DelveMovement movement;
	private final DelveWeaponService weaponService;

	private boolean ownsSwap;
	private long lastAttackAt;
	private boolean warnedMissingWeapon;
	private String warnedMissingWeaponName;

	@Getter
	private int larvaCount;

	@Getter
	private String targetStyle = "-";

	@Getter
	private String status = "off";

	public DelveLarvaService(DelveMovement movement, DelveWeaponService weaponService)
	{
		this.movement = movement;
		this.weaponService = weaponService;
	}

	/**
	 * Runs one tick of larva handling.
	 *
	 * @return true when this tick was consumed by a swap, a cast or an attack
	 */
	public boolean tick(Rs2NpcModel boss, DelveAutoConfig config)
	{
		if (!config.handleLarvae())
		{
			releaseOwnedSwap();
			larvaCount = 0;
			targetStyle = "-";
			status = "off";
			return false;
		}

		List<Rs2NpcModel> larvae = Microbot.getRs2NpcCache().query()
			.withIds(LARVA_IDS)
			.where(npc -> !npc.isDead())
			.toList();
		larvaCount = larvae.size();

		if (larvae.isEmpty())
		{
			releaseOwnedSwap();
			targetStyle = "-";
			status = "none";
			return false;
		}

		Candidate target = chooseTarget(boss, larvae, config);
		if (target == null)
		{
			// Nothing is close enough to the boss yet. Give the weapon slot back so normal DPS
			// continues with the bow instead of holding a larva weapon.
			releaseOwnedSwap();
			targetStyle = "-";
			status = larvae.size() + " waiting";
			return false;
		}

		targetStyle = target.style.name();

		Rs2Spells spell = resolveSpell(target.style, config);
		if (spell != null)
		{
			return castAt(target.npc, spell, boss);
		}

		String weapon = weaponFor(config, target.style);
		if (weapon == null || weapon.trim().isEmpty())
		{
			status = "no " + target.style + " weapon";
			releaseOwnedSwap();
			return false;
		}

		weapon = weapon.trim();
		if (!ownsSwap)
		{
			weaponService.beginSwap(weapon);
			ownsSwap = true;
		}
		else if (!weapon.equalsIgnoreCase(weaponService.getSwapWeaponName()))
		{
			// The next larva needs a different style; retarget without losing the base weapon.
			weaponService.beginSwap(weapon);
		}

		if (!weaponService.hasSwapWeapon())
		{
			if (!warnedMissingWeapon || !weapon.equals(warnedMissingWeaponName))
			{
				warnedMissingWeapon = true;
				warnedMissingWeaponName = weapon;
				Microbot.log("Delve Auto: larva weapon '" + weapon
					+ "' is neither equipped nor in the inventory; cannot kill " + target.style + " larvae");
			}
			status = "missing " + weapon;
			releaseOwnedSwap();
			return false;
		}

		if (!weaponService.isSwapWeaponEquipped())
		{
			weaponService.equipSwapWeapon();
			status = "swapping to " + weapon;
			return true;
		}

		warnedMissingWeapon = false;
		warnedMissingWeaponName = null;

		return attack(target.npc, target.style, weapon, boss);
	}

	/**
	 * Releases a swap that this service owns without ending one owned by the beam punish. Used when
	 * the beam punish takes over the weapon slot.
	 */
	public void releaseForBeam()
	{
		ownsSwap = false;
		targetStyle = "-";
		status = "beam punish";
	}

	/**
	 * Queues a restore of the base weapon if a larva swap is still open. Safe to call when the fight
	 * ends or the script shuts down.
	 */
	public void abort()
	{
		releaseOwnedSwap();
	}

	private void releaseOwnedSwap()
	{
		if (ownsSwap)
		{
			weaponService.endSwap();
			ownsSwap = false;
		}
	}

	private Candidate chooseTarget(Rs2NpcModel boss, List<Rs2NpcModel> larvae, DelveAutoConfig config)
	{
		int killDistance = Math.max(1, config.larvaKillDistance());

		return larvae.stream()
			.map(larva -> new Candidate(larva, bossDistance(boss, larva), playerDistance(larva)))
			.map(candidate -> {
				DelveCombatStyle style = requiredStyle(candidate.npc, config);
				return style == null ? null : new Candidate(candidate.npc, candidate.bossDistance,
					candidate.playerDistance, style);
			})
			.filter(candidate -> candidate != null
				&& candidate.bossDistance <= engagementDistance(candidate.style, killDistance)
				&& canHandle(candidate.style, config))
			.sorted(Comparator.comparingInt((Candidate candidate) -> candidate.bossDistance)
				.thenComparingInt(candidate -> candidate.playerDistance))
			.findFirst()
			.orElse(null);
	}

	private int engagementDistance(DelveCombatStyle style, int killDistance)
	{
		return style == DelveCombatStyle.MELEE ? killDistance + MELEE_APPROACH_LEAD : killDistance;
	}

	private int bossDistance(Rs2NpcModel boss, Rs2NpcModel larva)
	{
		WorldPoint tile = larva.getWorldLocation();
		if (tile == null)
		{
			return Integer.MAX_VALUE;
		}

		WorldArea area = boss.getWorldArea();
		if (area != null)
		{
			return area.distanceTo(tile);
		}

		WorldPoint bossTile = boss.getWorldLocation();
		return bossTile == null ? Integer.MAX_VALUE : tile.distanceTo(bossTile);
	}

	private int playerDistance(Rs2NpcModel larva)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		WorldPoint tile = larva.getWorldLocation();
		return player == null || tile == null ? Integer.MAX_VALUE : player.distanceTo(tile);
	}

	/**
	 * Maps a larva to the style it must be killed with. Neutral larvae expose a random overhead
	 * prayer in delves 1-3; the configured preference is used unless it is protected.
	 */
	private DelveCombatStyle requiredStyle(Rs2NpcModel larva, DelveAutoConfig config)
	{
		switch (larva.getId())
		{
			case NpcID.DOM_DEMONIC_ENERGY_RANGE:
			case NpcID.DOM_DEMONIC_ENERGY_GIANT_RANGE:
				return DelveCombatStyle.RANGE;
			case NpcID.DOM_DEMONIC_ENERGY_MAGE:
			case NpcID.DOM_DEMONIC_ENERGY_GIANT_MAGE:
				return DelveCombatStyle.MAGIC;
			case NpcID.DOM_DEMONIC_ENERGY_MELEE:
				return DelveCombatStyle.MELEE;
			default:
				return neutralStyle(larva, config);
		}
	}

	private DelveCombatStyle neutralStyle(Rs2NpcModel larva, DelveAutoConfig config)
	{
		HeadIcon protection = overhead(larva);
		DelveCombatStyle preferred = config.neutralLarvaStyle();
		if (preferred == null)
		{
			preferred = DelveCombatStyle.RANGE;
		}

		if (!isProtected(protection, preferred) && canHandle(preferred, config))
		{
			return preferred;
		}

		for (DelveCombatStyle style : new DelveCombatStyle[]{
			DelveCombatStyle.RANGE, DelveCombatStyle.MAGIC, DelveCombatStyle.MELEE})
		{
			if (style != preferred && !isProtected(protection, style) && canHandle(style, config))
			{
				return style;
			}
		}

		return preferred;
	}

	/**
	 * Reads the overhead prayer without the noisy logging in {@link Rs2NpcModel#getHeadIcon()}.
	 */
	private HeadIcon overhead(Rs2NpcModel larva)
	{
		short[] spriteIds = larva.getNpc().getOverheadSpriteIds();
		if (spriteIds == null)
		{
			return null;
		}

		HeadIcon[] values = HeadIcon.values();
		for (int spriteId : spriteIds)
		{
			if (spriteId >= 0 && spriteId < values.length)
			{
				return values[spriteId];
			}
		}
		return null;
	}

	private boolean isProtected(HeadIcon protection, DelveCombatStyle style)
	{
		if (protection == null)
		{
			return false;
		}

		switch (protection)
		{
			case MELEE:
			case DEFLECT_MELEE:
				return style == DelveCombatStyle.MELEE;
			case RANGED:
			case DEFLECT_RANGE:
				return style == DelveCombatStyle.RANGE;
			case MAGIC:
			case DEFLECT_MAGE:
				return style == DelveCombatStyle.MAGIC;
			case RANGE_MAGE:
				return style == DelveCombatStyle.RANGE || style == DelveCombatStyle.MAGIC;
			case RANGE_MELEE:
				return style == DelveCombatStyle.RANGE || style == DelveCombatStyle.MELEE;
			case MAGE_MELEE:
				return style == DelveCombatStyle.MAGIC || style == DelveCombatStyle.MELEE;
			case RANGE_MAGE_MELEE:
				return true;
			default:
				return false;
		}
	}

	private String weaponFor(DelveAutoConfig config, DelveCombatStyle style)
	{
		switch (style)
		{
			case RANGE:
				return config.larvaRangedWeapon();
			case MAGIC:
				return config.larvaMagicWeapon();
			case MELEE:
			default:
				return config.larvaMeleeWeapon();
		}
	}

	private boolean canHandle(DelveCombatStyle style, DelveAutoConfig config)
	{
		if (style == DelveCombatStyle.MAGIC)
		{
			Rs2Spells spell = resolveSpell(style, config);
			if (spell != null && Rs2Magic.canCast(spell))
			{
				return true;
			}
		}

		String weapon = weaponFor(config, style);
		if (weapon == null || weapon.trim().isEmpty())
		{
			return false;
		}

		weapon = weapon.trim();
		return Rs2Equipment.isWearing(weapon) || Rs2Inventory.hasItem(weapon);
	}

	private Rs2Spells resolveSpell(DelveCombatStyle style, DelveAutoConfig config)
	{
		if (style != DelveCombatStyle.MAGIC)
		{
			return null;
		}

		DelveLarvaSpell choice = config.larvaMagicSpell();
		if (choice == null || choice == DelveLarvaSpell.NONE)
		{
			return null;
		}

		if (choice == DelveLarvaSpell.DARK_DEMONBANE)
		{
			return Rs2Magic.canCast(Rs2Spells.DARK_DEMONBANE) ? Rs2Spells.DARK_DEMONBANE : null;
		}
		if (choice == DelveLarvaSpell.SUPERIOR_DEMONBANE)
		{
			return Rs2Magic.canCast(Rs2Spells.SUPERIOR_DEMONBANE) ? Rs2Spells.SUPERIOR_DEMONBANE : null;
		}
		if (choice == DelveLarvaSpell.INFERIOR_DEMONBANE)
		{
			return Rs2Magic.canCast(Rs2Spells.INFERIOR_DEMONBANE) ? Rs2Spells.INFERIOR_DEMONBANE : null;
		}

		// AUTO: strongest first. canCast is a cheap level/spellbook/rune check.
		if (Rs2Magic.canCast(Rs2Spells.DARK_DEMONBANE))
		{
			return Rs2Spells.DARK_DEMONBANE;
		}
		if (Rs2Magic.canCast(Rs2Spells.SUPERIOR_DEMONBANE))
		{
			return Rs2Spells.SUPERIOR_DEMONBANE;
		}
		if (Rs2Magic.canCast(Rs2Spells.INFERIOR_DEMONBANE))
		{
			return Rs2Spells.INFERIOR_DEMONBANE;
		}
		return null;
	}

	private boolean castAt(Rs2NpcModel larva, Rs2Spells spell, Rs2NpcModel boss)
	{
		String spellName = spell.getMagicAction().getName();
		if (!Rs2Magic.canCast(spell))
		{
			status = spellName + " not castable";
			return false;
		}

		if (!withinRange(larva, boss, 10))
		{
			status = "closing on magic";
			return true;
		}

		long now = System.currentTimeMillis();
		if (now - lastAttackAt < ATTACK_REARM_MS || Rs2Player.isAnimating(ATTACK_ANIMATION_GUARD_MS))
		{
			status = "casting " + spellName;
			return true;
		}

		lastAttackAt = now;
		// cast() selects the spell (switching to the magic tab); the NPC click then casts it on the
		// larva. Rs2Magic.castOn cannot be used with Rs2NpcModel: it expects a raw NPC instance.
		if (!Rs2Magic.cast(spell))
		{
			status = "cast failed";
			return true;
		}

		// Wait for the spell widget to be selected; clicking before that would just melee the larva.
		if (!Global.sleepUntil(() -> Microbot.getClient().isWidgetSelected(), 600))
		{
			status = "spell not selected";
			return true;
		}

		larva.click();
		status = "casting " + spellName;
		return true;
	}

	private boolean attack(Rs2NpcModel larva, DelveCombatStyle style, String weapon, Rs2NpcModel boss)
	{
		WorldPoint larvaTile = larva.getWorldLocation();
		WorldPoint playerTile = Rs2Player.getWorldLocation();
		if (larvaTile == null || playerTile == null)
		{
			return true;
		}

		if (style == DelveCombatStyle.MELEE)
		{
			int reach = DelveWeaponService.reachFor(weapon, 0);
			if (playerTile.distanceTo(larvaTile) > reach)
			{
				WorldPoint step = movement.findMeleeTile(larvaTile, reach);
				if (step != null)
				{
					movement.walkTo(step);
				}
				status = "closing on melee";
				return true;
			}
		}
		else
		{
			// Weapon-style lookups can fail for a tick right after a swap, so never trust a
			// melee-sized range for a ranged larva.
			int range = style == DelveCombatStyle.MAGIC
				? 10
				: Math.max(4, Rs2Combat.getAttackRange());
			if (playerTile.distanceTo(larvaTile) > range)
			{
				WorldPoint step = movement.findRangedTile(larvaTile, boss.getWorldLocation(), range);
				if (step != null)
				{
					movement.walkTo(step);
				}
				status = "closing on " + style.name().toLowerCase();
				return true;
			}
		}

		long now = System.currentTimeMillis();
		if (now - lastAttackAt < ATTACK_REARM_MS || Rs2Player.isAnimating(ATTACK_ANIMATION_GUARD_MS))
		{
			status = "attacking " + style.name().toLowerCase();
			return true;
		}

		lastAttackAt = now;
		larva.click("Attack");
		status = "attacking " + style.name().toLowerCase();
		return true;
	}

	private boolean withinRange(Rs2NpcModel larva, Rs2NpcModel boss, int range)
	{
		WorldPoint larvaTile = larva.getWorldLocation();
		WorldPoint playerTile = Rs2Player.getWorldLocation();
		if (larvaTile == null || playerTile == null)
		{
			return true;
		}

		if (playerTile.distanceTo(larvaTile) <= range)
		{
			return true;
		}

		WorldPoint step = movement.findRangedTile(larvaTile, boss.getWorldLocation(), range);
		if (step != null)
		{
			movement.walkTo(step);
		}
		return false;
	}

	public void reset()
	{
		ownsSwap = false;
		lastAttackAt = 0;
		warnedMissingWeapon = false;
		warnedMissingWeaponName = null;
		larvaCount = 0;
		targetStyle = "-";
		status = "off";
	}

	private static final class Candidate
	{
		private final Rs2NpcModel npc;
		private final int bossDistance;
		private final int playerDistance;
		private final DelveCombatStyle style;

		private Candidate(Rs2NpcModel npc, int bossDistance, int playerDistance)
		{
			this(npc, bossDistance, playerDistance, null);
		}

		private Candidate(Rs2NpcModel npc, int bossDistance, int playerDistance, DelveCombatStyle style)
		{
			this.npc = npc;
			this.bossDistance = bossDistance;
			this.playerDistance = playerDistance;
			this.style = style;
		}
	}
}
