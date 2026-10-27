package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.awt.Color;

@ConfigGroup("LizardmanShaman")
public interface LizardmanShamanConfig extends Config
{
	@ConfigSection(
			name = "General",
			description = "General behaviour of the script",
			position = 0
	)
	String general = "general";

	@ConfigSection(
			name = "Combat",
			description = "Food, prayer and potion settings",
			position = 1
	)
	String combat = "combat";

	@ConfigSection(
			name = "Mechanics",
			description = "Jump attack and purple spawn handling",
			position = 2
	)
	String mechanics = "mechanics";

	@ConfigSection(
			name = "Overlay",
			description = "On-screen information and tile markers",
			position = 3
	)
	String overlay = "overlay";

	@ConfigItem(
			keyName = "stopAfterDeath",
			name = "Stop after death",
			description = "Stop the script when you die",
			position = 0,
			section = general
	)
	default boolean stopAfterDeath()
	{
		return true;
	}

	@ConfigItem(
			keyName = "searchRadius",
			name = "Search radius",
			description = "Only consider shamans and spawns within this many tiles",
			position = 1,
			section = general
	)
	@Range(min = 3, max = 20)
	default int searchRadius()
	{
		return 12;
	}

	@ConfigItem(
			keyName = "foodMinPercent",
			name = "Eat below (min)",
			description = "Lower bound of the health percentage at which to eat",
			position = 0,
			section = combat
	)
	@Range(min = 1, max = 99)
	default int foodMinPercent()
	{
		return 45;
	}

	@ConfigItem(
			keyName = "foodMaxPercent",
			name = "Eat below (max)",
			description = "Upper bound of the randomised eat threshold",
			position = 1,
			section = combat
	)
	@Range(min = 2, max = 99)
	default int foodMaxPercent()
	{
		return 60;
	}

	@ConfigItem(
			keyName = "overheadPrayer",
			name = "Overhead prayer",
			description = "Protection prayer to keep active. Use Melee when fighting up close, Missiles when kiting",
			position = 2,
			section = combat
	)
	default OverheadPrayer overheadPrayer()
	{
		return OverheadPrayer.PROTECT_MISSILES;
	}

	@ConfigItem(
			keyName = "offensivePrayer",
			name = "Offensive prayer",
			description = "Offensive prayer to keep active alongside the overhead",
			position = 3,
			section = combat
	)
	default OffensivePrayer offensivePrayer()
	{
		return OffensivePrayer.NONE;
	}

	@ConfigItem(
			keyName = "useAntipoison",
			name = "Use antipoison",
			description = "Drink an antipoison potion when the shaman's acid spit poisons you",
			position = 4,
			section = combat
	)
	default boolean useAntipoison()
	{
		return true;
	}

	@ConfigItem(
			keyName = "prayerMinPercent",
			name = "Drink prayer below (min)",
			description = "Lower bound of the prayer percentage at which to drink a potion",
			position = 5,
			section = combat
	)
	@Range(min = 1, max = 99)
	default int prayerMinPercent()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "prayerMaxPercent",
			name = "Drink prayer below (max)",
			description = "Upper bound of the randomised prayer potion threshold",
			position = 6,
			section = combat
	)
	@Range(min = 2, max = 100)
	default int prayerMaxPercent()
	{
		return 25;
	}

	@ConfigItem(
			keyName = "dodgeJumps",
			name = "Avoid jump attacks",
			description = "When a shaman jumps at you, move to one of the southern safe spots",
			position = 0,
			section = mechanics
	)
	default boolean dodgeJumps()
	{
		return true;
	}

	@ConfigItem(
			keyName = "dodgeSpawns",
			name = "Move on purple spawns",
			description = "Wait for the configured number of ticks, then move to a different safe spot",
			position = 1,
			section = mechanics
	)
	default boolean dodgeSpawns()
	{
		return true;
	}

	@ConfigItem(
			keyName = "safetyTiles",
			name = "Safety margin",
			description = "Extra tiles added around every danger area",
			position = 2,
			section = mechanics
	)
	@Range(min = 0, max = 3)
	default int safetyTiles()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "spawnDodgeDelayTicks",
			name = "Spawn move delay (ticks)",
			description = "Game ticks to keep fighting after spawns appear before moving to another safe spot",
			position = 3,
			section = mechanics
	)
	@Range(min = 0, max = 6)
	default int spawnDodgeDelayTicks()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "jumpAnimationIds",
			name = "Jump animation ids (optional)",
			description = "Comma-separated animation ids treated as a jump. Leave empty to detect jumps from the animation duration and the landing teleport.",
			position = 4,
			section = mechanics
	)
	default String jumpAnimationIds()
	{
		return "";
	}

	@ConfigItem(
			keyName = "showOverlay",
			name = "Show info panel",
			description = "Show the script state panel",
			position = 0,
			section = overlay
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showDangerTiles",
			name = "Show danger tiles",
			description = "Highlight tiles covered by jumps and spawns",
			position = 1,
			section = overlay
	)
	default boolean showDangerTiles()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showDodgeTile",
			name = "Show move target",
			description = "Highlight the tile the script is walking to",
			position = 2,
			section = overlay
	)
	default boolean showDodgeTile()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
			keyName = "dangerColor",
			name = "Danger color",
			description = "Color used for dangerous tiles",
			position = 3,
			section = overlay
	)
	default Color dangerColor()
	{
		return new Color(255, 60, 60, 90);
	}

	@Alpha
	@ConfigItem(
			keyName = "dodgeColor",
			name = "Move target color",
			description = "Color used for the tile the script is moving to",
			position = 4,
			section = overlay
	)
	default Color dodgeColor()
	{
		return new Color(60, 255, 60, 120);
	}

	@ConfigItem(
			keyName = "showSafeSpots",
			name = "Show safe spots",
			description = "Highlight the three safe-spot groups",
			position = 5,
			section = overlay
	)
	default boolean showSafeSpots()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
			keyName = "safeSpotColor",
			name = "Safe spot color",
			description = "Color used for the safe-spot tiles",
			position = 6,
			section = overlay
	)
	default Color safeSpotColor()
	{
		return new Color(80, 160, 255, 40);
	}

	enum OverheadPrayer
	{
		NONE("None", null),
		PROTECT_MELEE("Protect from Melee", Rs2PrayerEnum.PROTECT_MELEE),
		PROTECT_MISSILES("Protect from Missiles", Rs2PrayerEnum.PROTECT_RANGE);

		private final String label;
		private final Rs2PrayerEnum prayer;

		OverheadPrayer(String label, Rs2PrayerEnum prayer)
		{
			this.label = label;
			this.prayer = prayer;
		}

		public Rs2PrayerEnum getPrayer()
		{
			return prayer;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum OffensivePrayer
	{
		NONE("None", null),
		PIETY("Piety", Rs2PrayerEnum.PIETY),
		RIGOUR("Rigour", Rs2PrayerEnum.RIGOUR),
		AUGURY("Augury", Rs2PrayerEnum.AUGURY);

		private final String label;
		private final Rs2PrayerEnum prayer;

		OffensivePrayer(String label, Rs2PrayerEnum prayer)
		{
			this.label = label;
			this.prayer = prayer;
		}

		public Rs2PrayerEnum getPrayer()
		{
			return prayer;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
