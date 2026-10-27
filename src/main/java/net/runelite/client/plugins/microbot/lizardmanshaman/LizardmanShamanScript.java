package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Fights lizardman shamans in the Lizardman Temple.
 *
 * <p>The script focuses on the two mechanics that actually kill players:</p>
 * <ul>
 *     <li><b>Jump attack</b> - the shaman lands on the tile the player occupied when it
 *     took off. The temple AoE is 2x2 with that tile as the north-east corner.</li>
 *     <li><b>Purple spawns</b> - three spawns are summoned around the shaman and explode
 *     a few seconds later. In the temple the blast is 3x3.</li>
 * </ul>
 *
 * <p>Instead of dodging tile by tile, the script rotates between three safe-spot groups
 * ({@link LizardmanShamanSafeSet}): north-east, south-east and south-west. While fighting it
 * holds a group no shaman can melee, answers purple spawns by waiting a few ticks and then
 * moving to a different group, and answers a telegraphed jump by leaving the north-east
 * group for one of the southern ones.</p>
 *
 * <p>Jump detection is deliberately animation-id free: an animation that persists for at
 * least {@link #JUMP_ANIMATION_CONFIRM_MS} ms while the shaman teleports at least
 * {@link #JUMP_MIN_TELEPORT_TILES} tiles is treated as a jump, and the animation id is
 * remembered so later jumps are detected at take-off. A normal attack animation is short,
 * so the teleport requirement keeps the detection from firing on every attack. A known
 * animation id can also be supplied through the config for exact detection.</p>
 */
public class LizardmanShamanScript extends Script
{
	private static final long LOOP_DELAY_MS = 300;
	private static final long JUMP_ANIMATION_CONFIRM_MS = 500;
	private static final long JUMP_DANGER_TTL_MS = 2400;
	private static final long JUMP_TRIGGER_DEBOUNCE_MS = 1200;
	private static final int JUMP_MIN_TELEPORT_TILES = 2;
	private static final int JUMP_MAX_START_DISTANCE = 12;
	private static final long SPAWN_MEMORY_MS = 900;
	private static final long SPAWN_DANGER_TTL_MS = 1000;
	private static final long EAT_COOLDOWN_MS = 900;
	private static final long PRAYER_COOLDOWN_MS = 1500;
	private static final long ANTIPOISON_COOLDOWN_MS = 2500;
	private static final long SET_MOVE_COOLDOWN_MS = 600;
	private static final long SET_WALK_RETRY_MS = 700;
	private static final long REATTACK_DELAY_MS = 900;
	private static final long SHAMAN_AREA_MEMORY_MS = 1800;
	private static final int SHAMAN_MELEE_RADIUS = 2;
	private static final int SHAMAN_FALLBACK_SIZE = 2;
	private static final int MAX_PRAYER_FAILURES = 3;

	@Inject
	private LizardmanShamanConfig config;

	private volatile LizardmanShamanState state = LizardmanShamanState.IDLE;
	private volatile String detail = "Idle";
	private volatile int shamanCount;
	private volatile int spawnCount;
	private volatile boolean temple;
	private volatile List<LizardmanShamanDangerArea> visibleDangers = Collections.emptyList();
	private volatile WorldPoint spotTarget;
	private volatile LizardmanShamanSafeSet targetSet;
	private volatile LizardmanShamanSafeSet currentSet;

	private final List<LizardmanShamanDangerArea> dangerAreas = new ArrayList<>();
	private final Map<Integer, JumpWatch> jumpWatches = new HashMap<>();
	private final Map<Integer, Long> lastJumpTriggeredAt = new HashMap<>();
	private final Map<Integer, WorldPoint> lastShamanTiles = new HashMap<>();
	private final Map<Integer, Integer> lastShamanAnimations = new HashMap<>();
	private final Map<Integer, ShamanAreaSnapshot> shamanAreaMemory = new HashMap<>();
	private final Set<Integer> learnedJumpAnimations = new HashSet<>();
	private final Map<WorldPoint, Long> spawnSightings = new HashMap<>();
	private final Map<Rs2PrayerEnum, Integer> prayerFailures = new HashMap<>();

	private long lastEatAt;
	private long lastPrayerAt;
	private long lastAntipoisonAt;
	private long lastSetMoveAt;
	private long reattackAt;
	private int spawnDodgeReadyTick;
	private boolean spawnEventActive;
	private LizardmanShamanSafeSet spawnOriginSet;

	public boolean run(LizardmanShamanConfig config)
	{
		this.config = config;
		resetState();
		state = LizardmanShamanState.SEARCHING;
		Microbot.status = "Lizardman Shaman: starting";

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			try
			{
				if (!shouldContinue())
				{
					return;
				}
				loop();
			}
			catch (Exception ex)
			{
				Microbot.logStackTrace(getClass().getSimpleName(), ex);
			}
		}, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

		return true;
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
		state = LizardmanShamanState.STOPPED;
		detail = "Stopped";
		Microbot.status = "Lizardman Shaman: stopped";
	}

	private boolean shouldContinue()
	{
		return super.run();
	}

	private void loop()
	{
		if (config == null || !Microbot.isLoggedIn())
		{
			state = LizardmanShamanState.IDLE;
			detail = "Not logged in";
			return;
		}

		final long now = System.currentTimeMillis();
		final WorldPoint playerTile = Rs2Player.getWorldLocation();
		if (playerTile == null)
		{
			state = LizardmanShamanState.IDLE;
			detail = "Waiting for the player";
			return;
		}

		final List<Rs2NpcModel> shamans = query(LizardmanShamanIds.SHAMAN_IDS);
		final List<Rs2NpcModel> spawns = query(LizardmanShamanIds.SPAWN_IDS);
		shamanCount = shamans.size();
		spawnCount = spawns.size();
		temple = isTempleFight(shamans, playerTile);

		// WorldArea.distanceTo is safe off-thread, but Actor.getWorldArea is not, so the
		// areas are snapshotted on the client thread once per pass instead of per tile.
		final List<WorldArea> shamanAreas = shamanAreas(shamans, now);

		dangerAreas.removeIf(area -> area.isExpired(now));
		detectJumps(shamans, playerTile, now, parseConfiguredJumpAnimations());
		trackSpawns(spawns, now);
		refreshVisibleDangers();

		currentSet = resolveSet(playerTile);

		if (shamans.isEmpty() && spawns.isEmpty())
		{
			resetSpawnSequence();
			state = LizardmanShamanState.SEARCHING;
			detail = "Searching for a shaman";
			Microbot.status = "Lizardman Shaman: searching";
			spotTarget = null;
			targetSet = null;
			return;
		}

		// A jump targets the tile the player stood on at take-off. Leave the exposed
		// north-east group as soon as any jump is telegraphed, even if the marked area has
		// not reached the player's tile yet (the shaman can land on the group corner).
		final boolean jumpDanger = isInDanger(playerTile, LizardmanShamanDangerArea.Type.JUMP)
				|| currentSet != null && currentSet.hasJumpRisk() && hasDangerArea(LizardmanShamanDangerArea.Type.JUMP);

		handleFood(now);
		handlePrayer(now);
		handleAntipoison(now);
		applyPrayers();

		// A jump sends the player out of the corner the shaman can reach; only the southern
		// groups are out of jump range.
		if (config.dodgeJumps() && jumpDanger && handleJumpMove(shamanAreas, playerTile, now))
		{
			return;
		}

		// Purple spawns: wait for the configured ticks, then move to another group.
		if (handleSpawnSequence(shamanAreas, playerTile, now))
		{
			return;
		}

		// Keep the player in a group no shaman can melee, and move when the held group is
		// no longer safe.
		if (maintainSafeSet(shamanAreas, playerTile, now))
		{
			return;
		}

		final Rs2NpcModel current = getInteractingShaman(shamans);
		if (current != null)
		{
			state = LizardmanShamanState.FIGHTING;
			detail = "Fighting shaman";
			Microbot.status = "Lizardman Shaman: fighting";

			// Walking to another group can leave a melee attacker out of range while the
			// interaction is still set. Re-issue the attack once the walk has finished so
			// the kill does not stall until auto-retaliate happens to pull the player back.
			if (reattackAt > 0 && now >= reattackAt && !Rs2Player.isMoving())
			{
				reattackAt = 0;
				if (current.click("Attack"))
				{
					Microbot.status = "Lizardman Shaman: attacking";
				}
			}
			return;
		}

		final Rs2NpcModel target = chooseTarget(shamans, playerTile);
		if (target == null)
		{
			state = LizardmanShamanState.SEARCHING;
			detail = "No reachable shaman";
			Microbot.status = "Lizardman Shaman: searching";
			return;
		}

		state = LizardmanShamanState.FIGHTING;
		detail = "Attacking shaman";
		if (target.click("Attack"))
		{
			Microbot.status = "Lizardman Shaman: attacking";
			reattackAt = 0;
		}
	}

	private List<Rs2NpcModel> query(int[] ids)
	{
		return Microbot.getRs2NpcCache().query()
				.withIds(ids)
				.within(Math.max(1, config.searchRadius()))
				.toList();
	}

	private boolean isTempleFight(List<Rs2NpcModel> shamans, WorldPoint playerTile)
	{
		for (Rs2NpcModel shaman : shamans)
		{
			if (LizardmanShamanIds.isTempleShaman(shaman.getId()))
			{
				return true;
			}
		}
		return LizardmanShamanIds.TEMPLE_AREA.contains(playerTile);
	}

	private void detectJumps(List<Rs2NpcModel> shamans, WorldPoint playerTile, long now, Set<Integer> configuredAnimations)
	{
		final Set<Integer> alive = new HashSet<>();
		for (Rs2NpcModel shaman : shamans)
		{
			final int index = shaman.getIndex();
			alive.add(index);

			final int animation = shaman.getAnimation();
			if (animation <= 0)
			{
				// Forget the previous sighting so a shaman that walked while idle is not
				// mistaken for one that teleported.
				jumpWatches.remove(index);
				lastShamanTiles.remove(index);
				lastShamanAnimations.remove(index);
				continue;
			}

			final WorldPoint shamanTile = shaman.getWorldLocation();
			final WorldPoint previousTile = lastShamanTiles.put(index, shamanTile);
			final Integer previousAnimation = lastShamanAnimations.put(index, animation);
			if (previousTile != null && shamanTile != null
					&& previousTile.distanceTo(shamanTile) >= JUMP_MIN_TELEPORT_TILES
					&& previousAnimation != null && previousAnimation > 0)
			{
				// The shaman teleported, so the animation observed just before the teleport
				// is the jump animation. Remember it so the next jump is caught at take-off.
				learnedJumpAnimations.add(previousAnimation);
			}

			final boolean knownJumpAnimation = configuredAnimations.contains(animation)
					|| learnedJumpAnimations.contains(animation);
			JumpWatch watch = jumpWatches.get(index);
			if (watch == null || watch.animation != animation)
			{
				watch = new JumpWatch(animation, now, playerTile, shamanTile);
				jumpWatches.put(index, watch);
				if (knownJumpAnimation)
				{
					triggerJump(index, watch, now);
				}
				continue;
			}

			if (watch.triggered)
			{
				continue;
			}

			final long elapsed = now - watch.startedAt;
			final boolean teleported = watch.shamanTile != null
					&& shamanTile != null
					&& watch.shamanTile.distanceTo(shamanTile) >= JUMP_MIN_TELEPORT_TILES;
			// A normal attack animation is short; requiring a teleport keeps the detection
			// from firing on every attack. Once the animation has been matched to a teleport
			// it is remembered, so later jumps are detected at take-off.
			final boolean confirmed = knownJumpAnimation
					|| teleported && elapsed >= JUMP_ANIMATION_CONFIRM_MS;

			if (confirmed)
			{
				triggerJump(index, watch, now);
			}
		}

		jumpWatches.keySet().retainAll(alive);
		lastJumpTriggeredAt.keySet().retainAll(alive);
		lastShamanTiles.keySet().retainAll(alive);
		lastShamanAnimations.keySet().retainAll(alive);
	}

	private void triggerJump(int shamanIndex, JumpWatch watch, long now)
	{
		watch.triggered = true;
		if (watch.playerTile == null)
		{
			return;
		}
		if (watch.shamanTile != null && watch.shamanTile.distanceTo(watch.playerTile) > JUMP_MAX_START_DISTANCE)
		{
			return;
		}

		// The animation can pass through several ids while the shaman is in the air; only
		// register the landing area once per jump, but allow a genuine follow-up jump.
		final Long lastTrigger = lastJumpTriggeredAt.get(shamanIndex);
		if (lastTrigger != null && now - lastTrigger < JUMP_TRIGGER_DEBOUNCE_MS)
		{
			return;
		}
		lastJumpTriggeredAt.put(shamanIndex, now);

		final LizardmanShamanDangerArea area = LizardmanShamanDangerArea.jump(
				watch.playerTile, temple, config.safetyTiles(), now + JUMP_DANGER_TTL_MS);
		dangerAreas.removeIf(existing -> existing.getType() == LizardmanShamanDangerArea.Type.JUMP
				&& existing.getAnchor().equals(area.getAnchor())
				&& existing.getWidth() == area.getWidth()
				&& existing.getHeight() == area.getHeight());
		dangerAreas.add(area);
		detail = "Jump attack - move";
	}

	private void trackSpawns(List<Rs2NpcModel> spawns, long now)
	{
		final Set<WorldPoint> currentTiles = new HashSet<>();
		for (Rs2NpcModel spawn : spawns)
		{
			final WorldPoint tile = spawn.getWorldLocation();
			if (tile != null)
			{
				currentTiles.add(tile);
			}
		}

		for (WorldPoint tile : currentTiles)
		{
			spawnSightings.put(tile, now);
		}
		spawnSightings.entrySet().removeIf(entry -> now - entry.getValue() > SPAWN_MEMORY_MS);

		// Spawn areas are rebuilt from the sightings each pass so their expiry follows the
		// spawn instead of accumulating one area per loop.
		dangerAreas.removeIf(area -> area.getType() == LizardmanShamanDangerArea.Type.SPAWN);
		if (!config.dodgeSpawns())
		{
			return;
		}

		for (WorldPoint tile : spawnSightings.keySet())
		{
			dangerAreas.add(LizardmanShamanDangerArea.spawn(
					tile, temple, config.safetyTiles(), now + SPAWN_DANGER_TTL_MS));
		}
	}

	private void refreshVisibleDangers()
	{
		visibleDangers = Collections.unmodifiableList(new ArrayList<>(dangerAreas));
	}

	private boolean isInDanger(WorldPoint tile)
	{
		for (LizardmanShamanDangerArea area : dangerAreas)
		{
			if (shouldDodge(area) && area.contains(tile))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isInDanger(WorldPoint tile, LizardmanShamanDangerArea.Type type)
	{
		for (LizardmanShamanDangerArea area : dangerAreas)
		{
			if (area.getType() == type && shouldDodge(area) && area.contains(tile))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasDangerArea(LizardmanShamanDangerArea.Type type)
	{
		for (LizardmanShamanDangerArea area : dangerAreas)
		{
			if (area.getType() == type)
			{
				return true;
			}
		}
		return false;
	}

	private boolean shouldDodge(LizardmanShamanDangerArea area)
	{
		if (area.getType() == LizardmanShamanDangerArea.Type.JUMP)
		{
			return config.dodgeJumps();
		}
		return config.dodgeSpawns();
	}

	/**
	 * A jump is telegraphed. The north-east corner is the only group a shaman can land on,
	 * so leave the current group for one of the southern groups and keep fighting.
	 *
	 * @return {@code true} while a move is running.
	 */
	private boolean handleJumpMove(List<WorldArea> shamanAreas, WorldPoint playerTile, long now)
	{
		final LizardmanShamanSafeSet destination = chooseSet(shamanAreas, playerTile, currentSet, true, true);
		if (destination == null)
		{
			state = LizardmanShamanState.DODGING;
			detail = "Jump - no southern safe spot";
			return false;
		}
		if (isInSet(destination, playerTile) && !isInDanger(playerTile))
		{
			state = LizardmanShamanState.FIGHTING;
			detail = "Jump dodged";
			return false;
		}

		state = LizardmanShamanState.DODGING;
		detail = "Jump - moving to " + destination.getLabel();
		Microbot.status = "Lizardman Shaman: dodging jump";
		return moveToSet(destination, playerTile, now);
	}

	/**
	 * Purple spawns appear around the shaman and walk towards the player before exploding.
	 * Keep fighting for the configured number of ticks so the spawns commit to the current
	 * group, then walk to a different safe-spot group that the shaman cannot melee.
	 *
	 * @return {@code true} while the sequence owns the player's movement.
	 */
	private boolean handleSpawnSequence(List<WorldArea> shamanAreas, WorldPoint playerTile, long now)
	{
		if (!config.dodgeSpawns())
		{
			resetSpawnSequence();
			return false;
		}

		final boolean spawnsPresent = spawnCount > 0;
		if (!spawnEventActive)
		{
			if (!spawnsPresent)
			{
				return false;
			}
			beginSpawnSequence(playerTile);
		}
		else if (!spawnsPresent && !hasDangerArea(LizardmanShamanDangerArea.Type.SPAWN))
		{
			resetSpawnSequence();
			return false;
		}

		// Always honour the configured delay, even when a spawn area already covers the
		// player: the spawns need those ticks to path towards the group being abandoned.
		final int ticksLeft = spawnDodgeReadyTick - currentTick();
		if (ticksLeft > 0)
		{
			// Keep attacking while the spawns walk towards the group we are about to leave.
			state = LizardmanShamanState.WAITING_SPAWN;
			detail = "Spawns in " + ticksLeft + (ticksLeft == 1 ? " tick" : " ticks");
			Microbot.status = "Lizardman Shaman: waiting for spawns";
			return false;
		}

		final LizardmanShamanSafeSet destination = chooseSet(shamanAreas, playerTile, spawnOriginSet, false, true);
		if (destination == null)
		{
			state = LizardmanShamanState.DODGING;
			detail = "Spawns - no safe spot";
			return false;
		}
		if (isInSet(destination, playerTile))
		{
			// Already in the chosen group; hold it until the spawns have exploded.
			state = LizardmanShamanState.DODGING;
			detail = "Spawn safe spot: " + destination.getLabel();
			targetSet = destination;
			spotTarget = null;
			return false;
		}

		state = LizardmanShamanState.DODGING;
		detail = "Spawns - moving to " + destination.getLabel();
		Microbot.status = "Lizardman Shaman: dodging spawns";
		return moveToSet(destination, playerTile, now);
	}

	private void beginSpawnSequence(WorldPoint playerTile)
	{
		spawnEventActive = true;
		spawnOriginSet = resolveSet(playerTile);
		spawnDodgeReadyTick = currentTick() + Math.max(0, config.spawnDodgeDelayTicks());
		targetSet = null;
		spotTarget = null;
	}

	private void resetSpawnSequence()
	{
		spawnEventActive = false;
		spawnOriginSet = null;
		spawnDodgeReadyTick = 0;
	}

	private int currentTick()
	{
		return Microbot.getClient() == null ? 0 : Microbot.getClient().getTickCount();
	}

	/**
	 * Keeps the player inside a group no shaman can melee. Moves to the nearest free group
	 * when the held one is in melee range; if every group is covered, the player stays put
	 * rather than ping-ponging between them.
	 *
	 * @return {@code true} while a move to another group is running.
	 */
	private boolean maintainSafeSet(List<WorldArea> shamanAreas, WorldPoint playerTile, long now)
	{
		// The spawn sequence owns movement until the spawns are gone.
		if (spawnEventActive)
		{
			return false;
		}

		// A move that is still in progress owns the player.
		if (targetSet != null && !isInSet(targetSet, playerTile))
		{
			if (Rs2Player.isMoving())
			{
				return true;
			}
			if (now - lastSetMoveAt >= SET_WALK_RETRY_MS)
			{
				state = LizardmanShamanState.POSITIONING;
				detail = "Moving to " + targetSet.getLabel();
				return moveToSet(targetSet, playerTile, now);
			}
			return false;
		}
		if (targetSet != null)
		{
			targetSet = null;
			spotTarget = null;
		}

		if (shamanAreas.isEmpty())
		{
			return false;
		}

		final LizardmanShamanSafeSet here = resolveSet(playerTile);
		currentSet = here;
		if (here != null && !isSetInMelee(here, shamanAreas, playerTile.getPlane()))
		{
			return false;
		}

		if (now - lastSetMoveAt < SET_MOVE_COOLDOWN_MS)
		{
			return false;
		}

		final LizardmanShamanSafeSet destination = chooseSet(shamanAreas, playerTile, here, false, false);
		if (destination == null)
		{
			return false;
		}

		state = LizardmanShamanState.POSITIONING;
		detail = "Moving to " + destination.getLabel();
		Microbot.status = "Lizardman Shaman: repositioning";
		return moveToSet(destination, playerTile, now);
	}

	/**
	 * Walks to the nearest usable tile of a group. The destination tile is kept while the
	 * walk runs, so the script does not pick a new tile on every loop pass.
	 *
	 * @return {@code true} while a move is running.
	 */
	private boolean moveToSet(LizardmanShamanSafeSet set, WorldPoint playerTile, long now)
	{
		if (set == null)
		{
			return false;
		}
		if (isInSet(set, playerTile))
		{
			targetSet = set;
			spotTarget = null;
			return false;
		}

		if (targetSet != set || spotTarget == null || !isUsableSpot(spotTarget, playerTile))
		{
			spotTarget = pickSetTile(set, playerTile);
			targetSet = set;
		}
		if (spotTarget == null)
		{
			targetSet = null;
			return false;
		}

		if (Rs2Player.isMoving() || now - lastSetMoveAt < SET_WALK_RETRY_MS)
		{
			return true;
		}

		if (Rs2Walker.walkFastCanvas(spotTarget))
		{
			lastSetMoveAt = now;
			reattackAt = now + REATTACK_DELAY_MS;
			return true;
		}

		// Unreachable tile: drop the move and wait before trying again.
		targetSet = null;
		spotTarget = null;
		lastSetMoveAt = now;
		return false;
	}

	/**
	 * Chooses the group to move to. Groups outside the shaman's melee reach win; among them
	 * the nearest one is used. When every group is in reach, the least exposed group is
	 * returned if {@code allowUnsafe} is set (a jump or a spawn blast is worse than melee).
	 */
	private LizardmanShamanSafeSet chooseSet(List<WorldArea> shamanAreas, WorldPoint origin,
											 LizardmanShamanSafeSet exclude, boolean southOnly, boolean allowUnsafe)
	{
		LizardmanShamanSafeSet bestSafe = null;
		int bestSafeDistance = Integer.MAX_VALUE;
		LizardmanShamanSafeSet bestFallback = null;
		int bestFallbackShamanDistance = Integer.MIN_VALUE;
		int bestFallbackPlayerDistance = Integer.MAX_VALUE;

		for (LizardmanShamanSafeSet set : LizardmanShamanSafeSet.values())
		{
			if (set == exclude || southOnly && set.hasJumpRisk())
			{
				continue;
			}

			final WorldPoint tile = pickSetTile(set, origin);
			if (tile == null)
			{
				continue;
			}

			final int playerDistance = tile.distanceTo(origin);
			if (!isSetInMelee(set, shamanAreas, origin.getPlane()))
			{
				if (playerDistance < bestSafeDistance)
				{
					bestSafeDistance = playerDistance;
					bestSafe = set;
				}
				continue;
			}

			final int shamanDistance = nearestShamanDistance(tile, shamanAreas);
			if (shamanDistance > bestFallbackShamanDistance
					|| shamanDistance == bestFallbackShamanDistance && playerDistance < bestFallbackPlayerDistance)
			{
				bestFallbackShamanDistance = shamanDistance;
				bestFallbackPlayerDistance = playerDistance;
				bestFallback = set;
			}
		}

		if (bestSafe != null)
		{
			return bestSafe;
		}
		return allowUnsafe ? bestFallback : null;
	}

	/**
	 * A group is unsafe when any of its tiles is within melee reach of a shaman. The groups
	 * are checked as a whole, so the player never half-stands in a group that can be hit.
	 */
	private boolean isSetInMelee(LizardmanShamanSafeSet set, List<WorldArea> shamanAreas, int plane)
	{
		for (WorldPoint tile : setTiles(set, plane))
		{
			if (nearestShamanDistance(tile, shamanAreas) <= SHAMAN_MELEE_RADIUS)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the nearest walkable tile of the group, preferring tiles outside any marked
	 * danger area.
	 */
	private WorldPoint pickSetTile(LizardmanShamanSafeSet set, WorldPoint origin)
	{
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		WorldPoint fallback = null;
		int fallbackDistance = Integer.MAX_VALUE;

		for (WorldPoint tile : setTiles(set, origin.getPlane()))
		{
			if (!Rs2Tile.isWalkable(tile))
			{
				continue;
			}

			final int distance = tile.distanceTo(origin);
			if (distance < fallbackDistance)
			{
				fallbackDistance = distance;
				fallback = tile;
			}
			if (!isInDanger(tile) && distance < bestDistance)
			{
				bestDistance = distance;
				best = tile;
			}
		}
		return best != null ? best : fallback;
	}

	private List<WorldPoint> setTiles(LizardmanShamanSafeSet set, int plane)
	{
		return set == null ? Collections.emptyList() : set.getTiles(plane);
	}

	private LizardmanShamanSafeSet resolveSet(WorldPoint tile)
	{
		if (tile == null)
		{
			return null;
		}
		for (LizardmanShamanSafeSet set : LizardmanShamanSafeSet.values())
		{
			if (isInSet(set, tile))
			{
				return set;
			}
		}
		return null;
	}

	private boolean isInSet(LizardmanShamanSafeSet set, WorldPoint tile)
	{
		return set != null && tile != null && setTiles(set, tile.getPlane()).contains(tile);
	}

	private boolean isUsableSpot(WorldPoint tile, WorldPoint origin)
	{
		return tile != null && tile.getPlane() == origin.getPlane() && Rs2Tile.isWalkable(tile);
	}

	private int nearestShamanDistance(WorldPoint tile, List<WorldArea> shamanAreas)
	{
		int nearest = Integer.MAX_VALUE;
		for (WorldArea area : shamanAreas)
		{
			nearest = Math.min(nearest, area.distanceTo(tile));
		}
		return nearest;
	}

	/**
	 * Snapshots the shaman areas on the client thread; {@link WorldArea#distanceTo} is then
	 * safe to call from the script thread.
	 *
	 * <p>A shaman that is mid-jump can drop out of the NPC cache for a tick or return a null
	 * area, which used to make the script believe every group was safe. Its last known area
	 * is therefore kept for {@link #SHAMAN_AREA_MEMORY_MS} so a disappearing shaman still
	 * counts as a melee threat.</p>
	 */
	private List<WorldArea> shamanAreas(List<Rs2NpcModel> shamans, long now)
	{
		final List<ShamanAreaSnapshot> current = snapshotShamanAreas(shamans, now);
		final Set<Integer> present = new HashSet<>();
		final List<WorldArea> areas = new ArrayList<>(current.size());

		for (ShamanAreaSnapshot snapshot : current)
		{
			present.add(snapshot.index);
			shamanAreaMemory.put(snapshot.index, snapshot);
			areas.add(snapshot.area);
		}

		// Only remember an absent shaman for a short window; a dead shaman must not keep a
		// group marked unsafe forever.
		shamanAreaMemory.entrySet().removeIf(entry ->
				!present.contains(entry.getKey())
						&& now - entry.getValue().seenAt > SHAMAN_AREA_MEMORY_MS);

		for (ShamanAreaSnapshot snapshot : shamanAreaMemory.values())
		{
			if (!present.contains(snapshot.index))
			{
				areas.add(snapshot.area);
			}
		}
		return areas;
	}

	private List<ShamanAreaSnapshot> snapshotShamanAreas(List<Rs2NpcModel> shamans, long now)
	{
		if (shamans.isEmpty())
		{
			return Collections.emptyList();
		}
		final List<ShamanAreaSnapshot> snapshot = Microbot.getClientThread().invoke(() ->
		{
			final List<ShamanAreaSnapshot> result = new ArrayList<>(shamans.size());
			for (Rs2NpcModel shaman : shamans)
			{
				try
				{
					// getWorldLocation is hopped by Rs2ActorModel and also projects actors in
					// a non-top-level world view back onto the main world.
					final WorldPoint tile = shaman.getWorldLocation();
					if (tile == null)
					{
						continue;
					}

					WorldArea area = null;
					try
					{
						area = shaman.getWorldArea();
					}
					catch (RuntimeException ex)
					{
						// The NPC can despawn between the query and this snapshot.
						area = null;
					}
					if (area == null)
					{
						// Never drop a live shaman from the melee check; a 2x2 area at its
						// tile is the same footprint getWorldArea would report.
						area = new WorldArea(tile, SHAMAN_FALLBACK_SIZE, SHAMAN_FALLBACK_SIZE);
					}
					result.add(new ShamanAreaSnapshot(shaman.getIndex(), area, now));
				}
				catch (RuntimeException ex)
				{
					// A stale NPC must not abort the snapshot for every other shaman.
				}
			}
			return result;
		});
		return snapshot == null ? Collections.emptyList() : snapshot;
	}

	private void handleFood(long now)
	{
		if (now - lastEatAt < EAT_COOLDOWN_MS)
		{
			return;
		}

		final int min = Math.min(config.foodMinPercent(), config.foodMaxPercent());
		final int max = Math.max(config.foodMinPercent(), config.foodMaxPercent());
		int threshold = min;
		if (max > min)
		{
			threshold += ThreadLocalRandom.current().nextInt(max - min + 1);
		}

		if (Rs2Player.getHealthPercentage() <= threshold && Rs2Player.eatAt(threshold))
		{
			lastEatAt = now;
			detail = "Eating";
		}
	}

	private void handlePrayer(long now)
	{
		if (now - lastPrayerAt < PRAYER_COOLDOWN_MS)
		{
			return;
		}

		final int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		if (maxPrayer <= 0)
		{
			return;
		}

		final int min = maxPrayer * Math.min(config.prayerMinPercent(), config.prayerMaxPercent()) / 100;
		final int max = maxPrayer * Math.max(config.prayerMinPercent(), config.prayerMaxPercent()) / 100;
		int drinkAt = min;
		if (max > min)
		{
			drinkAt += ThreadLocalRandom.current().nextInt(max - min + 1);
		}

		if (Rs2Player.drinkPrayerPotionAt(drinkAt))
		{
			lastPrayerAt = now;
			detail = "Restoring prayer";
		}
	}

	private void handleAntipoison(long now)
	{
		if (!config.useAntipoison() || now - lastAntipoisonAt < ANTIPOISON_COOLDOWN_MS)
		{
			return;
		}
		if (Rs2Player.drinkAntiPoisonPotion())
		{
			lastAntipoisonAt = now;
		}
	}

	private void applyPrayers()
	{
		final int prayerLevel = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		if (prayerLevel <= 0 || Rs2Prayer.getPrayerPoints() <= 0)
		{
			return;
		}

		ensurePrayer(config.overheadPrayer().getPrayer(), prayerLevel);
		ensurePrayer(config.offensivePrayer().getPrayer(), prayerLevel);
	}

	/**
	 * Enables a prayer once. A prayer the account cannot activate (for example an
	 * unlocked-only prayer such as Rigour) is retried a few times and then ignored so
	 * {@link Rs2Prayer#toggle} cannot stall the loop for its ten second timeout on
	 * every iteration.
	 */
	private void ensurePrayer(Rs2PrayerEnum prayer, int prayerLevel)
	{
		if (prayer == null || prayerLevel < prayer.getLevel() || Rs2Prayer.isPrayerActive(prayer))
		{
			return;
		}
		if (prayerFailures.getOrDefault(prayer, 0) >= MAX_PRAYER_FAILURES)
		{
			return;
		}
		if (!Rs2Prayer.toggle(prayer, true))
		{
			prayerFailures.merge(prayer, 1, Integer::sum);
		}
	}

	private Rs2NpcModel getInteractingShaman(List<Rs2NpcModel> shamans)
	{
		final Actor actor = Rs2Player.getInteracting();
		if (!(actor instanceof NPC))
		{
			return null;
		}

		final int index = ((NPC) actor).getIndex();
		for (Rs2NpcModel shaman : shamans)
		{
			if (shaman.getIndex() == index && !shaman.isDead())
			{
				return shaman;
			}
		}
		return null;
	}

	private Rs2NpcModel chooseTarget(List<Rs2NpcModel> shamans, WorldPoint playerTile)
	{
		Rs2NpcModel best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Rs2NpcModel shaman : shamans)
		{
			if (shaman.isDead())
			{
				continue;
			}

			final WorldPoint tile = shaman.getWorldLocation();
			if (tile == null || !shaman.hasLineOfSight())
			{
				continue;
			}

			int score = tile.distanceTo(playerTile) * 10;
			if (shaman.isInteractingWithPlayer())
			{
				score -= 15;
			}

			if (score < bestScore)
			{
				bestScore = score;
				best = shaman;
			}
		}
		return best;
	}

	private Set<Integer> parseConfiguredJumpAnimations()
	{
		final String raw = config.jumpAnimationIds();
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		final Set<Integer> ids = new HashSet<>();
		for (String part : raw.split(","))
		{
			final String value = part.trim();
			if (value.isEmpty())
			{
				continue;
			}
			try
			{
				ids.add(Integer.parseInt(value));
			}
			catch (NumberFormatException ignored)
			{
				// A malformed entry must not stop the script; the fallback detection still works.
			}
		}
		return ids;
	}

	private void resetState()
	{
		dangerAreas.clear();
		jumpWatches.clear();
		lastJumpTriggeredAt.clear();
		lastShamanTiles.clear();
		lastShamanAnimations.clear();
		learnedJumpAnimations.clear();
		shamanAreaMemory.clear();
		spawnSightings.clear();
		prayerFailures.clear();
		resetSpawnSequence();
		visibleDangers = Collections.emptyList();
		spotTarget = null;
		targetSet = null;
		currentSet = null;
		shamanCount = 0;
		spawnCount = 0;
		temple = false;
		lastEatAt = 0;
		lastPrayerAt = 0;
		lastAntipoisonAt = 0;
		lastSetMoveAt = 0;
		reattackAt = 0;
	}

	public LizardmanShamanState getState()
	{
		return state;
	}

	public String getDetail()
	{
		return detail;
	}

	public int getShamanCount()
	{
		return shamanCount;
	}

	public int getSpawnCount()
	{
		return spawnCount;
	}

	public boolean isTemple()
	{
		return temple;
	}

	public List<LizardmanShamanDangerArea> getVisibleDangers()
	{
		return visibleDangers;
	}

	public WorldPoint getSpotTarget()
	{
		return spotTarget;
	}

	public LizardmanShamanSafeSet getCurrentSet()
	{
		return currentSet;
	}

	public LizardmanShamanSafeSet getTargetSet()
	{
		return targetSet;
	}

	/**
	 * @return the hard-coded tiles of a safe-spot group for the player's plane.
	 */
	public List<WorldPoint> getSafeSpotTiles(LizardmanShamanSafeSet set)
	{
		final WorldPoint playerTile = Rs2Player.getWorldLocation();
		return setTiles(set, playerTile == null ? 0 : playerTile.getPlane());
	}

	private static final class ShamanAreaSnapshot
	{
		private final int index;
		private final WorldArea area;
		private final long seenAt;

		private ShamanAreaSnapshot(int index, WorldArea area, long seenAt)
		{
			this.index = index;
			this.area = area;
			this.seenAt = seenAt;
		}
	}

	private static final class JumpWatch
	{
		private final int animation;
		private final long startedAt;
		private final WorldPoint playerTile;
		private final WorldPoint shamanTile;
		private boolean triggered;

		private JumpWatch(int animation, long startedAt, WorldPoint playerTile, WorldPoint shamanTile)
		{
			this.animation = animation;
			this.startedAt = startedAt;
			this.playerTile = playerTile;
			this.shamanTile = shamanTile;
		}
	}
}
