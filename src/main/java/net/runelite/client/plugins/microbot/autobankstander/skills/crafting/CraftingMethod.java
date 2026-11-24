package net.runelite.client.plugins.microbot.autobankstander.skills.crafting;

public enum CraftingMethod {
    LEATHER("Leather"),
    GEMS("Cut gems"),
    JEWELLERY("String jewellery"),
    GLASSBLOWING("Glassblowing"),
    POTTERY("Pottery"),
    BATTLESTAVES("Battlestaves");

    private final String displayName;

    CraftingMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
