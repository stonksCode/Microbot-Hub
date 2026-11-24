package net.runelite.client.plugins.microbot.autobankstander.skills.magic;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.CastMode;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.Input;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.Recipe;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

/** Smelting recipes supported by the Superheat Item spell. */
public enum SuperheatOre {
    IRON("Iron bar", ItemID.IRON_ORE, 1, -1, 0, 15),
    SILVER("Silver bar", ItemID.SILVER_ORE, 2, -1, 0, 20),
    LEAD("Lead bar", ItemID.LEAD_ORE, 2, -1, 0, 25),
    STEEL("Steel bar", ItemID.IRON_ORE, 1, ItemID.COAL, 2, 30),
    GOLD("Gold bar", ItemID.GOLD_ORE, 1, -1, 0, 40),
    MITHRIL("Mithril bar", ItemID.MITHRIL_ORE, 1, ItemID.COAL, 4, 50),
    ADAMANTITE("Adamantite bar", ItemID.ADAMANTITE_ORE, 1, ItemID.COAL, 6, 70),
    CUPRONICKEL("Cupronickel bar", ItemID.NICKEL_ORE, 1, ItemID.COPPER_ORE, 2, 74),
    RUNITE("Runite bar", ItemID.RUNITE_ORE, 1, ItemID.COAL, 8, 85);

    private static final int MAGIC_LEVEL = 43;

    private final String displayName;
    private final int oreId;
    private final int oreQuantity;
    private final int additiveId;
    private final int additiveQuantity;
    private final int smithingLevel;

    SuperheatOre(String displayName, int oreId, int oreQuantity, int additiveId,
                 int additiveQuantity, int smithingLevel) {
        this.displayName = displayName;
        this.oreId = oreId;
        this.oreQuantity = oreQuantity;
        this.additiveId = additiveId;
        this.additiveQuantity = additiveQuantity;
        this.smithingLevel = smithingLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSmithingLevel() {
        return smithingLevel;
    }

    public Recipe toRecipe() {
        Input[] inputs = additiveId > 0
                ? new Input[]{Input.id(oreId, oreQuantity), Input.id(additiveId, additiveQuantity)}
                : new Input[]{Input.id(oreId, oreQuantity)};
        return new Recipe(displayName, MAGIC_LEVEL, MagicAction.SUPERHEAT_ITEM,
                CastMode.SUPERHEAT,
                new int[]{ItemID.FIRERUNE, ItemID.NATURERUNE}, new int[]{4, 1},
                inputs, 0);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
