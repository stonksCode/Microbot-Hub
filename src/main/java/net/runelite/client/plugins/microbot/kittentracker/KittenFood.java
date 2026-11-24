package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Foods a kitten will accept, per the OSRS wiki: raw/cooked fish, milk, frog spawn,
 * barbarian roe/caviar, and the blighted fish.
 */
public enum KittenFood {
    ANY("Any available food", -1),
    RAW_KARAMBWANJI("Raw karambwanji", ItemID.TBWT_RAW_KARAMBWANJI),
    BUCKET_OF_MILK("Bucket of milk", ItemID.BUCKET_MILK),
    BOTTOMLESS_MILK_BUCKET("Bottomless milk bucket (filled)", ItemID.BOTTOMLESS_MILK_BUCKET_FILLED),
    RAW_SARDINE("Raw sardine", ItemID.RAW_SARDINE),
    SARDINE("Sardine", ItemID.SARDINE),
    SEASONED_SARDINE("Seasoned sardine", ItemID.SEASONED_SARDINE),
    RAW_SHRIMP("Raw shrimp", ItemID.RAW_SHRIMP),
    SHRIMP("Shrimp", ItemID.SHRIMP),
    RAW_ANCHOVIES("Raw anchovies", ItemID.RAW_ANCHOVIES),
    ANCHOVIES("Anchovies", ItemID.ANCHOVIES),
    RAW_TROUT("Raw trout", ItemID.RAW_TROUT),
    TROUT("Trout", ItemID.TROUT),
    RAW_SALMON("Raw salmon", ItemID.RAW_SALMON),
    SALMON("Salmon", ItemID.SALMON),
    RAW_COD("Raw cod", ItemID.RAW_COD),
    COD("Cod", ItemID.COD),
    RAW_PIKE("Raw pike", ItemID.RAW_PIKE),
    PIKE("Pike", ItemID.PIKE),
    RAW_TUNA("Raw tuna", ItemID.RAW_TUNA),
    TUNA("Tuna", ItemID.TUNA),
    RAW_BASS("Raw bass", ItemID.RAW_BASS),
    BASS("Bass", ItemID.BASS),
    RAW_SWORDFISH("Raw swordfish", ItemID.RAW_SWORDFISH),
    SWORDFISH("Swordfish", ItemID.SWORDFISH),
    RAW_LOBSTER("Raw lobster", ItemID.RAW_LOBSTER),
    LOBSTER("Lobster", ItemID.LOBSTER),
    RAW_SHARK("Raw shark", ItemID.RAW_SHARK),
    SHARK("Shark", ItemID.SHARK),
    RAW_MANTARAY("Raw manta ray", ItemID.RAW_MANTARAY),
    MANTARAY("Manta ray", ItemID.MANTARAY),
    RAW_SEA_TURTLE("Raw sea turtle", ItemID.RAW_SEATURTLE),
    SEA_TURTLE("Sea turtle", ItemID.SEATURTLE),
    RAW_ANGLERFISH("Raw anglerfish", ItemID.RAW_ANGLERFISH),
    ANGLERFISH("Anglerfish", ItemID.ANGLERFISH),
    BLIGHTED_ANGLERFISH("Blighted anglerfish", ItemID.BLIGHTED_ANGLERFISH),
    BLIGHTED_MANTARAY("Blighted manta ray", ItemID.BLIGHTED_MANTARAY),
    BLIGHTED_KARAMBWAN("Blighted karambwan", ItemID.BLIGHTED_KARAMBWAN),
    FROG_SPAWN("Frog spawn", ItemID.GIANT_FROGSPAWN),
    ROE("Roe", ItemID.BRUT_ROE),
    CAVIAR("Caviar", ItemID.BRUT_CAVIAR);

    private final String displayName;
    private final int itemId;

    KittenFood(String displayName, int itemId) {
        this.displayName = displayName;
        this.itemId = itemId;
    }

    public int getItemId() {
        return itemId;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Preferred order when the user leaves the dropdown on "Any available food".
     * Stackable karambwanji comes first so a full trip only needs a small stack.
     */
    private static final int[] AUTO_PRIORITY = {
            ItemID.TBWT_RAW_KARAMBWANJI,
            ItemID.BUCKET_MILK,
            ItemID.BOTTOMLESS_MILK_BUCKET_FILLED,
            ItemID.RAW_SARDINE,
            ItemID.SARDINE,
            ItemID.SEASONED_SARDINE,
            ItemID.RAW_SHRIMP,
            ItemID.SHRIMP,
            ItemID.RAW_ANCHOVIES,
            ItemID.ANCHOVIES,
            ItemID.RAW_TROUT,
            ItemID.TROUT,
            ItemID.RAW_SALMON,
            ItemID.SALMON,
            ItemID.RAW_COD,
            ItemID.COD,
            ItemID.RAW_PIKE,
            ItemID.PIKE,
            ItemID.RAW_TUNA,
            ItemID.TUNA,
            ItemID.RAW_BASS,
            ItemID.BASS,
            ItemID.RAW_SWORDFISH,
            ItemID.SWORDFISH,
            ItemID.RAW_LOBSTER,
            ItemID.LOBSTER,
            ItemID.RAW_SHARK,
            ItemID.SHARK,
            ItemID.RAW_MANTARAY,
            ItemID.MANTARAY,
            ItemID.RAW_SEATURTLE,
            ItemID.SEATURTLE,
            ItemID.RAW_ANGLERFISH,
            ItemID.ANGLERFISH,
            ItemID.BLIGHTED_ANGLERFISH,
            ItemID.BLIGHTED_MANTARAY,
            ItemID.BLIGHTED_KARAMBWAN,
            ItemID.GIANT_FROGSPAWN,
            ItemID.BRUT_ROE,
            ItemID.BRUT_CAVIAR
    };

    /**
     * Returns the first valid food found in the inventory, or -1 if there is none.
     */
    public static int findAvailableFood() {
        for (int itemId : AUTO_PRIORITY) {
            if (Rs2Inventory.hasItem(itemId)) {
                return itemId;
            }
        }
        return -1;
    }
}
