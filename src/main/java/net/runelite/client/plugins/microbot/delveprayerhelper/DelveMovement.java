package net.runelite.client.plugins.microbot.delveprayerhelper;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.List;

/**
 * Tile selection and click-to-move helpers for the small instanced Delve arena.
 *
 * <p>Movement is deliberately canvas-click based. The arena is a small room, so pathfinding is
 * unnecessary and the slower walker would miss one or two tick dodges.</p>
 *
 * <p>The Delve arena is instanced, so every {@link WorldPoint} handled here is <b>instance world</b>
 * space: {@link WorldPoint#fromLocal(Client, LocalPoint)} of the actor's local tile. That is the space
 * {@link Rs2Tile#getWalkableTilesAroundTile(WorldPoint, int)} and
 * {@link Rs2Walker#walkFastCanvas(WorldPoint)} understand. It is <b>not</b> the template space returned
 * by {@code actor.getWorldLocation()} or {@code Rs2Player.getWorldLocation()}: those coordinates are
 * not part of the loaded scene, so the walker rejects them and walks to an unrelated fallback tile.</p>
 */
public class DelveMovement
{
	private static final int SEARCH_RADIUS = 6;
	private static final int MIN_BOSS_DISTANCE = 3;
	private static final long WALK_THROTTLE_MS = 250;

	/**
	 * Instance-world player tile, refreshed on the client thread by {@code ClientTick}.
	 *
	 * <p>Every client-thread round trip from the script thread can block for up to a game tick,
	 * which is fatal when two attacks land one tick apart. Reading this cache is free.</p>
	 */
	private static volatile WorldPoint cachedRawPlayerLocation;

	private final DelvePrayerHelperConfig config;
	private final DelveHazardTracker hazards;

	private long lastWalkTime;

	public DelveMovement(DelvePrayerHelperConfig config, DelveHazardTracker hazards)
	{
		this.config = config;
		this.hazards = hazards;
	}

	/**
	 * The player's tile in instance world space. This is the space every entity and
	 * {@link Rs2Tile#getWalkableTilesAroundTile(WorldPoint, int)} uses inside an instance.
	 *
	 * <p>Reads the cache maintained by {@link #updateRawPlayerLocation(WorldPoint)}. Only when the
	 * cache has never been filled (plugin enabled mid-login) does it fall back to a client-thread
	 * hop. The fast prayer loop must never hop: {@link Player#getLocalLocation()} is only safe on
	 * the client thread, and a hop from the script thread can block for a whole game tick.</p>
	 */
	public static WorldPoint getRawPlayerLocation()
	{
		WorldPoint cached = cachedRawPlayerLocation;
		if (cached != null)
		{
			return cached;
		}
		try
		{
			return Microbot.getClientThread().invoke(() -> readRawPlayerLocation(Microbot.getClient()));
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/** Reads the player's instance-world tile. Must be called on the client thread. */
	public static WorldPoint readRawPlayerLocation(Client client)
	{
		if (client == null)
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		return local == null ? null : fromLocal(client, local.getLocalLocation());
	}

	/**
	 * Converts an entity's local tile to instance world space.
	 *
	 * <p>Inside an instance {@code actor.getWorldLocation()} returns the template location (the
	 * original world area the instance was copied from), not the coordinates of the scene the player
	 * is actually standing in. The walker and the tile helpers both use
	 * {@code WorldPoint.fromLocal}, so every position compared with them or with the player has to
	 * be converted through this method first.</p>
	 */
	public static WorldPoint fromLocal(Client client, LocalPoint local)
	{
		if (client == null || local == null)
		{
			return null;
		}
		return WorldPoint.fromLocal(client, local);
	}

	/** Stores the tile read on the client thread so the script thread can use it without hopping. */
	public static void updateRawPlayerLocation(WorldPoint location)
	{
		if (location != null)
		{
			cachedRawPlayerLocation = location;
		}
	}

	public boolean walkTo(WorldPoint target)
	{
		return walkTo(getRawPlayerLocation(), target);
	}

	/**
	 * Same as {@link #walkTo(WorldPoint)} but reuses an already-fetched player tile, so a loop that
	 * already knows where the player is does not pay for another client-thread round trip.
	 */
	public boolean walkTo(WorldPoint player, WorldPoint target)
	{
		if (target == null)
		{
			return false;
		}

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

		// true = make sure run is enabled: the shield moves one tile per tick and the player has to
		// keep pace. Never pass false here, that helper would switch run OFF.
		return Rs2Walker.walkFastCanvas(target, true);
	}

	/**
	 * Best tile to step onto: walkable, not acid, not adjacent to the boss, and close to the
	 * preferred bow distance. Returns {@code null} when nothing suitable is nearby.
	 */
	public WorldPoint findSafeTile(WorldPoint bossTile, int preferredDistance)
	{
		return findSafeTile(getRawPlayerLocation(), bossTile, preferredDistance);
	}

	public WorldPoint findSafeTile(WorldPoint player, WorldPoint bossTile, int preferredDistance)
	{
		if (player == null)
		{
			return null;
		}

		List<WorldPoint> candidates = Rs2Tile.getWalkableTilesAroundTile(player, SEARCH_RADIUS);
		if (candidates.isEmpty())
		{
			return null;
		}

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> bossTile == null || tile.distanceTo(bossTile) >= MIN_BOSS_DISTANCE)
			.filter(tile -> !tile.equals(player))
			.min(Comparator.comparingInt(tile -> score(tile, bossTile, player, preferredDistance)))
			.orElse(null);
	}

	/**
	 * Best tile to step onto when a telegraphed attack is about to land on the player's current
	 * tile. Unlike {@link #findSafeTile} this maximises distance from where the player is standing
	 * (capped at a few steps) so the marked tile is actually left behind.
	 */
	public WorldPoint findDodgeTile(WorldPoint bossTile)
	{
		return findDodgeTile(getRawPlayerLocation(), bossTile);
	}

	public WorldPoint findDodgeTile(WorldPoint player, WorldPoint bossTile)
	{
		if (player == null)
		{
			return null;
		}

		List<WorldPoint> candidates = Rs2Tile.getWalkableTilesAroundTile(player, SEARCH_RADIUS);
		if (candidates.isEmpty())
		{
			return null;
		}

		return candidates.stream()
			.filter(tile -> !config.avoidAcid() || !hazards.isAcidTile(tile))
			.filter(tile -> bossTile == null || tile.distanceTo(bossTile) >= MIN_BOSS_DISTANCE)
			.filter(tile -> !tile.equals(player))
			.max(Comparator.comparingInt(tile -> Math.min(tile.distanceTo(player), 4)))
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
