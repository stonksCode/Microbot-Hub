package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

public class KittenScript extends Script {

    /**
     * Feeding and play are handled by {@link FeedKittenEvent} and {@link KittenAttentionEvent}
     * (registered by the plugin), so this loop only keeps the script lifecycle alive.
     */
    public boolean run(KittenConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                super.run();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
