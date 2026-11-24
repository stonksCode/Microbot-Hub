package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

public class FeedKittenEvent implements BlockingEvent {
    private static final int ACTION_TIMEOUT_MS = 10000;

    private final KittenPlugin kittenPlugin;
    private final KittenConfig config;

    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin, KittenConfig config) {
        this.kittenPlugin = kittenPlugin;
        this.config = config;
    }

    @Override
    public boolean validate() {
        return config.autoFeed()
                && kittenPlugin.playerHasFollower()
                && kittenPlugin.isKitten()
                && getFoodItemId() != -1
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry();
    }

    @Override
    public boolean execute() {
        final int foodId = getFoodItemId();
        if (foodId == -1) {
            return false;
        }

        AtomicBoolean used = new AtomicBoolean(false);
        Microbot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream()
                .findFirst()
                .ifPresent(kitten -> used.set(Rs2Inventory.useItemOnNpc(foodId, kitten.getNpc())));

        if (!used.get()) {
            Microbot.log("Kitten Tracker: could not feed the kitten (no kitten in range or item use failed).");
            return false;
        }

        return Global.sleepUntil(
                () -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry(),
                ACTION_TIMEOUT_MS);
    }

    /**
     * Uses the configured food, or the first available valid food when set to "Any available food".
     */
    private int getFoodItemId() {
        KittenFood selected = config.kittenFood();
        if (selected == KittenFood.ANY) {
            return KittenFood.findAvailableFood();
        }
        return Rs2Inventory.hasItem(selected.getItemId()) ? selected.getItemId() : -1;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
