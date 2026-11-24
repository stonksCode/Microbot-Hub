package net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.CastMode;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.Input;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.Recipe;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

/**
 * Every piece of enchantable jewellery, grouped by the spell that enchants it.
 * Runes and levels follow the standard spellbook; elemental runes supplied by
 * an equipped staff are handled by {@link net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor}.
 */
public enum JewelleryType {
    SAPPHIRE_RING("Sapphire ring", 7, ItemID.SAPPHIRE_RING, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    SAPPHIRE_AMULET("Sapphire amulet", 7, ItemID.SAPPHIRE_AMULET, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    SAPPHIRE_NECKLACE("Sapphire necklace", 7, ItemID.SAPPHIRE_NECKLACE, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    SAPPHIRE_BRACELET("Sapphire bracelet", 7, ItemID.SAPPHIRE_BRACELET, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    OPAL_RING("Opal ring", 7, ItemID.OPAL_RING, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    OPAL_AMULET("Opal amulet", 7, ItemID.OPAL_AMULET, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    OPAL_NECKLACE("Opal necklace", 7, ItemID.OPAL_NECKLACE, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),
    OPAL_BRACELET("Opal bracelet", 7, ItemID.OPAL_BRACELET, MagicAction.ENCHANT_SAPPHIRE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER}, new int[]{1, 1}),

    EMERALD_RING("Emerald ring", 27, ItemID.EMERALD_RING, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    EMERALD_AMULET("Emerald amulet", 27, ItemID.EMERALD_AMULET, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    EMERALD_NECKLACE("Emerald necklace", 27, ItemID.EMERALD_NECKLACE, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    EMERALD_BRACELET("Emerald bracelet", 27, ItemID.EMERALD_BRACELET, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    JADE_RING("Jade ring", 27, ItemID.JADE_RING, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    JADE_AMULET("Jade amulet", 27, ItemID.JADE_AMULET, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    JADE_NECKLACE("Jade necklace", 27, ItemID.JADE_NECKLACE, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),
    JADE_BRACELET("Jade bracelet", 27, ItemID.JADE_BRACELET, MagicAction.ENCHANT_EMERALD_JEWELLERY,
            new int[]{R.COSMIC, R.AIR}, new int[]{1, 3}),

    RUBY_RING("Ruby ring", 49, ItemID.RUBY_RING, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    RUBY_AMULET("Ruby amulet", 49, ItemID.RUBY_AMULET, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    RUBY_NECKLACE("Ruby necklace", 49, ItemID.RUBY_NECKLACE, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    RUBY_BRACELET("Ruby bracelet", 49, ItemID.RUBY_BRACELET, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    TOPAZ_RING("Topaz ring", 49, ItemID.TOPAZ_RING, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    TOPAZ_AMULET("Topaz amulet", 49, ItemID.TOPAZ_AMULET, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    TOPAZ_NECKLACE("Topaz necklace", 49, ItemID.TOPAZ_NECKLACE, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),
    TOPAZ_BRACELET("Topaz bracelet", 49, ItemID.TOPAZ_BRACELET, MagicAction.ENCHANT_RUBY_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE}, new int[]{1, 5}),

    DIAMOND_RING("Diamond ring", 57, ItemID.DIAMOND_RING, MagicAction.ENCHANT_DIAMOND_JEWELLERY,
            new int[]{R.COSMIC, R.EARTH}, new int[]{1, 10}),
    DIAMOND_AMULET("Diamond amulet", 57, ItemID.DIAMOND_AMULET, MagicAction.ENCHANT_DIAMOND_JEWELLERY,
            new int[]{R.COSMIC, R.EARTH}, new int[]{1, 10}),
    DIAMOND_NECKLACE("Diamond necklace", 57, ItemID.DIAMOND_NECKLACE, MagicAction.ENCHANT_DIAMOND_JEWELLERY,
            new int[]{R.COSMIC, R.EARTH}, new int[]{1, 10}),
    DIAMOND_BRACELET("Diamond bracelet", 57, ItemID.DIAMOND_BRACELET, MagicAction.ENCHANT_DIAMOND_JEWELLERY,
            new int[]{R.COSMIC, R.EARTH}, new int[]{1, 10}),

    DRAGONSTONE_RING("Dragonstone ring", 68, ItemID.DRAGONSTONE_RING, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER, R.EARTH}, new int[]{1, 15, 15}),
    DRAGONSTONE_AMULET("Dragonstone amulet", 68, ItemID.DRAGONSTONE_AMULET, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER, R.EARTH}, new int[]{1, 15, 15}),
    DRAGON_NECKLACE("Dragon necklace", 68, ItemID.DRAGON_NECKLACE, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER, R.EARTH}, new int[]{1, 15, 15}),
    DRAGONSTONE_BRACELET("Dragonstone bracelet", 68, ItemID.DRAGONSTONE_BRACELET, MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY,
            new int[]{R.COSMIC, R.WATER, R.EARTH}, new int[]{1, 15, 15}),

    ONYX_RING("Onyx ring", 87, ItemID.ONYX_RING, MagicAction.ENCHANT_ONYX_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE, R.EARTH}, new int[]{1, 20, 20}),
    ONYX_AMULET("Onyx amulet", 87, ItemID.ONYX_AMULET, MagicAction.ENCHANT_ONYX_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE, R.EARTH}, new int[]{1, 20, 20}),
    ONYX_NECKLACE("Onyx necklace", 87, ItemID.ONYX_NECKLACE, MagicAction.ENCHANT_ONYX_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE, R.EARTH}, new int[]{1, 20, 20}),
    ONYX_BRACELET("Onyx bracelet", 87, ItemID.ONYX_BRACELET, MagicAction.ENCHANT_ONYX_JEWELLERY,
            new int[]{R.COSMIC, R.FIRE, R.EARTH}, new int[]{1, 20, 20}),

    ZENYTE_RING("Zenyte ring", 93, ItemID.ZENYTE_RING, MagicAction.ENCHANT_ZENYTE_JEWELLERY,
            new int[]{R.COSMIC, R.BLOOD, R.SOUL}, new int[]{1, 20, 20}),
    ZENYTE_AMULET("Zenyte amulet", 93, ItemID.ZENYTE_AMULET, MagicAction.ENCHANT_ZENYTE_JEWELLERY,
            new int[]{R.COSMIC, R.BLOOD, R.SOUL}, new int[]{1, 20, 20}),
    ZENYTE_NECKLACE("Zenyte necklace", 93, ItemID.ZENYTE_NECKLACE, MagicAction.ENCHANT_ZENYTE_JEWELLERY,
            new int[]{R.COSMIC, R.BLOOD, R.SOUL}, new int[]{1, 20, 20}),
    ZENYTE_BRACELET("Zenyte bracelet", 93, ItemID.ZENYTE_BRACELET, MagicAction.ENCHANT_ZENYTE_JEWELLERY,
            new int[]{R.COSMIC, R.BLOOD, R.SOUL}, new int[]{1, 20, 20});

    private static final class R {
        static final int AIR = net.runelite.api.gameval.ItemID.AIRRUNE;
        static final int WATER = net.runelite.api.gameval.ItemID.WATERRUNE;
        static final int EARTH = net.runelite.api.gameval.ItemID.EARTHRUNE;
        static final int FIRE = net.runelite.api.gameval.ItemID.FIRERUNE;
        static final int COSMIC = net.runelite.api.gameval.ItemID.COSMICRUNE;
        static final int BLOOD = net.runelite.api.gameval.ItemID.BLOODRUNE;
        static final int SOUL = net.runelite.api.gameval.ItemID.SOULRUNE;
    }

    private final String displayName;
    private final int level;
    private final int itemId;
    private final MagicAction spell;
    private final int[] runeIds;
    private final int[] runeQuantities;

    JewelleryType(String displayName, int level, int itemId, MagicAction spell,
                  int[] runeIds, int[] runeQuantities) {
        this.displayName = displayName;
        this.level = level;
        this.itemId = itemId;
        this.spell = spell;
        this.runeIds = runeIds;
        this.runeQuantities = runeQuantities;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    public int getItemId() {
        return itemId;
    }

    public Recipe toRecipe() {
        return new Recipe(displayName, level, spell, CastMode.CAST_USE,
                runeIds, runeQuantities, new Input[]{Input.id(itemId)}, 0);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
