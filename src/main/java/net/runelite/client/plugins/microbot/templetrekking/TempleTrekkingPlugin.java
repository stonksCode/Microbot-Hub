package net.runelite.client.plugins.microbot.templetrekking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = "<html>[<font color=#FF4500>FYRE</font>] " + "Temple Trekking",
        description = "Automates the easy Temple Trekking route, evading combat and solving events.",
        tags = {"temple trekking", "templetrekking", "minigame", "morytania", "fyre"},
        authors = {"Microbot"},
        version = TempleTrekkingPlugin.version,
        minClientVersion = "2.6.15",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TempleTrekkingPlugin extends Plugin {
    public static final String version = "1.1.4";

    @Inject
    private TempleTrekkingScript templeTrekkingScript;

    @Inject
    private TempleTrekkingConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TempleTrekkingOverlay templeTrekkingOverlay;

    @Provides
    TempleTrekkingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TempleTrekkingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(templeTrekkingOverlay);
        templeTrekkingScript.run(config);
    }

    @Override
    protected void shutDown() {
        templeTrekkingScript.shutdown();
        overlayManager.remove(templeTrekkingOverlay);
    }

    String getRuntimeStatus() {
        return templeTrekkingScript.getRuntimeStatus();
    }

    TempleTrekkingScript.OverlaySnapshot getOverlaySnapshot() {
        return templeTrekkingScript.getOverlaySnapshot();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        ChatMessageType type = chatMessage.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM) {
            return;
        }
        String message = Text.removeTags(chatMessage.getMessage());
        if (message == null || message.isEmpty()) {
            return;
        }
        templeTrekkingScript.noteMessage(message);
    }
}
