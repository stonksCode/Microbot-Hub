package net.runelite.client.plugins.microbot.lizardmanshaman;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
		name = PluginDescriptor.Default + "Lizardman Shaman",
		description = "Automates lizardman shamans in the Lizardman Temple, rotating between three safe spots to avoid jumps and purple spawns",
		tags = {"microbot", "lizardman", "shaman", "slayer", "kourend", "combat"},
		authors = {"Microbot"},
		version = LizardmanShamanPlugin.version,
		minClientVersion = "2.6.32",
		cardUrl = "",
		iconUrl = "",
		enabledByDefault = PluginConstants.DEFAULT_ENABLED,
		isExternal = PluginConstants.IS_EXTERNAL
)
public class LizardmanShamanPlugin extends Plugin
{
	public static final String version = "1.3.2";

	@Inject
	private LizardmanShamanConfig config;

	@Inject
	private LizardmanShamanScript script;

	@Inject
	private LizardmanShamanOverlay overlay;

	@Inject
	private LizardmanShamanSceneOverlay sceneOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Provides
	LizardmanShamanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LizardmanShamanConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (overlayManager != null)
		{
			overlayManager.add(overlay);
			overlayManager.add(sceneOverlay);
		}
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		script.shutdown();
		if (overlayManager != null)
		{
			overlayManager.remove(overlay);
			overlayManager.remove(sceneOverlay);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		if (config.stopAfterDeath() && event.getMessage().equalsIgnoreCase("oh dear, you are dead!"))
		{
			Rs2Walker.setTarget(null);
			shutDown();
		}
	}

	public LizardmanShamanScript getScript()
	{
		return script;
	}

	public LizardmanShamanConfig getConfig()
	{
		return config;
	}
}
