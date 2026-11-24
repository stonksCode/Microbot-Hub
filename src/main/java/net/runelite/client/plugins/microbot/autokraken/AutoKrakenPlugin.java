package net.runelite.client.plugins.microbot.autokraken;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DEFAULT_PREFIX + "Auto Kraken",
        description = "AFK Kraken boss: opens with fishing explosives, fights, and loots while on task.",
        tags = {"kraken", "boss", "slayer", "fishing explosive", "pvm"},
        authors = {"Microbot"},
        version = AutoKrakenPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoKrakenPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private AutoKrakenConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoKrakenOverlay overlay;

    @Inject
    private AutoKrakenScript script;

    @Provides
    AutoKrakenConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoKrakenConfig.class);
    }

    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        String message = event.getMessage().toLowerCase();

        if (message.contains("oh dear, you are dead!")) {
            if (config.stopOnDeath()) {
                script.requestStop("You died.");
            }
            return;
        }

        if (message.contains("stick to your slayer")
                || message.contains("not on a slayer task")
                || message.contains("not currently assigned")) {
            script.requestStop("Off-task chat message: \"" + event.getMessage() + "\"");
            return;
        }

        if (message.contains("you've completed your task")
                || message.contains("you have completed your task")
                || message.contains("completed your slayer task")) {
            script.requestStop("Slayer task completed.");
        }
    }
}
