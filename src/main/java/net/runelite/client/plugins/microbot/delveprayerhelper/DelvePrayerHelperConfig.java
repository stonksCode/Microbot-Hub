package net.runelite.client.plugins.microbot.delveprayerhelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperOffensivePrayer;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperThrall;

@ConfigGroup(DelvePrayerHelperConfig.configGroup)
public interface DelvePrayerHelperConfig extends Config {
	String configGroup = "delve-prayer-helper";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Recasting",
		description = "Recast Arceuus spells during the fight",
		position = 1
	)
	String recastSection = "recast";

	@ConfigSection(
		name = "Fight log",
		description = "Write a detailed log of every fight so a bad run can be reviewed",
		position = 2
	)
	String fightLogSection = "fightLog";

	@ConfigSection(
		name = "Survival",
		description = "Dodge hazards, step out of acid, and keep supplies topped up",
		position = 3
	)
	String survivalSection = "survival";

	@ConfigSection(
		name = "Earth shield",
		description = "Handle the volatile earth / earthen shield slam",
		position = 4
	)
	String shieldSection = "shield";

	@ConfigItem(
		keyName = "offensivePrayer",
		name = "Offensive Prayer",
		description = "Enable to use offensive ranged prayer",
		position = 0,
		section = generalSection
	)
	default boolean offensivePrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "offensivePrayerType",
		name = "Offensive prayer type",
		description = "Which offensive prayer to use. 'Best available' uses the highest ranged prayer you have. "
			+ "If the prayer you pick is not unlocked or your level is too low, the best available one is used instead.",
		position = 1,
		section = generalSection
	)
	default DelvePrayerHelperOffensivePrayer offensivePrayerType()
	{
		return DelvePrayerHelperOffensivePrayer.BEST_AVAILABLE;
	}

    @ConfigItem(
            keyName = "noOffensivePrayerInShieldPhase",
            name = "No Offensive Prayer in Shield Phase",
            description = "Enable to toggle the offensive prayer in the shield phase for saving prayer points",
            position = 2,
            section = generalSection
    )
    default boolean noOffensivePrayerInShieldPhase()
    {
        return true;
    }

    @ConfigItem(
            keyName = "landingMarginCycles",
            name = "Switch delay after hit (cycles)",
            description = "Client cycles (20ms each) to wait after an attack lands before switching to the next attack's prayer. "
                    + "0 switches as soon as the hit has landed, which is what back-to-back attacks need. "
                    + "Only raise it if an attack still hurts right as the plugin switches away from its prayer.",
            position = 3,
            section = generalSection
    )
    @Range(min = 0, max = 15)
    default int landingMarginCycles()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Log every prayer click and recast with its timing, and warn when a hit lands without the right prayer on",
            position = 4,
            section = generalSection
    )
    default boolean debugLogging()
    {
        return false;
    }

    @ConfigItem(
            keyName = "recastThrall",
            name = "Recast thrall",
            description = "Summon the selected thrall whenever none is active and it can be cast. "
                    + "Needs the Arceuus spellbook, the Book of the Dead (inventory or equipped) and the runes.",
            position = 0,
            section = recastSection
    )
    default boolean recastThrall()
    {
        return false;
    }

    @ConfigItem(
            keyName = "thrallType",
            name = "Thrall",
            description = "Which thrall to summon",
            position = 1,
            section = recastSection
    )
    default DelvePrayerHelperThrall thrallType()
    {
        return DelvePrayerHelperThrall.GREATER_GHOST;
    }

    @ConfigItem(
            keyName = "recastDeathCharge",
            name = "Recast Death Charge",
            description = "Cast Death Charge whenever it is off cooldown and not active. "
                    + "Needs the Arceuus spellbook, the level and the runes.",
            position = 2,
            section = recastSection
    )
    default boolean recastDeathCharge()
    {
        return false;
    }

    @ConfigItem(
            keyName = "fightLog",
            name = "Write fight log",
            description = "Write every fight event (projectiles, prayers, damage, spawns, animations, graphics) "
                    + "to ~/.runelite/logs/delve-prayer-helper/fight-<timestamp>.log so it can be shared for review.",
            position = 0,
            section = fightLogSection
    )
    default boolean fightLog()
    {
        return true;
    }

    @ConfigItem(
            keyName = "verboseTickLog",
            name = "Verbose tick log",
            description = "Also write a line every game tick with health, prayer, protection prayer, boss animation "
                    + "and hazard state. Useful for diagnosing a missed switch, but makes the log much larger.",
            position = 1,
            section = fightLogSection
    )
    default boolean verboseTickLog()
    {
        return false;
    }

    @ConfigItem(
            keyName = "dodgeRockThrow",
            name = "Dodge Rock Throw",
            description = "Step off the marked tile before the Rock Throw explodes (7 ticks after the animation).",
            position = 0,
            section = survivalSection
    )
    default boolean dodgeRockThrow()
    {
        return true;
    }

    @ConfigItem(
            keyName = "rockDodgeTicks",
            name = "Dodge at (ticks before impact)",
            description = "How many ticks before the rock lands to start moving. 2 is a good default: "
                    + "moving too early can put you back on the marked tile.",
            position = 1,
            section = survivalSection
    )
    @Range(min = 1, max = 6)
    default int rockDodgeTicks()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "avoidAcid",
            name = "Avoid acid pools",
            description = "Step off acid blood when standing on it and treat acid tiles as dangerous when choosing a dodge tile.",
            position = 2,
            section = survivalSection
    )
    default boolean avoidAcid()
    {
        return true;
    }

    @ConfigItem(
            keyName = "enablePrayerRestoration",
            name = "Enable prayer restoration",
            description = "Master switch for drinking prayer potions. Turn this off to guarantee that a "
                    + "potion can never be drunk during a prayer switch (you then have to manage prayer points yourself).",
            position = 3,
            section = survivalSection
    )
    default boolean enablePrayerRestoration()
    {
        return true;
    }

    @ConfigItem(
            keyName = "prayerPotAtPoints",
            name = "Prayer pot at (points)",
            description = "Drink a prayer potion when prayer points fall below this value. 0 disables. "
                    + "Also drunk when protection prayer cannot be activated because prayer points ran out "
                    + "(only when prayer restoration is enabled).",
            position = 4,
            section = survivalSection
    )
    @Range(min = 0, max = 99)
    default int prayerPotAtPoints()
    {
        return 20;
    }

    @ConfigItem(
            keyName = "enableEating",
            name = "Enable eating",
            description = "Master switch for eating food. Turn this off to guarantee that eating can never "
                    + "happen during a prayer switch (you then have to eat manually).",
            position = 5,
            section = survivalSection
    )
    default boolean enableEating()
    {
        return true;
    }

    @ConfigItem(
            keyName = "eatAtPercent",
            name = "Eat at (%)",
            description = "Eat food when health falls below this percentage. 0 disables.",
            position = 6,
            section = survivalSection
    )
    @Range(min = 0, max = 100)
    default int eatAtPercent()
    {
        return 50;
    }

    @ConfigItem(
            keyName = "preferredDistance",
            name = "Preferred distance",
            description = "Ideal number of tiles to keep between you and the boss when stepping off acid.",
            position = 7,
            section = survivalSection
    )
    @Range(min = 3, max = 10)
    default int preferredDistance()
    {
        return 6;
    }

    @ConfigItem(
            keyName = "followEarthenShield",
            name = "Follow earthen shield",
            description = "Walk with the moving earthen shield during the volatile earth slam. "
                    + "The shield is the only safe spot while the slam lands, so the player follows its tile.",
            position = 0,
            section = shieldSection
    )
    default boolean followEarthenShield()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoSelectTotems",
            name = "Auto-select totems",
            description = "Destroy the two volatile earth totems that are farthest apart, which gives the shield "
                    + "the longest path. The first totem destroyed becomes the shield's destination, the second "
                    + "its spawn point. The nearest totem is destroyed first so the shield spawns next to you. "
                    + "Turn this off to choose the two totems yourself.",
            position = 1,
            section = shieldSection
    )
    default boolean autoSelectTotems()
    {
        return true;
    }
}
