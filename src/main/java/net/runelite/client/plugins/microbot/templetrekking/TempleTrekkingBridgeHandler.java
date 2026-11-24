package net.runelite.client.plugins.microbot.templetrekking;

/** Contract used by the main script to drive whichever bridge event is active. */
interface TempleTrekkingBridgeHandler {
    /** True only when this specific bridge variant is actually in the scene. */
    boolean isPresent();

    void enter();

    void step();

    void noteMessage(String message);

    void reset();

    boolean isSolved();

    String describe();
}
