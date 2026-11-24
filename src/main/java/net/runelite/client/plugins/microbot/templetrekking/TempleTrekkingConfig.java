package net.runelite.client.plugins.microbot.templetrekking;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("templetrekking")
@ConfigInformation(
        "<html><b>Temple Trekking (easy route)</b><br />"
                + "Start at either Paterdomus or Burgh de Rott with an axe, knife, hammer and food "
                + "in your inventory. The plugin escorts easy followers, evades every combat event "
                + "and solves the broken bridge / swamp vine events.<br /><br />"
                + "Both ends of the minigame must be unlocked. After each delivery the script picks "
                + "up the next follower at the end it arrives at, so it alternates direction.</html>")
public interface TempleTrekkingConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "Where, how long and optional lumberjack farming",
            position = 0
    )
    String generalSection = "generalSection";

    @ConfigSection(
            name = "Supplies",
            description = "Inventory prechecks and food",
            position = 1
    )
    String suppliesSection = "suppliesSection";

    @ConfigSection(
            name = "Rewards",
            description = "When and how to redeem reward tokens",
            position = 2
    )
    String rewardsSection = "rewardsSection";

    @ConfigItem(
            keyName = "startSide",
            name = "Start side",
            description = "Preferred end for the first pickup only. After that both hubs are accepted "
                    + "so treks alternate automatically.",
            position = 0,
            section = generalSection
    )
    default TempleTrekkingStartSide startSide() {
        return TempleTrekkingStartSide.AUTO;
    }

    @Range(min = 0, max = 1000)
    @ConfigItem(
            keyName = "maxTreks",
            name = "Max treks",
            description = "Stop after this many completed treks. 0 = run until stopped.",
            position = 1,
            section = generalSection
    )
    default int maxTreks() {
        return 0;
    }

    @ConfigItem(
            keyName = "farmLumberjacks",
            name = "Farm lumberjacks",
            description = "Undead Lumberjack bridge event: keep killing lumberjacks until one "
                    + "Lumberjack outfit piece drops, then finish the bridge. Only one piece drops "
                    + "per encounter. When off, kill only enough lumberjacks for the three Planks "
                    + "needed to repair the bridge. Planks and any dropped pieces are always looted.",
            position = 2,
            section = generalSection
    )
    default boolean farmLumberjacks() {
        return false;
    }

    @ConfigItem(
            keyName = "checkSupplies",
            name = "Check supplies",
            description = "Log a warning when the axe, knife or hammer needed for events is missing",
            position = 0,
            section = suppliesSection
    )
    default boolean checkSupplies() {
        return true;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Food name",
            description = "Exact inventory food name used during Undead Lumberjack combat. "
                    + "Blank disables eating.",
            position = 1,
            section = suppliesSection
    )
    default String foodName() {
        return "Swordfish";
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "eatAtHpPercent",
            name = "Eat at HP %",
            description = "Eat configured food when hitpoints fall below this percentage",
            position = 2,
            section = suppliesSection
    )
    default int eatAtHpPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "redeemRewards",
            name = "Redeem reward tokens",
            description = "Redeem tokens once the inventory holds the configured number. When off, "
                    + "tokens simply accumulate in the inventory.",
            position = 0,
            section = rewardsSection
    )
    default boolean redeemRewards() {
        return false;
    }

    @Range(min = 1, max = 28)
    @ConfigItem(
            keyName = "rewardThreshold",
            name = "Redeem at tokens",
            description = "Redeem all accumulated tokens once the inventory holds at least this "
                    + "many. Each token occupies a slot, so leave room for event supplies. The "
                    + "overlay shows the current count.",
            position = 1,
            section = rewardsSection
    )
    default int rewardThreshold() {
        return 5;
    }

    @ConfigItem(
            keyName = "rewardType",
            name = "Reward to redeem",
            description = "Reward option selected in the Temple Trekking Rewards interface. XP "
                    + "tomes give experience in one of seven random skills.",
            position = 2,
            section = rewardsSection
    )
    default TempleTrekkingReward rewardType() {
        return TempleTrekkingReward.XP_TOME;
    }
}
