package net.runelite.client.plugins.microbot.delveprayerhelper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperProjectile;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.ENGIN + "Delve Prayer Helper",
	description = "Automatically prays against the Delve boss attacks",
	tags = {"delve", "doom of mokhaiotl", "mokha", "pvm", "boss"},
	authors = { "Engin" },
	version = DelvePrayerHelperPlugin.version,
	minClientVersion = "2.0.0",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    cardUrl = "https://chsami.github.io/Microbot-Hub/DelvePrayerHelperPlugin/assets/card.png",
    iconUrl = "https://chsami.github.io/Microbot-Hub/DelvePrayerHelperPlugin/assets/icon.png",
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DelvePrayerHelperPlugin extends Plugin {

	static final String version = "1.3.6";

    @Inject
    private DelvePrayerHelperConfig config;
    @Provides
    DelvePrayerHelperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DelvePrayerHelperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DelvePrayerHelperOverlay delvePrayerHelperOverlay;

    @Inject
    private DelvePrayerHelperScript delvePrayerHelperScript;

    @Inject
    private DelveFightLogger fightLogger;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(delvePrayerHelperOverlay);
        }
        fightLogger.start();
        delvePrayerHelperScript.run();
    }

    protected void shutDown() {
        delvePrayerHelperScript.shutdown();
        fightLogger.stop();
        overlayManager.remove(delvePrayerHelperOverlay);
    }

    @Subscribe
    public void onClientTick(ClientTick e)
    {
        // Runs on the client thread every ~20ms. Everything the fast prayer loop needs is cached
        // here, so the loop never has to hop onto the client thread (a hop can take a whole tick).
        delvePrayerHelperScript.onClientTick();
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e)
    {
        Projectile projectile = e.getProjectile();

        // Log every projectile, including ids the helper does not map, so unknown attacks in deeper
        // delves can be identified from the fight log.
        fightLogger.logProjectile(projectile, DelvePrayerHelperProjectile.protectionFor(projectile.getId()));

        if (DelvePrayerHelperProjectile.isAttackProjectile(projectile.getId()))
        {
            delvePrayerHelperScript.trackProjectile(projectile);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned e)
    {
        fightLogger.logNpc(e.getNpc(), true);
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e)
    {
        fightLogger.logNpc(e.getNpc(), false);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e)
    {
        fightLogger.logAnimation(e.getActor());
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied e)
    {
        fightLogger.logHitsplat(e);
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated e)
    {
        fightLogger.logGraphicsObject(e);
    }

    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        fightLogger.logChat(e);
    }
}
