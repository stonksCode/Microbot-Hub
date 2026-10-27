package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A rectangular group of tiles that will be hit by a shaman mechanic, together with
 * the time the area stops being dangerous.
 *
 * <p>Both areas are anchored from the tile the mechanic is centred on:</p>
 * <ul>
 *     <li><b>Temple jump</b> - 2x2, with the player's take-off tile as the north-east corner.</li>
 *     <li><b>Surface jump</b> - 3x3 centred on the player's take-off tile.</li>
 *     <li><b>Temple spawn</b> - 3x3 centred on the spawn NPC (1 tile radius).</li>
 *     <li><b>Surface spawn</b> - 5x5 centred on the spawn NPC (2 tile radius).</li>
 * </ul>
 */
public final class LizardmanShamanDangerArea
{
	public enum Type
	{
		JUMP,
		SPAWN
	}

	private final WorldPoint anchor;
	private final int width;
	private final int height;
	private final long expiresAt;
	private final Type type;
	private final String label;
	private final List<WorldPoint> tiles;

	private LizardmanShamanDangerArea(WorldPoint anchor, int width, int height, long expiresAt, Type type, String label)
	{
		this.anchor = anchor;
		this.width = width;
		this.height = height;
		this.expiresAt = expiresAt;
		this.type = type;
		this.label = label;

		List<WorldPoint> areaTiles = new ArrayList<>(width * height);
		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				areaTiles.add(new WorldPoint(anchor.getX() + x, anchor.getY() + y, anchor.getPlane()));
			}
		}
		this.tiles = Collections.unmodifiableList(areaTiles);
	}

	public static LizardmanShamanDangerArea jump(WorldPoint playerTile, boolean temple, int safetyTiles, long expiresAt)
	{
		// The temple 2x2 has the player tile as its north-east corner. The surface 3x3 is
		// centred, which yields the same south-west offset of one tile.
		int size = temple ? 2 : 3;
		return build(playerTile, size, size, 1, 1, safetyTiles, Type.JUMP, "Jump", expiresAt);
	}

	public static LizardmanShamanDangerArea spawn(WorldPoint spawnTile, boolean temple, int safetyTiles, long expiresAt)
	{
		int radius = temple ? 1 : 2;
		int size = radius * 2 + 1;
		return build(spawnTile, size, size, radius, radius, safetyTiles, Type.SPAWN, "Spawn", expiresAt);
	}

	private static LizardmanShamanDangerArea build(WorldPoint origin, int width, int height, int offsetX, int offsetY,
												   int safetyTiles, Type type, String label, long expiresAt)
	{
		int margin = Math.max(0, safetyTiles);
		WorldPoint southWest = new WorldPoint(
				origin.getX() - offsetX - margin,
				origin.getY() - offsetY - margin,
				origin.getPlane());
		return new LizardmanShamanDangerArea(
				southWest,
				width + margin * 2,
				height + margin * 2,
				expiresAt,
				type,
				label);
	}

	public boolean contains(WorldPoint point)
	{
		if (point == null || point.getPlane() != anchor.getPlane())
		{
			return false;
		}
		return point.getX() >= anchor.getX()
				&& point.getX() < anchor.getX() + width
				&& point.getY() >= anchor.getY()
				&& point.getY() < anchor.getY() + height;
	}

	/**
	 * @return {@code true} when the tile is inside the area or within {@code buffer}
	 * tiles of its edge.
	 */
	public boolean isNear(WorldPoint point, int buffer)
	{
		if (point == null || point.getPlane() != anchor.getPlane())
		{
			return false;
		}
		int pad = Math.max(0, buffer);
		return point.getX() >= anchor.getX() - pad
				&& point.getX() < anchor.getX() + width + pad
				&& point.getY() >= anchor.getY() - pad
				&& point.getY() < anchor.getY() + height + pad;
	}

	public boolean isExpired(long now)
	{
		return now >= expiresAt;
	}

	public WorldPoint getAnchor()
	{
		return anchor;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}

	public long getExpiresAt()
	{
		return expiresAt;
	}

	public Type getType()
	{
		return type;
	}

	public String getLabel()
	{
		return label;
	}

	public List<WorldPoint> getTiles()
	{
		return tiles;
	}
}
