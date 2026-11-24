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
            description = "Enable the special attack orb while fighting when the equipped weapon has enough energy. Also implied by Use Special Attack Gear",
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

    @ConfigItem(
            keyName = "gearSwitchMode",
            name = "Gear Switch Mode",
            description = "How gear swaps are performed. Normal = regular simulated mouse clicks. "
                    + "Quick = mouse clicks with a much shorter press/settle (still moves the mouse, but roughly twice as fast). "
                    + "Instant = every equip queued in a single game tick with no mouse movement (fastest, but the most robotic)",
            section = tormentedDemonSection,
            position = 10
    )
    default GearSwitchMode gearSwitchMode() {
        return GearSwitchMode.QUICK;
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

    @ConfigItem(
            keyName = "inventoryFullAction",
            name = "When Inventory Is Full",
            description = "What to try first when looting needs an inventory slot. Do nothing leaves the item on the ground; "
                    + "Stop plugin disables the plugin with a message; Eat food for space eats one food; "
                    + "Scatter ashes/bones scatters one ashes item or buries one bones item; "
                    + "Demonic Offering casts the Arceuus spell if you can cast it and are carrying demonic ashes",
            section = lootingSection,
            position = 17
    )
    default InventoryFullAction inventoryFullAction() {
        return InventoryFullAction.EAT_FOOD;
    }

    @ConfigItem(
            keyName = "inventoryFullFallback",
            name = "If That Fails (Fallback)",
            description = "Tried when the first option does not free an inventory slot. If this also fails, the plugin stops with an 'Inventory full' message",
            section = lootingSection,
            position = 18
    )
    default InventoryFullAction inventoryFullFallback() {
        return InventoryFullAction.STOP_PLUGIN;
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
            description = "Percentage of health below which the bot will eat food. In Combat Only this is also the point where it stops if no food is left",
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
            description = "Percentage of prayer points below which the bot will drink a prayer potion. If no prayer doses are left, Full Auto retreats and Combat Only stops",
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
            description = "Health percentage at which the bot leaves combat. Full Auto retreats and restocks; Combat Only stops the plugin",
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
            keyName = "stopWhenOutOfSupplies",
            name = "Stop When Out of Supplies",
            description = "Stop when no food or prayer potion is left. Full Auto retreats and stops if the bank cannot supply the setup; Combat Only stops in place",
            section = bankingAndSuppliesSection,
            position = 3
    )
    default boolean stopWhenOutOfSupplies() {
        return true;
    }

    @ConfigItem(
            keyName = "combatPotionType",
            name = "Combat Potion Type",
            description = "Select the type of combat potion to use",
            section = bankingAndSuppliesSection,
            position = 4
    )
    default CombatPotionType combatPotionType() {
        return CombatPotionType.SUPER_COMBAT;
    }

    @ConfigItem(
            keyName = "rangingPotionType",
            name = "Ranging Potion Type",
            description = "Select the type of ranging potion to use",
            section = bankingAndSuppliesSection,
            position = 5
    )
    default RangingPotionType rangingPotionType() {
        return RangingPotionType.RANGING;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "% Boosted Stats Threshold",
            description = "The threshold for using a potion when the boosted stats are below the maximum.",
            section = bankingAndSuppliesSection,
            position = 6
    )
    @Range(
            min = 1,
            max = 100
    )
    default int boostedStatsThreshold() {
        return 10;
    }

    @ConfigItem(
            keyName = "emergencyTeleport",
            name = "Emergency Teleport on Stop",
            description = "When a supply stop happens while still in combat, teleport out before the plugin disables itself. Prevents being left in the cave with nothing to heal",
            section = bankingAndSuppliesSection,
            position = 7
    )
    default boolean emergencyTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "emergencyTeleportMethod",
            name = "Emergency Teleport Method",
            description = "Teleport used before stopping. Items and teleport tabs are used from the inventory; jewellery is worn automatically. "
                    + "If the selected teleport is unavailable, the plugin works down a list of bank-adjacent fallbacks "
                    + "(dueling ring, glory, wealth, seed pod, Varrock/Lumbridge/Falador tabs, ...) and finally the Guthixian temple teleport",
            section = bankingAndSuppliesSection,
            position = 8
    )
    default EmergencyTeleportMethod emergencyTeleportMethod() {
        return EmergencyTeleportMethod.RING_OF_DUELING;
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
            name = "Copy Current Gear",
            description = "Click to copy the names of your currently equipped items to the clipboard as a comma separated list. Keep the plugin enabled while clicking.",
            section = gearSettingsSection,
            position = 0
    )
    default ConfigButton copyGear() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = "usePunishGear",
            name = "Use Punish Gear",
            description = "Swap to punish gear for the first shield-down hit after the fire bomb and dodge, chosen by the demon's overhead prayer",
            section = gearSettingsSection,
            position = 7
    )
    default boolean usePunishGear() {
        return true;
    }

    @ConfigItem(
            keyName = "punishGearVsMelee",
            name = "Punish Gear vs Melee Prayer",
            description = "Gear to use when demon has Protect from Melee overhead (e.g. ranged/ballistae or magic)",
            section = gearSettingsSection,
            position = 8
    )
    default String punishGearVsMelee() {
        return " ";
    }

    @ConfigItem(
            keyName = "punishGearVsRange",
            name = "Punish Gear vs Ranged Prayer",
            description = "Gear to use when demon has Protect from Ranged overhead (e.g. crush melee/granite maul)",
            section = gearSettingsSection,
            position = 9
    )
    default String punishGearVsRange() {
        return " ";
    }

    @ConfigItem(
            keyName = "punishGearVsMagic",
            name = "Punish Gear vs Magic Prayer",
            description = "Gear to use when demon has Protect from Magic overhead (e.g. melee or ranged)",
            section = gearSettingsSection,
            position = 10
    )
    default String punishGearVsMagic() {
        return " ";
    }

    @ConfigItem(
            keyName = "useSpecialAttackGear",
            name = "Use Special Attack Gear",
            description = "Swap to special attack gear while the demon is defenceless and energy meets the threshold. Also enables the special attack orb, so this alone is enough to use specs",
            section = gearSettingsSection,
            position = 11
    )
    default boolean useSpecialAttackGear() {
        return true;
    }

    @ConfigItem(
            keyName = "specialAttackGear",
            name = "Special Attack Gear (Fallback)",
            description = "Gear/weapon for special attack during defenseless phase when none of the per-prayer special attack gear boxes are set. Leave blank to never use special attack gear",
            section = gearSettingsSection,
            position = 12
    )
    default String specialAttackGear() {
        return " ";
    }

    @ConfigItem(
            keyName = "specialAttackGearVsMelee",
            name = "Special Attack Gear vs Melee Prayer",
            description = "Spec gear to use when the demon has Protect from Melee overhead. Leave blank to not special attack against this prayer (e.g. keep the Blue Moon spear away when its set is not worn)",
            section = gearSettingsSection,
            position = 13
    )
    default String specialAttackGearVsMelee() {
        return " ";
    }

    @ConfigItem(
            keyName = "specialAttackGearVsRange",
            name = "Special Attack Gear vs Ranged Prayer",
            description = "Spec gear to use when the demon has Protect from Ranged overhead. Leave blank to not special attack against this prayer",
            section = gearSettingsSection,
            position = 14
    )
    default String specialAttackGearVsRange() {
        return " ";
    }

    @ConfigItem(
            keyName = "specialAttackGearVsMagic",
            name = "Special Attack Gear vs Magic Prayer",
            description = "Spec gear to use when the demon has Protect from Magic overhead. Leave blank to not special attack against this prayer",
            section = gearSettingsSection,
            position = 15
    )
    default String specialAttackGearVsMagic() {
        return " ";
    }

    @ConfigItem(
            keyName = "specialAttackThreshold",
            name = "Special Attack Threshold",
            description = "Minimum special attack energy as a percentage of the full bar (0-100). The orb is used while energy is at or above this value and turned off once it drops below",
            section = gearSettingsSection,
            position = 16
    )
    @Range(min = 0, max = 100)
    default int specialAttackThreshold() {
        return 25;
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

    @ConfigSection(
            name = "Overlay",
            description = "Reset the overlay statistics and choose which stats are shown",
            position = 7,
            closedByDefault = false
    )
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = "resetStats",
            name = "Reset Statistics",
            description = "Click to reset the kill count, average and last kill time, and this trip's loot totals",
            section = overlaySection,
            position = 0
    )
    default ConfigButton resetStats() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = "showStatus",
            name = "Show Status",
            description = "Show the current plugin status (Banking, Travelling, Fighting, ...) on the overlay",
            section = overlaySection,
            position = 1
    )
    default boolean showStatus() {
        return true;
    }

    @ConfigItem(
            keyName = "showKillCount",
            name = "Show Kill Count",
            description = "Show the session kill count on the overlay",
            section = overlaySection,
            position = 2
    )
    default boolean showKillCount() {
        return true;
    }

    @ConfigItem(
            keyName = "showAverageKillTime",
            name = "Show Average Kill Time",
            description = "Show the average kill time on the overlay",
            section = overlaySection,
            position = 3
    )
    default boolean showAverageKillTime() {
        return true;
    }

    @ConfigItem(
            keyName = "showLastKillTime",
            name = "Show Last Kill Time",
            description = "Show the duration of the most recent kill on the overlay",
            section = overlaySection,
            position = 4
    )
    default boolean showLastKillTime() {
        return true;
    }

    @ConfigItem(
            keyName = "showTripLoot",
            name = "Show Loot This Trip",
            description = "Show the total Grand Exchange value of items looted since the current trip began",
            section = overlaySection,
            position = 5
    )
    default boolean showTripLoot() {
        return true;
    }

    @ConfigItem(
            keyName = "showAverageLoot",
            name = "Show Average Loot Per Kill",
            description = "Show this trip's loot value divided by its kill count",
            section = overlaySection,
            position = 6
    )
    default boolean showAverageLoot() {
        return true;
    }

    enum MODE {
        FULL_AUTO,
        COMBAT_ONLY
    }

    @Getter
    enum GearSwitchMode {
        NORMAL("Normal (mouse clicks)"),
        QUICK("Quick clicks"),
        INSTANT("Instant (one tick)");

        private final String label;

        GearSwitchMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Getter
    enum InventoryFullAction {
        DO_NOTHING("Do nothing"),
        STOP_PLUGIN("Stop plugin"),
        EAT_FOOD("Eat food for space"),
        SCATTER_ASHES("Scatter ashes/bones"),
        DEMONIC_OFFERING("Demonic Offering");

        private final String label;

        InventoryFullAction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
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

    @Getter
    enum EmergencyTeleportMethod {
        RING_OF_DUELING("Ring of Dueling (Ferox Enclave)"),
        AMULET_OF_GLORY("Amulet of Glory (Edgeville)"),
        RING_OF_WEALTH("Ring of Wealth (Grand Exchange)"),
        SKILLS_NECKLACE("Skills Necklace (Fishing Guild)"),
        COMBAT_BRACELET("Combat Bracelet (Warriors' Guild)"),
        ROYAL_SEED_POD("Royal seed pod (Tree Gnome Stronghold)"),
        VARROCK_TAB("Teleport tab: Varrock"),
        LUMBRIDGE_TAB("Teleport tab: Lumbridge"),
        FALADOR_TAB("Teleport tab: Falador"),
        CAMELOT_TAB("Teleport tab: Camelot"),
        ARDOUGNE_TAB("Teleport tab: Ardougne"),
        WATCHTOWER_TAB("Teleport tab: Watchtower (Yanille)"),
        HOUSE_TAB("Teleport tab: House"),
        GUTHIXIAN_TEMPLE("Guthixian temple teleport"),
        NONE("None");

        private final String label;

        EmergencyTeleportMethod(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
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



