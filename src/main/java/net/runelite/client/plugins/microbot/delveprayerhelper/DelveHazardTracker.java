package net.runelite.client.plugins.microbot.delveprayerhelper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Polls the client for the Doom's telegraphed hazards that the prayer helper reacts to:
 * the Rock Throw fuse, acid pools and the volatile earth / earthen shield slam.
 *
 * <p>Everything is derived from live game state, so the tracker keeps working when the plugin is
 * enabled mid-fight and does not depend on spawn events.</p>
 */
@Singleton
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
	private volatile int rockThrowEndTick = -1;

	private volatile Set<WorldPoint> acidTiles = Collections.emptySet();
	private long lastAcidScan;

	/** Live volatile earth totems, in instance world space. Replaced on every scan. */
	private volatile List<WorldPoint> nodeTiles = Collections.emptyList();
	/**
	 * Volatile earth totems destroyed during this slam, in destruction order. The first is the
	 * shield's destination and the second is where it spawns, so the order matters.
	 */
	private volatile List<WorldPoint> destroyedNodes = Collections.emptyList();
	private volatile WorldPoint shieldDestination;
	private volatile WorldPoint shieldSpawn;
	private volatile WorldPoint earthenShieldTile;
	private volatile boolean shockwaveActive;

	private final List<WorldPoint> destroyedNodesBuffer = new ArrayList<>();
	private final Set<WorldPoint> seenNodeTiles = new HashSet<>();
	/** Live totems seen on the previous scan, used to detect the small -> full-spawn edge that starts a new slam. */
	private int previousNodeCount;
	/** Totem the script just attacked. Only this one may count as destroyed once the shield exists. */
	private volatile WorldPoint targetedNodeTile;
	/** True once the shield NPC has been seen in this slam; from then on rolling kills are not ours. */
	private boolean shieldSeenThisPhase;

	@Inject
	public DelveHazardTracker()
	{
	}

	public void updateRockFuse(int animation, int currentTick)
	{
		if (animation != lastBossAnimation)
		{
			if (animation == AnimationID.DOM_ROCK_THROW_ATTACK)
			{
				rockThrowEndTick = currentTick + ROCK_FUSE_TICKS;
			}
			lastBossAnimation = animation;
		}
	}

	/**
	 * Rescans the acid pools. Must be called on the client thread: the tile-object query would
	 * otherwise block the script thread for a whole game tick, delaying a prayer switch.
	 * Throttled internally, so it is cheap to call every game tick.
	 */
	public void scanAcidTiles()
	{
		updateAcidTiles();
	}

	/**
	 * Rescans the volatile earth totems and the earthen shield. Must be called on the client thread:
	 * the NPC queries would otherwise block the script thread for a whole game tick.
	 *
	 * <p>The slam is only safe on/next to the earthen shield NPC, which moves one tile per tick. The
	 * two volatile earth totems the player destroys decide its path: the first destroyed is the
	 * destination, the second is where the shield spawns.</p>
	 *
	 * @param bossAnimation the boss's current animation, used to keep the phase active during the
	 *                      slam charge before the earths appear
	 */
	public void scanShockwave(int bossAnimation)
	{
		final net.runelite.api.Client client = Microbot.getClient();
		if (client == null)
		{
			return;
		}

		List<Rs2NpcModel> nodes = Microbot.getRs2NpcCache().query()
			.withId(NpcID.DOM_SHOCKWAVE_PATH_NODE)
			.where(npc -> !npc.isDead())
			.toList();

		Set<WorldPoint> currentNodes = new HashSet<>();
		for (Rs2NpcModel node : nodes)
		{
			// Instance world space (fromLocal), not getWorldLocation(): the latter is template
			// space, which the walker and the tile helpers cannot resolve inside an instance.
			WorldPoint location = DelveMovement.fromLocal(client, node.getLocalLocation());
			if (location != null)
			{
				currentNodes.add(location);
			}
		}
		nodeTiles = Collections.unmodifiableList(new ArrayList<>(currentNodes));

		Rs2NpcModel shield = Microbot.getRs2NpcCache().query()
			.withId(NpcID.DOM_SHOCKWAVE_SHIELD)
			.where(npc -> !npc.isDead())
			.nearest();
		earthenShieldTile = shield == null
			? null
			: DelveMovement.fromLocal(client, shield.getLocalLocation());
		if (earthenShieldTile != null)
		{
			shieldSeenThisPhase = true;
		}

		// A slam starts from a handful of live totems (or none): the previous shield rolled over the
		// rest. Detecting the edge from a small count to a full spawn is much safer than an empty
		// seen-set, and it still works when the totems appear over more than one scan (the first
		// scan may only see a few of them). A stale pair left behind would make the plugin believe
		// the phase was already handled and skip it entirely.
		boolean newSlam = previousNodeCount < 5 && currentNodes.size() >= 5;
		if (newSlam)
		{
			destroyedNodesBuffer.clear();
			destroyedNodes = Collections.emptyList();
			shieldDestination = null;
			shieldSpawn = null;
			targetedNodeTile = null;
			shieldSeenThisPhase = false;
		}

		// Record only totems the player actually destroyed. Before the shield spawns every
		// disappearance is a player kill; once it spawns the shield rolls over the remaining earths,
		// so only the totem that was just attacked may be counted.
		if (destroyedNodesBuffer.size() < 2)
		{
			for (WorldPoint previous : seenNodeTiles)
			{
				if (currentNodes.contains(previous))
				{
					continue;
				}

				boolean targeted = previous.equals(targetedNodeTile);
				// Only the totems the player destroyed count. Once the shield has ever been seen in
				// this slam, every other disappearance is the shield rolling over the remaining
				// earths (even if the shield NPC is missing from the cache for one scan).
				if (targeted || (!shieldSeenThisPhase && earthenShieldTile == null))
				{
					recordDestroyedNode(previous);
					if (destroyedNodesBuffer.size() >= 2)
					{
						break;
					}
				}
			}
		}
		if (targetedNodeTile != null && !currentNodes.contains(targetedNodeTile))
		{
			targetedNodeTile = null;
		}

		seenNodeTiles.clear();
		seenNodeTiles.addAll(currentNodes);
		previousNodeCount = currentNodes.size();

		// The slam charge keeps the phase active even after the last earth is gone and before the
		// shield NPC shows up, so the player keeps waiting on the destination instead of wandering.
		boolean slamming = bossAnimation == AnimationID.DOM_AREA_CHARGE
			|| bossAnimation == AnimationID.DOM_AREA_CHARGE_LOOP
			|| bossAnimation == AnimationID.DOOM_AREA_SLAM;
		shockwaveActive = !currentNodes.isEmpty() || earthenShieldTile != null || slamming;
	}

	/**
	 * Marks the totem the script just clicked. Its disappearance is treated as a player kill even
	 * when the shield has already spawned (which happens on the same tick as the second kill).
	 */
	public void markTargetedNode(WorldPoint tile)
	{
		targetedNodeTile = tile;
	}

	private void recordDestroyedNode(WorldPoint tile)
	{
		// Only the pair matters: destination + spawn. Never grow past two, whatever the caller saw.
		if (tile == null || destroyedNodesBuffer.size() >= 2 || destroyedNodesBuffer.contains(tile))
		{
			return;
		}

		destroyedNodesBuffer.add(tile);
		destroyedNodes = Collections.unmodifiableList(new ArrayList<>(destroyedNodesBuffer));
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

		// Delve is instanced. Everything here has to be in instance world space (fromLocal), which
		// is the space the player, the walker and the tile helpers use. Do not use
		// WorldPoint.fromLocalInstance / getWorldLocation: those return template space and would
		// never match the player.
		final net.runelite.api.Client client = Microbot.getClient();
		Set<WorldPoint> points = new HashSet<>();
		for (Rs2TileObjectModel pool : pools)
		{
			WorldPoint origin = DelveMovement.fromLocal(client, pool.getLocalLocation());
			if (origin == null)
			{
				continue;
			}

			// Acid pools can be larger than one tile (the diagonal variant in particular), so add the
			// whole footprint. getLocalLocation() is the scene-min corner; the max dimension is used
			// on both axes so a rotated diagonal pool is still fully covered.
			int size = Math.max(1, Math.max(pool.sizeX(), pool.sizeY()));
			for (int dx = 0; dx < size; dx++)
			{
				for (int dy = 0; dy < size; dy++)
				{
					points.add(new WorldPoint(origin.getX() + dx, origin.getY() + dy, origin.getPlane()));
				}
			}
		}
		acidTiles = points;
	}

	public boolean isRockFuseActive()
	{
		return rockThrowEndTick > Microbot.getClient().getTickCount();
	}

	public int getTicksUntilRockImpact()
	{
		return Math.max(0, rockThrowEndTick - Microbot.getClient().getTickCount());
	}

	public boolean isAcidTile(WorldPoint point)
	{
		return point != null && acidTiles.contains(point);
	}

	public int getAcidTileCount()
	{
		return acidTiles.size();
	}

	public int getLastBossAnimation()
	{
		return lastBossAnimation;
	}

	/** True while volatile earth totems or the earthen shield are present. */
	public boolean isShockwaveActive()
	{
		return shockwaveActive;
	}

	/** Current tile of the moving earthen shield, or {@code null} before it spawns. */
	public WorldPoint getEarthenShieldTile()
	{
		return earthenShieldTile;
	}

	/** Where the shield will roll to: the first volatile earth totem destroyed. */
	public WorldPoint getShieldDestination()
	{
		return shieldDestination;
	}

	/** Where the shield spawns: the second volatile earth totem destroyed. */
	public WorldPoint getShieldSpawn()
	{
		return shieldSpawn;
	}

	/** Live volatile earth totems. */
	public List<WorldPoint> getNodeTiles()
	{
		return nodeTiles;
	}

	/** Volatile earth totems destroyed during the current slam, in destruction order. */
	public List<WorldPoint> getDestroyedNodes()
	{
		return destroyedNodes;
	}

	public void reset()
	{
		lastBossAnimation = -1;
		rockThrowEndTick = -1;
		acidTiles = Collections.emptySet();
		lastAcidScan = 0;
		nodeTiles = Collections.emptyList();
		destroyedNodes = Collections.emptyList();
		destroyedNodesBuffer.clear();
		seenNodeTiles.clear();
		previousNodeCount = 0;
		targetedNodeTile = null;
		shieldSeenThisPhase = false;
		shieldDestination = null;
		shieldSpawn = null;
		earthenShieldTile = null;
		shockwaveActive = false;
	}
}
