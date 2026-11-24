package net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums;

public enum FletchingMode {
    DARTS("Darts"),
    BOLTS("Bolts"),
    ARROWS("Arrows"),
    JAVELINS("Javelins"),
    ARROW_SHAFTS("Arrow shafts"),
    JAVELIN_SHAFTS("Javelin shafts"),
    BOWS("Bows"),
    CROSSBOWS("Crossbows"),
    SHIELDS("Shields");

    private final String displayName;

    FletchingMode(String displayName) {
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