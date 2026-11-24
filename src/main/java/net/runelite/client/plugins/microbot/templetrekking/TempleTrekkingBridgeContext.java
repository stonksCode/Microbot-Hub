package net.runelite.client.plugins.microbot.templetrekking;

/**
 * Small amount of shared plumbing handed to the two independent bridge handlers.
 *
 * This deliberately contains no bridge rules: each bridge variant owns its own object IDs,
 * materials and progression logic.
 */
interface TempleTrekkingBridgeContext {
    TempleTrekkingConfig config();

    boolean canAct();

    void markAction();

    void noteProgress();

    /** Clears follower chatter or confirms the "Attempt to fix the bridge?" prompt. */
    boolean clearDialogue();

    boolean interactObject(int[] ids, String action);

    void maybeEat();

    void warn(String message);
}
