package net.runelite.client.plugins.microbot.delveauto;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;

/**
 * Tile selection and click-to-move helpers.
 *
 * <p>Movement is deliberately canvas-click based. The arena is a small instanced room, so
 * pathfinding is unnecessary and the slower walker would miss 1-2 tick dodges.</p>
 */
public class DelveMovement
{
	private static final int SEARCH_RADIUS = 6;
	private static final int MIN_BOSS_DISTANCE = 3;
	private static final long WALK_THROTTLE_MS = 250;

	private final DelveAutoConfig config;
	private final DelveHazardTracker hazards;

	private long lastWalkTime;

	public DelveMovement(DelveAutoConfig config, DelveHazardTracker hazards)
	{
		this.config = config;
		this.hazards = hazards;
	}

	public boolean walkTo(WorldPoint target)
	{
		if (target == null)
		{
			return false;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		if (player != null && player.distanceTo(target) == 0)
		{
			return true;
		}

		long now = System.currentTimeMillis();
		if (now - lastWalkTime < WALK_THROTTLE_MS)
		{
			return false;
		}
		lastWalkTime = now;

		return Rs2Walker.walkFastCanvas(target);
	}

	/**
	 * Best tile to step onto: walkable, not acid, not adjacent to the boss, and close to the
	 * preferred bow distance. Returns {@code null} when nothing suitable is nearby.
	 */
	public WorldPoint findSafeTile(WorldPoint bossTile, int preferredDistance)
	{
		List<WorldPoint> candidates = Rs2Tile.getSafeTiles(SEARCH_RADIUS);
		if (candidates.isEmpty())
		{
			return null;
		}

		WorldPoint player = Rs2Player.getWorldLocation();

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> bossTile == null || tile.distanceTo(bossTile) >= MIN_BOSS_DISTANCE)
			.min(Comparator.comparingInt(tile -> score(tile, bossTile, player, preferredDistance)))
			.orElse(null);
	}

	/**
	 * Best tile to step onto when a telegraphed attack is about to land on the player's current
	 * tile. Unlike {@link #findSafeTile} this maximises distance from where the player is standing
	 * (capped at a couple of steps) so the marked tile is actually left behind.
	 */
	public WorldPoint findDodgeTile(WorldPoint bossTile)
	{
		List<WorldPoint> candidates = Rs2Tile.getSafeTiles(SEARCH_RADIUS);
		WorldPoint player = Rs2Player.getWorldLocation();
		if (candidates.isEmpty() || player == null)
		{
			return null;
		}

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> bossTile == null || tile.distanceTo(bossTile) >= MIN_BOSS_DISTANCE)
			.max(Comparator.comparingInt(tile -> Math.min(tile.distanceTo(player), 4)))
			.orElse(null);
	}

	/**
	 * Best tile to step onto so the boss is inside melee reach but not on top of the player. Used by
	 * the beam-charge punish, where the bow has been swapped for a halberd or another melee weapon.
	 * A halberd can stay two tiles away; a normal weapon has to be adjacent.
	 */
	public WorldPoint findMeleeTile(WorldPoint bossTile, int reach)
	{
		if (bossTile == null)
		{
			return null;
		}

		List<WorldPoint> candidates = Rs2Tile.getSafeTiles(SEARCH_RADIUS);
		if (candidates.isEmpty())
		{
			return null;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		int maxReach = Math.max(1, reach);

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> tile.distanceTo(bossTile) >= 1 && tile.distanceTo(bossTile) <= maxReach)
			.min(Comparator.comparingInt(tile -> player == null ? 0 : tile.distanceTo(player)))
			.orElse(null);
	}

	/**
	 * Best tile to step onto so a spawned larva is inside ranged/magic reach without walking into
	 * the boss. Returns {@code null} when no suitable tile is nearby.
	 */
	public WorldPoint findRangedTile(WorldPoint targetTile, WorldPoint bossTile, int maxRange)
	{
		if (targetTile == null)
		{
			return null;
		}

		List<WorldPoint> candidates = Rs2Tile.getSafeTiles(SEARCH_RADIUS);
		if (candidates.isEmpty())
		{
			return null;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		int range = Math.max(1, maxRange);

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> tile.distanceTo(targetTile) <= range)
			.filter(tile -> bossTile == null || tile.distanceTo(bossTile) >= MIN_BOSS_DISTANCE)
			.min(Comparator.comparingInt(tile -> player == null ? 0 : tile.distanceTo(player)))
			.orElse(null);
	}

	private int score(WorldPoint tile, WorldPoint bossTile, WorldPoint player, int preferredDistance)
	{
		int distanceToBoss = bossTile == null ? preferredDistance : tile.distanceTo(bossTile);
		int distanceToPlayer = player == null ? 0 : tile.distanceTo(player);
		return Math.abs(distanceToBoss - preferredDistance) * 10 + distanceToPlayer;
	}

	public void reset()
	{
		lastWalkTime = 0;
	}
}
