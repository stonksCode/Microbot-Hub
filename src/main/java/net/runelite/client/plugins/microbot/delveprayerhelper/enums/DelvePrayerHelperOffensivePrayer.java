package net.runelite.client.plugins.microbot.delveprayerhelper.enums;

/**
 * Offensive (ranged) prayer choices for the config dropdown.
 * {@link #toString()} is what the dropdown shows.
 */
public enum DelvePrayerHelperOffensivePrayer {
    BEST_AVAILABLE("Best available"),
    RIGOUR("Rigour"),
    DEADEYE("Deadeye"),
    EAGLE_EYE("Eagle Eye"),
    HAWK_EYE("Hawk Eye"),
    SHARP_EYE("Sharp Eye");

    private final String displayName;

    DelvePrayerHelperOffensivePrayer(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
