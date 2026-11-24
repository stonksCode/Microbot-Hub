package net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

/**
 * Bank-standing Lunar spellbook recipes.
 *
 * <p>Unlike the modern spells, most Lunar production spells are cast once and
 * then consume every matching item in the inventory themselves. The number of
 * input sets processed per cast therefore matters for rune budgeting and is
 * stored on each {@link Variant}.</p>
 */
public enum LunarSpell {
    PLANK_MAKE("Plank make", MagicAction.PLANK_MAKE, 86,
            new int[]{ItemID.EARTHRUNE, ItemID.ASTRALRUNE, ItemID.NATURERUNE},
            new int[]{15, 2, 1},
            new Variant[]{
                    new Variant("Normal plank", 1, ItemID.LOGS, ItemID.WOODPLANK, 70),
                    new Variant("Oak plank", 1, ItemID.OAK_LOGS, ItemID.PLANK_OAK, 175),
                    new Variant("Teak plank", 1, ItemID.TEAK_LOGS, ItemID.PLANK_TEAK, 350),
                    new Variant("Mahogany plank", 1, ItemID.MAHOGANY_LOGS, ItemID.PLANK_MAHOGANY, 1050),
                    new Variant("Camphor plank", 1, ItemID.CAMPHOR_LOGS, ItemID.PLANK_CAMPHOR, 1750),
                    new Variant("Ironwood plank", 1, ItemID.IRONWOOD_LOGS, ItemID.PLANK_IRONWOOD, 3500),
                    new Variant("Rosewood plank", 1, ItemID.ROSEWOOD_LOGS, ItemID.PLANK_ROSEWOOD, 5250)
            }),

    BAKE_PIE("Bake pie", MagicAction.BAKE_PIE, 65,
            new int[]{ItemID.FIRERUNE, ItemID.WATERRUNE, ItemID.ASTRALRUNE},
            new int[]{5, 4, 1},
            new Variant[]{
                    new Variant("Redberry pie", 1, ItemID.UNCOOKED_REDBERRY_PIE, ItemID.REDBERRY_PIE, 0),
                    new Variant("Meat pie", 1, ItemID.UNCOOKED_MEAT_PIE, ItemID.MEAT_PIE, 0),
                    new Variant("Mud pie", 1, ItemID.UNCOOKED_MUD_PIE, ItemID.MUD_PIE, 0),
                    new Variant("Apple pie", 1, ItemID.UNCOOKED_APPLE_PIE, ItemID.APPLE_PIE, 0),
                    new Variant("Garden pie", 1, ItemID.UNCOOKED_GARDEN_PIE, ItemID.GARDEN_PIE, 0),
                    new Variant("Fish pie", 1, ItemID.UNCOOKED_FISH_PIE, ItemID.FISH_PIE, 0),
                    new Variant("Botanical pie", 1, ItemID.UNCOOKED_BOTANICAL_PIE, ItemID.BOTANICAL_PIE, 0),
                    new Variant("Mushroom pie", 1, ItemID.UNCOOKED_MUSHROOM_PIE, ItemID.MUSHROOM_PIE, 0),
                    new Variant("Admiral pie", 1, ItemID.UNCOOKED_ADMIRAL_PIE, ItemID.ADMIRAL_PIE, 0),
                    new Variant("Dragonfruit pie", 1, ItemID.UNCOOKED_DRAGONFRUIT_PIE, ItemID.DRAGONFRUIT_PIE, 0),
                    new Variant("Wild pie", 1, ItemID.UNCOOKED_WILD_PIE, ItemID.WILD_PIE, 0),
                    new Variant("Summer pie", 1, ItemID.UNCOOKED_SUMMER_PIE, ItemID.SUMMER_PIE, 0)
            }),

    SPIN_FLAX("Spin flax", MagicAction.SPIN_FLAX, 76,
            new int[]{ItemID.AIRRUNE, ItemID.ASTRALRUNE, ItemID.NATURERUNE},
            new int[]{5, 1, 2},
            new Variant[]{
                    new Variant("Bowstring", 5, ItemID.FLAX, ItemID.BOW_STRING, 0)
            }),

    SUPERGLASS_MAKE("Superglass make", MagicAction.SUPERGLASS_MAKE, 77,
            new int[]{ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.ASTRALRUNE},
            new int[]{10, 6, 2},
            new Variant[]{
                    new Variant("Sand + seaweed", 18,
                            new int[]{ItemID.BUCKET_SAND, ItemID.SEAWEED},
                            new int[]{1, 1}, ItemID.MOLTEN_GLASS, 0),
                    new Variant("Sand + soda ash", 18,
                            new int[]{ItemID.BUCKET_SAND, ItemID.SODA_ASH},
                            new int[]{1, 1}, ItemID.MOLTEN_GLASS, 0),
                    new Variant("Sand + giant seaweed", 18,
                            new int[]{ItemID.BUCKET_SAND, ItemID.GIANT_SEAWEED},
                            new int[]{1, 1}, ItemID.MOLTEN_GLASS, 0)
            }),

    TAN_LEATHER("Tan leather", MagicAction.TAN_LEATHER, 78,
            new int[]{ItemID.FIRERUNE, ItemID.ASTRALRUNE, ItemID.NATURERUNE},
            new int[]{5, 2, 1},
            new Variant[]{
                    new Variant("Cowhide", 5, ItemID.COW_HIDE, ItemID.LEATHER, 0),
                    new Variant("Green dragonhide", 5, ItemID.DRAGONHIDE_GREEN, ItemID.DRAGON_LEATHER, 0),
                    new Variant("Blue dragonhide", 5, ItemID.DRAGONHIDE_BLUE, ItemID.DRAGON_LEATHER_BLUE, 0),
                    new Variant("Red dragonhide", 5, ItemID.DRAGONHIDE_RED, ItemID.DRAGON_LEATHER_RED, 0),
                    new Variant("Black dragonhide", 5, ItemID.DRAGONHIDE_BLACK, ItemID.DRAGON_LEATHER_BLACK, 0)
            }),

    STRING_JEWELLERY("String jewellery", MagicAction.STRING_JEWELLERY, 80,
            new int[]{ItemID.EARTHRUNE, ItemID.WATERRUNE, ItemID.ASTRALRUNE},
            new int[]{10, 5, 2},
            new Variant[]{
                    new Variant("Gold amulet", 1, ItemID.UNSTRUNG_GOLD_AMULET, ItemID.STRUNG_GOLD_AMULET, 0),
                    new Variant("Sapphire amulet", 1, ItemID.UNSTRUNG_SAPPHIRE_AMULET, ItemID.STRUNG_SAPPHIRE_AMULET, 0),
                    new Variant("Emerald amulet", 1, ItemID.UNSTRUNG_EMERALD_AMULET, ItemID.STRUNG_EMERALD_AMULET, 0),
                    new Variant("Ruby amulet", 1, ItemID.UNSTRUNG_RUBY_AMULET, ItemID.STRUNG_RUBY_AMULET, 0),
                    new Variant("Diamond amulet", 1, ItemID.UNSTRUNG_DIAMOND_AMULET, ItemID.STRUNG_DIAMOND_AMULET, 0),
                    new Variant("Dragonstone amulet", 1, ItemID.UNSTRUNG_DRAGONSTONE_AMULET, ItemID.STRUNG_DRAGONSTONE_AMULET, 0),
                    new Variant("Opal amulet", 1, ItemID.UNSTRUNG_OPAL_AMULET, ItemID.STRUNG_OPAL_AMULET, 0),
                    new Variant("Jade amulet", 1, ItemID.UNSTRUNG_JADE_AMULET, ItemID.STRUNG_JADE_AMULET, 0),
                    new Variant("Topaz amulet", 1, ItemID.UNSTRUNG_TOPAZ_AMULET, ItemID.STRUNG_TOPAZ_AMULET, 0),
                    new Variant("Onyx amulet", 1, ItemID.UNSTRUNG_ONYX_AMULET, ItemID.STRUNG_ONYX_AMULET, 0),
                    new Variant("Zenyte amulet", 1, ItemID.UNSTRUNG_ZENYTE_AMULET, ItemID.ZENYTE_AMULET, 0)
            });

    /** One selectable input/output pair for a Lunar spell. */
    public static final class Variant {
        private final String name;
        private final int setsPerCast;
        private final int[] inputIds;
        private final int[] inputQuantities;
        private final int outputId;
        private final int coinsPerSet;

        public Variant(String name, int setsPerCast, int inputId, int outputId, int coinsPerSet) {
            this(name, setsPerCast, new int[]{inputId}, new int[]{1}, outputId, coinsPerSet);
        }

        public Variant(String name, int setsPerCast, int[] inputIds, int[] inputQuantities,
                       int outputId, int coinsPerSet) {
            this.name = name;
            this.setsPerCast = Math.max(1, setsPerCast);
            this.inputIds = inputIds.clone();
            this.inputQuantities = inputQuantities == null ? new int[inputIds.length] : inputQuantities.clone();
            for (int i = 0; i < this.inputQuantities.length; i++) {
                if (this.inputQuantities[i] < 1) {
                    this.inputQuantities[i] = 1;
                }
            }
            this.outputId = outputId;
            this.coinsPerSet = Math.max(0, coinsPerSet);
            if (this.inputIds.length != this.inputQuantities.length) {
                throw new IllegalArgumentException("Input ids and quantities must match");
            }
        }

        public String getName() { return name; }
        public int getSetsPerCast() { return setsPerCast; }
        public int[] getInputIds() { return inputIds.clone(); }
        public int[] getInputQuantities() { return inputQuantities.clone(); }
        public int getOutputId() { return outputId; }
        public int getCoinsPerSet() { return coinsPerSet; }

        @Override
        public String toString() { return name; }
    }

    private final String displayName;
    private final MagicAction spell;
    private final int level;
    private final int[] runeIds;
    private final int[] runeQuantities;
    private final Variant[] variants;

    LunarSpell(String displayName, MagicAction spell, int level,
               int[] runeIds, int[] runeQuantities, Variant[] variants) {
        this.displayName = displayName;
        this.spell = spell;
        this.level = level;
        this.runeIds = runeIds;
        this.runeQuantities = runeQuantities;
        this.variants = variants;
    }

    public String getDisplayName() { return displayName; }
    public MagicAction getSpell() { return spell; }
    public int getLevel() { return level; }
    public int[] getRuneIds() { return runeIds.clone(); }
    public int[] getRuneQuantities() { return runeQuantities.clone(); }
    public Variant[] getVariants() { return variants.clone(); }

    public Variant getVariant(String name) {
        for (Variant variant : variants) {
            if (variant.getName().equals(name)) {
                return variant;
            }
        }
        return variants[0];
    }

    public String[] getSelections() {
        String[] selections = new String[variants.length];
        for (int i = 0; i < variants.length; i++) {
            selections[i] = variants[i].getName();
        }
        return selections;
    }

    public String getDefaultSelection() {
        return variants[0].getName();
    }

    public static String[] getSelections(LunarSpell spell) {
        return spell == null ? new String[0] : spell.getSelections();
    }

    public static String getDefaultSelection(LunarSpell spell) {
        return spell == null ? "" : spell.getDefaultSelection();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
