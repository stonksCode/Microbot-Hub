package net.runelite.client.plugins.microbot.autokraken;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("autokraken")
public interface AutoKrakenConfig extends Config {

    @ConfigSection(
            name = "Fight",
            description = "Slayer task and combat settings",
            position = 0
    )
    String fightSection = "fightSection";

    @ConfigSection(
            name = "Loot",
            description = "What to pick up after each kill",
            position = 1
    )
    String lootSection = "lootSection";

    @ConfigItem(
            keyName = "requireSlayerTask",
            name = "Require Kraken task",
            description = "Stop the plugin if you are not currently assigned cave krakens / Kraken.",
            position = 0,
            section = fightSection
    )
    default boolean requireSlayerTask() {
        return true;
    }

    @ConfigItem(
            keyName = "useFishingExplosive",
            name = "Use fishing explosive",
            description = "Use a fishing explosive on the large whirlpool to instantly awaken the Kraken.",
            position = 1,
            section = fightSection
    )
    default boolean useFishingExplosive() {
        return true;
    }

    @ConfigItem(
            keyName = "fallbackToDisturb",
            name = "Fall back to disturbing",
            description = "If there are no fishing explosives left, disturb the four small whirlpools and then the main one instead of stopping.",
            position = 2,
            section = fightSection
    )
    default boolean fallbackToDisturb() {
        return true;
    }

    @ConfigItem(
            keyName = "eatAtPercent",
            name = "Eat at % HP",
            description = "Eat food when health drops to or below this percentage during the fight.",
            position = 3,
            section = fightSection
    )
    @Range(min = 1, max = 99)
    default int eatAtPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "useQuickPrayers",
            name = "Use quick prayers",
            description = "Keep quick prayers enabled while fighting. Make sure your quick prayers are configured beforehand.",
            position = 4,
            section = fightSection
    )
    default boolean useQuickPrayers() {
        return false;
    }

    @ConfigItem(
            keyName = "stopWhenOutOfFood",
            name = "Stop when out of food",
            description = "Stop between kills when there is no edible food left in the inventory.",
            position = 5,
            section = fightSection
    )
    default boolean stopWhenOutOfFood() {
        return true;
    }

    @ConfigItem(
            keyName = "stopOnDeath",
            name = "Stop on death",
            description = "Stop the plugin when the death message appears in chat.",
            position = 6,
            section = fightSection
    )
    default boolean stopOnDeath() {
        return true;
    }

    @ConfigItem(
            keyName = "listOfItemsToLoot",
            name = "Extra loot names",
            description = "Comma-separated item names to loot in addition to Kraken uniques, which are always looted.",
            position = 0,
            section = lootSection
    )
    default String listOfItemsToLoot() {
        return "";
    }

    @ConfigItem(
            keyName = "toggleLootByValue",
            name = "Also loot by GE value",
            description = "If on, also loot any item above the minimum value below.",
            position = 1,
            section = lootSection
    )
    default boolean toggleLootByValue() {
        return true;
    }

    @ConfigItem(
            keyName = "minPriceOfItemsToLoot",
            name = "Min GE value",
            description = "Minimum GE value to loot. Only used when 'Also loot by GE value' is on.",
            position = 2,
            section = lootSection
    )
    default int minPriceOfItemsToLoot() {
        return 5000;
    }

    @ConfigItem(
            keyName = "lootCoins",
            name = "Loot coins",
            description = "Always pick up coin drops.",
            position = 3,
            section = lootSection
    )
    default boolean lootCoins() {
        return true;
    }
}
