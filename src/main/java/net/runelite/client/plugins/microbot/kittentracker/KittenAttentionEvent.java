package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

public class KittenAttentionEvent implements BlockingEvent {
    private static final int ACTION_TIMEOUT_MS = 10000;

    private final KittenPlugin kittenPlugin;
    private final KittenConfig config;

    @Inject
    public KittenAttentionEvent(KittenPlugin kittenPlugin, KittenConfig config) {
        this.kittenPlugin = kittenPlugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        return config.autoPlay()
                && kittenPlugin.playerHasFollower()
                && kittenPlugin.isKitten()
                && (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeNeedingAttention();
    }

    @Override
    public boolean execute() {
        if (config.useBallOfWool() && Rs2Inventory.hasItem(ItemID.BALL_OF_WOOL)) {
            return playWithWool();
        }
        return strokeKitten();
    }

    /**
     * A ball of wool buys ~51 minutes of attention instead of the ~18-22 minutes from stroking.
     */
    private boolean playWithWool() {
        AtomicBoolean used = new AtomicBoolean(false);
        Microbot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream()
                .findFirst()
                .ifPresent(kitten -> used.set(Rs2Inventory.useItemOnNpc(ItemID.BALL_OF_WOOL, kitten.getNpc())));

        if (!used.get()) {
            Microbot.log("Kitten Tracker: could not use the ball of wool on the kitten.");
            return false;
        }

        return Global.sleepUntil(
                () -> (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeNeedingAttention(),
                ACTION_TIMEOUT_MS);
    }

    private boolean strokeKitten() {
        AtomicBoolean clicked = new AtomicBoolean(false);
        Microbot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream()
                .findFirst()
                .ifPresent(kitten -> clicked.set(kitten.click("Interact")));

        if (!clicked.get()) {
            Microbot.log("Kitten Tracker: no kitten in range to interact with.");
            return false;
        }
        if (!Rs2Dialogue.sleepUntilHasDialogueOption("Stroke")) {
            return false;
        }
        if (!Rs2Dialogue.clickOption("Stroke")) {
            return false;
        }

        return Global.sleepUntil(
                () -> (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeNeedingAttention(),
                ACTION_TIMEOUT_MS);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
