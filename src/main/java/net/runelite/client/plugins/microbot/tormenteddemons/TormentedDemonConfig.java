package net.runelite.client.plugins.microbot.tormenteddemons;

import lombok.Getter;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

@ConfigInformation("IMPORTANT!<br/>"
        + "This plugin automates Tormented Demon kills, including combat, prayer, banking, and restocking.<br/><br/>"
        + "For <b>Full Auto</b> mode, ensure you have the required items and setup in your bank:<br/><br />"
        + "<p>Have the following in your bank and inventory setup:</p>\n"
        +"<ol>\n"
        + "    <li><b>Inventory Setup named \"tormented\"</b> – includes all required supplies</li>\n"
        + "    <li>Guthixian Temple teleport scrolls</li>\n"
        + "    <li>Ring of dueling</li>\n"
        + "</ol>\n"
        + "Full Auto mode also supports auto-looting and retreating when low on supplies to restock.<br/><br/>"
        + "<b>Magic utility</b>: Mark of Darkness and thralls are only used while the current combat style is magic. "
        + "You must be on the Arceuus spellbook, have the Book of the Dead for thralls, and the required runes.<br/><br/>"
        + "<br/>"
        + "<b>For Combat Only</b>: Position your character near the demons and start the plugin.<br/>"
        + "Configure options in the settings to enable prayer switching, auto gear change, and looting preferences.<br/>"
        + "Auto gear switching only uses styles that have a non-empty gear list.<br/>"
        + "</html>")


        @ConfigGroup("tormenteddemon")
public interface TormentedDemonConfig extends Config {

    @ConfigSection(
            name = "General Settings",
            description = "Full Auto or Combat only",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Choose the bot mode: Full Auto or Combat Only",
            section = generalSettings,
            position = 0
    )
    default MODE mode() {
        return MODE.FULL_AUTO;
    }

    @ConfigSection(
            name = "Tormented Demon Settings",
            description = "Settings for Tormented Demon boss helper",
            position = 1
    )
    String tormentedDemonSection = "tormentedDemon";

    @ConfigItem(
            keyName = "autoPrayerSwitch",
            name = "Enable Defensive Prayer",
            description = "Automatically switch prayers for Tormented Demon.",
            section = tormentedDemonSection,
            position = 2
    )
    default boolean enableDefensivePrayer() {
        return true;
    }

    @ConfigItem(
            keyName = "enableOffensivePrayer",
            name = "Enable Offensive Prayer",
            description = "Toggle offensive prayers during combat; the prayer used per style is set in the Offensive Prayers section",
            section = tormentedDemonSection,
            position = 3
    )
    default boolean enableOffensivePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "autoGearSwitch",
            name = "Auto Gear Switch",
            description = "Automatically switch gear for Tormented Demon.",
            section = tormentedDemonSection,
            position = 4
    )
    default boolean autoGearSwitch() {
        return true;
    }


    @ConfigItem(
            keyName = "dodgeFirebombs",
            name = "Dodge Firebombs",
            description = "Automatically move off firebomb impact tiles during the Tormented Demon special attack",
            section = tormentedDemonSection,
            position = 5
    )
    default boolean dodgeFirebombs() {
        return true;
    }

    @ConfigItem(
            keyName = "debugDodge",
            name = "Debug Dodging",
            description = "Log firebomb projectile and impact tile detection to the client log",
            section = tormentedDemonSection,
            position = 6
    )
    default boolean debugDodge() {
        return false;
    }

    @ConfigItem(
            keyName = "verifyWeapon",
            name = "Verify Correct Weapon",
            description = "Continuously verify the equipped weapon counters the demon's protection prayer and retry failed gear switches",
            section = tormentedDemonSection,
            position = 7
    )
    default boolean verifyWeapon() {
        return true;
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use Special Attack",
            description = "Enable the special attack of the equipped weapon while fighting when enough energy is available",
            section = tormentedDemonSection,
            position = 8
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "debugPrayer",
            name = "Debug Prayer Switching",
            description = "Log Tormented Demon animations, predicted/actual attack styles, prayer switches, and incoming hits",
            section = tormentedDemonSection,
            position = 9
    )
    default boolean debugPrayer() {
        return false;
    }


    @ConfigSection(
            name = "Looting",
            description = "Settings for item looting",
            position = 6
    )
    String lootingSection = "looting";

    @ConfigItem(
            keyName = "toggleLoot",
            name = "Enable Looting",
            description = "Enable looting of ground items",
            section = lootingSection,
            position = 0
    )
    default boolean toggleLoot() {
        return true;
    }

    @ConfigItem(
            keyName = "lootStyle",
            name = "Loot Style",
            description = "How to decide what to loot",
            section = lootingSection,
            position = 1
    )
    default LooterStyle looterStyle() {
        return LooterStyle.MIXED;
    }

    @ConfigItem(
            keyName = "lootItems",
            name = "List of Items to Loot",
            description = "Comma-separated list of item names to loot (used by Item List/Mixed)",
            section = lootingSection,
            position = 2
    )
    default String lootItems() {
        return "";
    }

    @ConfigItem(
            keyName = "minPriceOfItemsToLoot",
            name = "Min Price",
            description = "Minimum GE price of items to loot (Value Range)",
            section = lootingSection,
            position = 3
    )
    @Range(min = 0, max = Integer.MAX_VALUE)
    default int minPriceOfItemsToLoot() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxPriceOfItemsToLoot",
            name = "Max Price",
            description = "Maximum GE price of items to loot (Value Range)",
            section = lootingSection,
            position = 4
    )
    @Range(min = 0, max = Integer.MAX_VALUE)
    default int maxPriceOfItemsToLoot() {
        return Integer.MAX_VALUE;
    }

    @ConfigItem(
            keyName = "lootRadius",
            name = "Loot Radius",
            description = "Radius to search for loot",
            section = lootingSection,
            position = 5
    )
    @Range(min = 1, max = 20)
    default int lootRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "lootDelay",
            name = "Loot Delay (ms)",
            description = "Pause after a kill before looting and targeting the next demon",
            section = lootingSection,
            position = 6
    )
    @Range(min = 0, max = 5000)
    default int lootDelay() {
        return 1500;
    }

    @ConfigItem(
            keyName = "toggleLootMyItemsOnly",
            name = "Loot My Items Only",
            description = "Only loot items owned by you",
            section = lootingSection,
            position = 7
    )
    default boolean toggleLootMyItemsOnly() {
        return false;
    }

    @ConfigItem(
            keyName = "toggleDelayedLooting",
            name = "Delayed Looting",
            description = "Only loot items near despawn",
            section = lootingSection,
            position = 8
    )
    default boolean toggleDelayedLooting() {
        return false;
    }

    @ConfigItem(
            keyName = "lootCoins",
            name = "Loot Coins",
            description = "Loot coins",
            section = lootingSection,
            position = 9
    )
    default boolean lootCoins() {
        return true;
    }

    @ConfigItem(
            keyName = "lootBones",
            name = "Loot Bones",
            description = "Loot bones",
            section = lootingSection,
            position = 10
    )
    default boolean lootBones() {
        return false;
    }

    @ConfigItem(
            keyName = "lootAshes",
            name = "Loot Ashes",
            description = "Loot ashes",
            section = lootingSection,
            position = 11
    )
    default boolean lootAshes() {
        return false;
    }

    @ConfigItem(
            keyName = "scatterAshes",
            name = "Scatter Ashes",
            description = "Scatter Infernal Ashes upon looting",
            section = lootingSection,
            position = 12
    )
    default boolean scatterAshes() {
        return false;
    }

    @ConfigItem(
            keyName = "lootRunes",
            name = "Loot Runes",
            description = "Loot runes",
            section = lootingSection,
            position = 13
    )
    default boolean lootRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "lootArrows",
            name = "Loot Arrows",
            description = "Loot arrows",
            section = lootingSection,
            position = 14
    )
    default boolean lootArrows() {
        return false;
    }

    @ConfigItem(
            keyName = "lootUntradables",
            name = "Loot Untradables",
            description = "Loot untradeable items",
            section = lootingSection,
            position = 15
    )
    default boolean lootUntradables() {
        return false;
    }

    @ConfigItem(
            keyName = "lootHerbs",
            name = "Loot Herbs (Grimy/Cleaned)",
            description = "Loot all grimy and cleaned herbs",
            section = lootingSection,
            position = 16
    )
    default boolean lootHerbs() {
        return true;
    }



    @ConfigSection(
            name = "Food and Potions",
            description = "Settings for banking and required supplies",
            position = 4
    )
    String bankingAndSuppliesSection = "bankingAndSupplies";

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Minimum Health Percent",
            description = "Percentage of health below which the bot will eat food",
            section = bankingAndSuppliesSection,
            position = 0
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Minimum Prayer Percent",
            description = "Percentage of prayer points below which the bot will drink a prayer potion or retreat",
            section = bankingAndSuppliesSection,
            position = 1
    )
    @Range(
            min = 1,
            max = 99
    )
    default int minPrayerPercent() {
        return 20;
    }

    @ConfigItem(
            keyName = "healthThreshold",
            name = "Health Threshold to Exit",
            description = "Minimum health percentage to stay and fight",
            section = bankingAndSuppliesSection,
            position = 2
    )
    @Range(
            min = 1,
            max = 99
    )
    default int healthThreshold() {
        return 30;
    }


    @ConfigItem(
            keyName = "combatPotionType",
            name = "Combat Potion Type",
            description = "Select the type of combat potion to use",
            section = bankingAndSuppliesSection,
            position = 3
    )
    default CombatPotionType combatPotionType() {
        return CombatPotionType.SUPER_COMBAT;
    }

    @ConfigItem(
            keyName = "rangingPotionType",
            name = "Ranging Potion Type",
            description = "Select the type of ranging potion to use",
            section = bankingAndSuppliesSection,
            position = 4
    )
    default RangingPotionType rangingPotionType() {
        return RangingPotionType.RANGING;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = bankingAndSuppliesSection,
            position = 5
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 10;
    }

    @ConfigSection(
            name = "Gear Settings",
            description = "Specify gear sets and combat styles for switching",
            position = 5,
            closedByDefault = true
    )
    String gearSettingsSection = "gearSettings";

    @ConfigItem(
            keyName = "copyGear",
            name = "Copy Gear Setup",
            description = "Click to copy your currently equipped gear to the clipboard",
            section = gearSettingsSection,
            position = 0
    )
    default ConfigButton copyGear() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = "useRangeStyle",
            name = "Use Range Style",
            description = "Toggle to use range gear and style",
            section = gearSettingsSection,
            position = 1
    )
    default boolean useRangeStyle() {
        return true;
    }

    @ConfigItem(
            keyName = "rangeGear",
            name = "Range Gear",
            description = "List of items to equip for range attacks (comma separated)",
            section = gearSettingsSection,
            position = 2
    )
    default String rangeGear() {
        return " ";
    }

    @ConfigItem(
            keyName = "useMagicStyle",
            name = "Use Magic Style",
            description = "Toggle to use magic gear and style",
            section = gearSettingsSection,
            position = 3
    )
    default boolean useMagicStyle() {
        return true;
    }

    @ConfigItem(
            keyName = "magicGear",
            name = "Magic Gear",
            description = "List of items to equip for magic attacks (comma separated)",
            section = gearSettingsSection,
            position = 4
    )
    default String magicGear() {
        return " ";
    }

    @ConfigItem(
            keyName = "useMeleeStyle",
            name = "Use Melee Style",
            description = "Toggle to use melee gear and style",
            section = gearSettingsSection,
            position = 5
    )
    default boolean useMeleeStyle() {
        return true;
    }

    @ConfigItem(
            keyName = "meleeGear",
            name = "Melee Gear",
            description = "List of items to equip for melee attacks (comma separated)",
            section = gearSettingsSection,
            position = 6
    )
    default String meleeGear() {
        return " ";
    }

    @ConfigSection(
            name = "Magic Utility",
            description = "Arceuus Mark of Darkness and thrall settings",
            position = 3,
            closedByDefault = true
    )
    String magicUtilitySection = "magicUtility";

    @ConfigItem(
            keyName = "useMarkOfDarkness",
            name = "Use Mark of Darkness",
            description = "Cast Mark of Darkness on the current target while using magic on the Arceuus spellbook",
            section = magicUtilitySection,
            position = 0
    )
    default boolean useMarkOfDarkness() {
        return true;
    }

    @ConfigItem(
            keyName = "useThralls",
            name = "Use Thralls",
            description = "Summon the best available thrall while using magic on the Arceuus spellbook",
            section = magicUtilitySection,
            position = 1
    )
    default boolean useThralls() {
        return true;
    }

    @ConfigSection(
            name = "Offensive Prayers",
            description = "Choose which offensive prayer to use for each combat style",
            position = 2,
            closedByDefault = false
    )
    String offensivePrayersSection = "offensivePrayers";

    @ConfigItem(
            keyName = "meleeOffensivePrayer",
            name = "Melee Prayer",
            description = "Offensive prayer used while fighting with melee",
            section = offensivePrayersSection,
            position = 0
    )
    default MeleeOffensivePrayer meleeOffensivePrayer() {
        return MeleeOffensivePrayer.AUTO;
    }

    @ConfigItem(
            keyName = "rangeOffensivePrayer",
            name = "Ranged Prayer",
            description = "Offensive prayer used while fighting with ranged",
            section = offensivePrayersSection,
            position = 1
    )
    default RangedOffensivePrayer rangeOffensivePrayer() {
        return RangedOffensivePrayer.AUTO;
    }

    @ConfigItem(
            keyName = "magicOffensivePrayer",
            name = "Magic Prayer",
            description = "Offensive prayer used while fighting with magic",
            section = offensivePrayersSection,
            position = 2
    )
    default MagicOffensivePrayer magicOffensivePrayer() {
        return MagicOffensivePrayer.AUTO;
    }

    enum MODE {
        FULL_AUTO,
        COMBAT_ONLY
    }

    @Getter
    enum CombatPotionType {
        SUPER_COMBAT,
        DIVINE_SUPER_COMBAT

    }

    @Getter
    enum RangingPotionType {
        RANGING,
        DIVINE_RANGING,
        BASTION
    }

    enum MeleeOffensivePrayer {
        AUTO("Auto (best available)", null, true),
        NONE("None", null, false),
        BURST_OF_STRENGTH("Burst of Strength", Rs2PrayerEnum.BURST_STRENGTH, false),
        SUPERHUMAN_STRENGTH("Superhuman Strength", Rs2PrayerEnum.SUPERHUMAN_STRENGTH, false),
        ULTIMATE_STRENGTH("Ultimate Strength", Rs2PrayerEnum.ULTIMATE_STRENGTH, false),
        CHIVALRY("Chivalry", Rs2PrayerEnum.CHIVALRY, false),
        PIETY("Piety", Rs2PrayerEnum.PIETY, false);

        private final String label;
        private final Rs2PrayerEnum prayer;
        private final boolean auto;

        MeleeOffensivePrayer(String label, Rs2PrayerEnum prayer, boolean auto) {
            this.label = label;
            this.prayer = prayer;
            this.auto = auto;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }

        public boolean isAuto() {
            return auto;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum RangedOffensivePrayer {
        AUTO("Auto (best available)", null, true),
        NONE("None", null, false),
        SHARP_EYE("Sharp Eye", Rs2PrayerEnum.SHARP_EYE, false),
        HAWK_EYE("Hawk Eye", Rs2PrayerEnum.HAWK_EYE, false),
        EAGLE_EYE("Eagle Eye", Rs2PrayerEnum.EAGLE_EYE, false),
        DEADEYE("Deadeye (Royal Titans)", Rs2PrayerEnum.DEAD_EYE, false),
        RIGOUR("Rigour", Rs2PrayerEnum.RIGOUR, false);

        private final String label;
        private final Rs2PrayerEnum prayer;
        private final boolean auto;

        RangedOffensivePrayer(String label, Rs2PrayerEnum prayer, boolean auto) {
            this.label = label;
            this.prayer = prayer;
            this.auto = auto;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }

        public boolean isAuto() {
            return auto;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum MagicOffensivePrayer {
        AUTO("Auto (best available)", null, true),
        NONE("None", null, false),
        MYSTIC_WILL("Mystic Will", Rs2PrayerEnum.MYSTIC_WILL, false),
        MYSTIC_LORE("Mystic Lore", Rs2PrayerEnum.MYSTIC_LORE, false),
        MYSTIC_MIGHT("Mystic Might", Rs2PrayerEnum.MYSTIC_MIGHT, false),
        MYSTIC_VIGOUR("Mystic Vigour (Royal Titans)", Rs2PrayerEnum.MYSTIC_VIGOUR, false),
        AUGURY("Augury", Rs2PrayerEnum.AUGURY, false);

        private final String label;
        private final Rs2PrayerEnum prayer;
        private final boolean auto;

        MagicOffensivePrayer(String label, Rs2PrayerEnum prayer, boolean auto) {
            this.label = label;
            this.prayer = prayer;
            this.auto = auto;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }

        public boolean isAuto() {
            return auto;
        }

        @Override
        public String toString() {
            return label;
        }
    }

}



