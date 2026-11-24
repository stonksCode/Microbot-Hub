package net.runelite.client.plugins.microbot.templetrekking;

import net.runelite.api.gameval.ItemID;

/**
 * Reward types offered by the Temple Trekking Rewards interface (interface 274).
 *
 * The ICONS container (17956870) holds one item widget per reward option. The live recording showed
 * both the exact child index and item id for every option, so selection can target the icon
 * directly. {@link #matches(String)} is kept only as a descriptive fallback.
 */
public enum TempleTrekkingReward {
    XP_TOME(7, "XP tome (random skill)",
            new int[]{
                    ItemID.TEMPLETREK_TOME_FISHING_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_AGILITY_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_THIEVING_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_SLAYER_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_MINING_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_FIREMAKING_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_WOODCUTTING_LEVEL_1,
                    ItemID.TEMPLETREK_TOME_FISHING_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_AGILITY_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_THIEVING_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_SLAYER_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_MINING_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_FIREMAKING_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_WOODCUTTING_LEVEL_2,
                    ItemID.TEMPLETREK_TOME_FISHING_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_AGILITY_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_THIEVING_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_SLAYER_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_MINING_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_FIREMAKING_LEVEL_3,
                    ItemID.TEMPLETREK_TOME_WOODCUTTING_LEVEL_3
            },
            "xp", "tome", "experience"),
    HERBS(2, "Herbs (grimy)", new int[]{ItemID.UNIDENTIFIED_TOADFLAX}, "herb"),
    PURE_ESSENCE(0, "Pure essence", new int[]{ItemID.BLANKRUNE_HIGH}, "pure essence", "essence"),
    BOW_STRINGS(1, "Bow strings", new int[]{ItemID.BOW_STRING}, "bow string", "bowstring"),
    SILVER_BARS(3, "Silver bars", new int[]{ItemID.SILVER_BAR}, "silver bar", "silver"),
    COAL_AND_IRON(4, "Coal & iron ore", new int[]{ItemID.COAL, ItemID.IRON_ORE},
            "coal", "iron ore", "iron"),
    WATERMELON_SEEDS(5, "Watermelon seeds", new int[]{ItemID.WATERMELON_SEED_5, ItemID.WATERMELON_SEED},
            "watermelon", "seed"),
    RAW_LOBSTERS(6, "Raw lobsters", new int[]{ItemID.RAW_LOBSTER}, "lobster");

    private final int iconIndex;
    private final String label;
    private final int[] iconItemIds;
    private final String[] matches;

    TempleTrekkingReward(int iconIndex, String label, int[] iconItemIds, String... matches) {
        this.iconIndex = iconIndex;
        this.label = label;
        this.iconItemIds = iconItemIds;
        this.matches = matches;
    }

    /** Child index of this option inside the TrekRewards.ICONS container. */
    public int iconIndex() {
        return iconIndex;
    }

    public String label() {
        return label;
    }

    /** Copy of the icon item ids, used to detect and read XP tomes after redemption. */
    public int[] iconItemIds() {
        return iconItemIds.clone();
    }

    /** True when the given ICONS child item id is this reward option. */
    public boolean matchesIconItem(int itemId) {
        if (itemId <= 0) {
            return false;
        }
        for (int iconItemId : iconItemIds) {
            if (itemId == iconItemId) {
                return true;
            }
        }
        return false;
    }

    /** True when the given widget text refers to this reward option. */
    public boolean matches(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String candidate : matches) {
            if (lower.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
