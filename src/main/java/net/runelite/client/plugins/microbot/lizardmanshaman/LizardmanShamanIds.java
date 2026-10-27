package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;

/**
 * NPC ids and the coarse temple bounds used by the script.
 *
 * <p>The temple variant (8565) is a 2x2 NPC, while the surface variants are 3x3. The
 * area-based check is a fallback for the few seconds between kills when no shaman is
 * visible but the player is still inside the temple.</p>
 */
public final class LizardmanShamanIds
{
	public static final int TEMPLE_SHAMAN = NpcID.MOLCH_LIZARDSHAMAN_1;

	/**
	 * Every standard lizardman shaman id, including the temple variant. Kaht B'alam is
	 * intentionally excluded: he is the friendly NPC and has no attack option.
	 */
	public static final int[] SHAMAN_IDS = {
			NpcID.ZEAH_LIZARDSHAMAN_1,
			NpcID.ZEAH_LIZARDSHAMAN_2,
			NpcID.RAIDS_LIZARDSHAMAN_A,
			NpcID.RAIDS_LIZARDSHAMAN_B,
			NpcID.LIZARDMAN_CAVE_SHAMAN_1,
			NpcID.LIZARDMAN_CAVE_SHAMAN_2,
			TEMPLE_SHAMAN
	};

	/**
	 * The purple spawns summoned by a shaman. The raids variant is named "blocker" in
	 * the game data but is the same explosion NPC.
	 */
	public static final int[] SPAWN_IDS = {NpcID.ZEAH_LIZARDSHAMAN_SPAWN, NpcID.RAIDS_LIZARDSHAMAN_BLOCKER};

	/**
	 * Coarse bounds for the Lizardman Temple beneath Molch. The temple uses an
	 * underground map around y = 10080, so a generous rectangle is enough to tell it
	 * apart from the surface shamans (which live around y = 3600).
	 */
	public static final WorldArea TEMPLE_AREA = new WorldArea(new WorldPoint(1220, 9990, 0), 220, 220);

	private LizardmanShamanIds()
	{
	}

	public static boolean isShaman(int npcId)
	{
		for (int id : SHAMAN_IDS)
		{
			if (id == npcId)
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isSpawn(int npcId)
	{
		for (int id : SPAWN_IDS)
		{
			if (id == npcId)
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isTempleShaman(int npcId)
	{
		return npcId == TEMPLE_SHAMAN;
	}
}
