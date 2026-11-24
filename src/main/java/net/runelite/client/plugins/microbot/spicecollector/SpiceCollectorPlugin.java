package net.runelite.client.plugins.microbot.spicecollector;

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
        name = "<html>[<font color=#FF4500>FYRE</font>] " + "Spice Collector",
        description = "Collects 4-dose spices by sending your cat into the Hell-Rat Behemoth alcoves in Evil Dave's Basement of Doom.",
        tags = {"spice", "hell-rat", "behemoth", "cat", "kitten", "evil dave", "stew", "skilling"},
        authors = {"Microbot"},
        version = SpiceCollectorPlugin.version,
        minClientVersion = "2.6.57",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SpiceCollectorPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private SpiceCollectorConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SpiceCollectorOverlay overlay;

    @Inject
    private SpiceCollectorScript script;

    @Provides
    SpiceCollectorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SpiceCollectorConfig.class);
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
        SpiceCollectorDebug.close();
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String message = event.getMessage();
        if (message == null) return;
        SpiceCollectorDebug.trace("chat GAMEMESSAGE: %s", message);
        String lower = message.toLowerCase();

        if (lower.contains("your cat") && (
                lower.contains("passed away")
                        || lower.contains("has died")
                        || lower.contains("was killed")
                        || lower.contains("run away")
                        || lower.contains("run off"))) {
            script.requestStop("Your cat is gone.");
            return;
        }

        if (lower.contains("isn't sufficiently interested in killing rats")
                || lower.contains("is not sufficiently interested in killing rats")) {
            script.requestStop("Your follower is not a cat.");
            return;
        }

        if (lower.contains("you don't have a cat")
                || lower.contains("you do not have a cat")
                || lower.contains("you need a cat")) {
            script.requestStop("No cat is available.");
        }
    }
}
