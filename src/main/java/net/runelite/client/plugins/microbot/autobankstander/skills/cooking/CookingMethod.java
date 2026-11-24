package net.runelite.client.plugins.microbot.autobankstander.skills.cooking;

public enum CookingMethod {
    RAW_FOOD("Raw food"),
    PIES("Pies"),
    PIZZAS("Pizzas"),
    CAKES("Cakes"),
    BREAD("Bread"),
    WINES("Wines"),
    KARAMBWAN("Karambwans");

    private final String displayName;

    CookingMethod(String displayName) {
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
