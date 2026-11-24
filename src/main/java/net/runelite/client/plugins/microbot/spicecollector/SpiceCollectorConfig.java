package net.runelite.client.plugins.microbot.spicecollector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigInformation("<h2>Spice Collector</h2>"
        + "<p>Collects 4-dose spices by sending your cat into the Hell-Rat Behemoth alcoves in "
        + "Evil Dave's Basement of Doom.</p>"
        + "<p><b>Requirements:</b> Freeing Evil Dave completed, a cat or kitten following you, "
        + "and food in your inventory.</p>"
        + "<p><b>Recommended:</b> Raw karambwanji. It stacks, so one inventory slot is enough "
        + "for a very long session.</p>"
        + "<p>You can start anywhere; the script walks to the selected spice's curtain.</p>")
@ConfigGroup(SpiceCollectorConfig.configGroup)
public interface SpiceCollectorConfig extends Config {
    String configGroup = "spicecollector";

    @ConfigSection(
            name = "General",
            description = "Spice and collection settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Cat",
            description = "Cat healing settings",
            position = 1
    )
    String catSection = "cat";

    @ConfigSection(
            name = "Debug",
            description = "Troubleshooting options",
            position = 2
    )
    String debugSection = "debug";

    @ConfigItem(
            keyName = "spiceType",
            name = "Spice to collect",
            description = "Which Hell-Rat Behemoth alcove to use.",
            position = 0,
            section = generalSection
    )
    default SpiceType spiceType() {
        return SpiceType.RED;
    }

    @ConfigItem(
            keyName = "targetSpices",
            name = "Spices to collect",
            description = "Stop after collecting this many spices. Set to 0 to collect endlessly.",
            position = 1,
            section = generalSection
    )
    @Range(min = 0, max = 100000)
    default int targetSpices() {
        return 0;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Cat food",
            description = "Exact name of the food used to heal your cat. Raw karambwanji is recommended because it stacks.",
            position = 0,
            section = catSection
    )
    default String foodName() {
        return "Raw karambwanji";
    }

    @ConfigItem(
            keyName = "feedIntervalMs",
            name = "Feed interval (ms)",
            description = "How often to offer food to the cat during a fight. Lower values are safer for kittens, higher values waste less food.",
            position = 1,
            section = catSection
    )
    @Range(min = 600, max = 5000)
    default int feedIntervalMs() {
        return 1500;
    }

    @ConfigItem(
            keyName = "healAtPercent",
            name = "Feed at % health",
            description = "Feed the cat when its health bar is at or below this percentage. If the health bar is unavailable, food is offered every feed interval.",
            position = 2,
            section = catSection
    )
    @Range(min = 10, max = 100)
    default int healAtPercent() {
        return 85;
    }

    @ConfigItem(
            keyName = "stopWhenOutOfFood",
            name = "Stop when out of food",
            description = "Stop before entering another fight if the configured cat food is gone.",
            position = 3,
            section = catSection
    )
    default boolean stopWhenOutOfFood() {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug trace",
            description = "Write a detailed trace to the RuneLite log and to ~/.runelite/spice-collector/debug-<time>.log. Turn this on when reporting a problem.",
            position = 0,
            section = debugSection
    )
    default boolean debug() {
        return false;
    }
}
