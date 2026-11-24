package net.runelite.client.plugins.microbot.delveauto;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
	name = PluginConstants.ENGIN + "Delve Auto",
	description = "Full automation for the Doom of Mokhaiotl (Delve), starting with levels 1-3",
	tags = {"delve", "doom of mokhaiotl", "mokha", "pvm", "boss", "auto"},
	authors = {"Engin"},
	version = DelveAutoPlugin.version,
	minClientVersion = "2.0.0",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	cardUrl = "https://chsami.github.io/Microbot-Hub/DelveAutoPlugin/assets/card.png",
	iconUrl = "https://chsami.github.io/Microbot-Hub/DelveAutoPlugin/assets/icon.png",
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DelveAutoPlugin extends Plugin
{
	static final String version = "1.3.0";

	@Inject
	private DelveAutoConfig config;

	@Provides
	DelveAutoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DelveAutoConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DelveAutoOverlay overlay;

	@Inject
	private DelveAutoScript script;

	@Inject
	private DelvePrayerService prayerService;

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(overlay);
		}
		script.run();
	}

	@Override
	protected void shutDown()
	{
		script.shutdown();
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		prayerService.onProjectileMoved(event.getProjectile());
	}
}
