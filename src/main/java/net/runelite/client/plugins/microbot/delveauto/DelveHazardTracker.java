package net.runelite.client.plugins.microbot.delveauto;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Polls the client for the Doom's telegraphed mechanics and keeps the derived timing/position
 * state the arbiter needs. Everything here is computed from game state, so it works after
 * plugin re-enables and across floors without listening to spawn events.
 */
public class DelveHazardTracker
{
	/** Rock Throw is a 7 tick fuse from the attack animation starting. */
	private static final int ROCK_FUSE_TICKS = 7;

	/** Acid pools are static objects; rescanning twice a second is plenty. */
	private static final long ACID_SCAN_INTERVAL_MS = 300;

	private static final int[] ACID_OBJECT_IDS = {
		ObjectID1.DOM_ACIDPOOL,
		ObjectID1.DOM_ACIDPOOL_DIAGONAL
	};

	private int lastBossAnimation = -1;
	/** Game tick (not cycle) at which the rock fuse expires. 1 tick = 30 game cycles. */
	private int rockThrowEndTick = -1;
	private boolean beamCharging;
	private boolean shockwaveActive;

	private final Set<WorldPoint> seenNodeTiles = new HashSet<>();
	private final List<WorldPoint> destroyedNodes = new ArrayList<>();
	private WorldPoint shieldDestination;
	private WorldPoint shieldSpawn;
	private WorldPoint earthenShieldTile;

	private Set<WorldPoint> acidTiles = Collections.emptySet();
	private long lastAcidScan;

	public void update(Rs2NpcModel boss)
	{
		int currentTick = Microbot.getClient().getTickCount();
		int animation = boss == null ? -1 : boss.getAnimation();

		if (animation != lastBossAnimation)
		{
			if (animation == AnimationID.DOM_ROCK_THROW_ATTACK)
			{
				rockThrowEndTick = currentTick + ROCK_FUSE_TICKS;
			}
			lastBossAnimation = animation;
		}

		beamCharging = animation == AnimationID.DOM_BEAM_CHARGE
			|| animation == AnimationID.DOM_BEAM_CHARGE_LOOP;

		updateShockwave(animation);
		updateAcidTiles();
	}

	private void updateShockwave(int animation)
	{
		List<Rs2NpcModel> nodes = Microbot.getRs2NpcCache().query()
			.withId(NpcID.DOM_SHOCKWAVE_PATH_NODE)
			.where(npc -> !npc.isDead())
			.toList();

		Set<WorldPoint> currentNodes = nodes.stream()
			.map(Rs2NpcModel::getWorldLocation)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		Rs2NpcModel shield = Microbot.getRs2NpcCache().query()
			.withId(NpcID.DOM_SHOCKWAVE_SHIELD)
			.where(npc -> !npc.isDead())
			.nearest();
		earthenShieldTile = shield == null ? null : shield.getWorldLocation();

		boolean animCharging = animation == AnimationID.DOM_AREA_CHARGE
			|| animation == AnimationID.DOM_AREA_CHARGE_LOOP
			|| animation == AnimationID.DOOM_AREA_SLAM;

		boolean nodesPresent = !currentNodes.isEmpty();
		boolean active = nodesPresent || earthenShieldTile != null || animCharging;

		// Record earths that disappeared since the previous tick. The first destroyed earth is the
		// shield's destination, the second is where it spawns.
		for (WorldPoint previous : seenNodeTiles)
		{
			if (!currentNodes.contains(previous))
			{
				recordDestroyedNode(previous);
			}
		}

		// Only clear on the first tick of a new shockwave (fresh earths appeared). Clearing when the
		// earths briefly vanish before the shield NPC spawns would erase the pair we just destroyed.
		if (nodesPresent && !shockwaveActive)
		{
			destroyedNodes.clear();
			shieldDestination = null;
			shieldSpawn = null;
		}

		seenNodeTiles.clear();
		seenNodeTiles.addAll(currentNodes);
		shockwaveActive = active;
	}

	private void recordDestroyedNode(WorldPoint tile)
	{
		if (destroyedNodes.contains(tile))
		{
			return;
		}

		destroyedNodes.add(tile);
		if (shieldDestination == null)
		{
			shieldDestination = tile;
		}
		else if (shieldSpawn == null)
		{
			shieldSpawn = tile;
		}
	}

	private void updateAcidTiles()
	{
		long now = System.currentTimeMillis();
		if (now - lastAcidScan < ACID_SCAN_INTERVAL_MS)
		{
			return;
		}
		lastAcidScan = now;

		List<Rs2TileObjectModel> pools = Microbot.getRs2TileObjectCache().query()
			.withIds(ACID_OBJECT_IDS)
			.toList();

		if (pools.isEmpty())
		{
			acidTiles = Collections.emptySet();
			return;
		}

		// Tile objects expose scene-relative world points in instances, so project their local
		// point the same way the player position is projected.
		Set<WorldPoint> projected = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Set<WorldPoint> points = new HashSet<>();
			for (Rs2TileObjectModel pool : pools)
			{
				LocalPoint localPoint = pool.getLocalLocation();
				if (localPoint == null)
				{
					continue;
				}
				WorldPoint point = WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
				if (point != null)
				{
					points.add(point);
				}
			}
			return points;
		}).orElse(Collections.emptySet());

		acidTiles = projected;
	}

	public boolean isRockFuseActive()
	{
		return rockThrowEndTick > Microbot.getClient().getTickCount();
	}

	public int getTicksUntilRockImpact()
	{
		return Math.max(0, rockThrowEndTick - Microbot.getClient().getTickCount());
	}

	public boolean isBeamCharging()
	{
		return beamCharging;
	}

	public boolean isShockwaveActive()
	{
		return shockwaveActive;
	}

	public boolean isAcidTile(WorldPoint point)
	{
		return point != null && acidTiles.contains(point);
	}

	public WorldPoint getShieldDestination()
	{
		return shieldDestination;
	}

	public WorldPoint getShieldSpawn()
	{
		return shieldSpawn;
	}

	public WorldPoint getEarthenShieldTile()
	{
		return earthenShieldTile;
	}

	public List<WorldPoint> getDestroyedNodes()
	{
		return destroyedNodes;
	}

	public int getLastBossAnimation()
	{
		return lastBossAnimation;
	}

	public void reset()
	{
		lastBossAnimation = -1;
		rockThrowEndTick = -1;
		beamCharging = false;
		shockwaveActive = false;
		seenNodeTiles.clear();
		destroyedNodes.clear();
		shieldDestination = null;
		shieldSpawn = null;
		earthenShieldTile = null;
		acidTiles = Collections.emptySet();
		lastAcidScan = 0;
	}
}
