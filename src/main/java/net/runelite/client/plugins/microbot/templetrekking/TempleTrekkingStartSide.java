package net.runelite.client.plugins.microbot.templetrekking;

public enum TempleTrekkingStartSide {
    /**
     * Detect the current hub from the player's location and start there.
     */
    AUTO,
    /**
     * Only pick up a follower when standing at Paterdomus (the temple end).
     */
    PATERDOMUS,
    /**
     * Only pick up a follower when standing at Burgh de Rott.
     */
    BURGH_DE_ROTT
}
