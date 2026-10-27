package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One of the three fixed safe-spot groups the script rotates between.
 *
 * <p>The tiles are hard-coded for the Lizardman Temple. The plugin does not bank or travel,
 * so making them configurable only added parsing code and the risk of invalid input.</p>
 *
 * <p>The north-east corner sits in the open, so a shaman can jump onto it. The two southern
 * groups are shielded by the temple wall and are used whenever a jump is telegraphed.</p>
 */
public enum LizardmanShamanSafeSet
{
	NORTHEAST("North-east", true,
			new WorldPoint(1295, 10100, 0),
			new WorldPoint(1296, 10099, 0)),
	SOUTHEAST("South-east", false,
			new WorldPoint(1289, 10094, 0),
			new WorldPoint(1290, 10093, 0)),
	SOUTHWEST("South-west", false,
			new WorldPoint(1294, 10093, 0),
			new WorldPoint(1295, 10093, 0));

	private final String label;
	private final boolean jumpRisk;
	private final WorldPoint[] tiles;
	private final Map<Integer, List<WorldPoint>> tilesByPlane = new ConcurrentHashMap<>();

	LizardmanShamanSafeSet(String label, boolean jumpRisk, WorldPoint... tiles)
	{
		this.label = label;
		this.jumpRisk = jumpRisk;
		this.tiles = tiles;
	}

	public String getLabel()
	{
		return label;
	}

	/**
	 * @return {@code true} when a shaman can land a jump attack on this group.
	 */
	public boolean hasJumpRisk()
	{
		return jumpRisk;
	}

	/**
	 * @param plane plane to return the tiles on
	 * @return the group's tiles on that plane, cached per plane
	 */
	public List<WorldPoint> getTiles(int plane)
	{
		return tilesByPlane.computeIfAbsent(plane, this::buildTiles);
	}

	private List<WorldPoint> buildTiles(int plane)
	{
		final List<WorldPoint> result = new ArrayList<>(tiles.length);
		for (WorldPoint tile : tiles)
		{
			result.add(tile.getPlane() == plane
					? tile
					: new WorldPoint(tile.getX(), tile.getY(), plane));
		}
		return Collections.unmodifiableList(result);
	}
}
