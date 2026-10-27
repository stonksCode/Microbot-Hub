package net.runelite.client.plugins.microbot.gauntlethelper;

import com.google.inject.Provides;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Set;

import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = "Gauntlet Helper",
        description = "Gauntlet Helper",
        tags = {"Jam", "cg", "Gauntlet", "hunllef", "pvm", "prayer", "money making", "auto", "boss"},
        version = GauntletHelperPlugin.version,
        minClientVersion = "2.0.30",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class GauntletHelperPlugin extends Plugin {
    public static final String version = "1.3";

    private static final int RANGE_PROJECTILE_MINIBOSS = 1705;
    private static final int MAGE_PROJECTILE_MINIBOSS = 1701;
    private static final int RANGE_PROJECTILE = 1711;
    private static final int MAGE_PROJECTILE = 1707;
    private static final int CG_RANGE_PROJECTILE = 1712;
    private static final int CG_MAGE_PROJECTILE = 1708;
    private static final int MAGE_ANIMATION = 8754;
    private static final int RANGE_ANIMATION = 8755;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GauntletHelperOverlay overlay;
    @Inject
    private GauntletHelperScript GH_script;

    @Provides
    GauntletHelperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GauntletHelperConfig.class);
    }

    @Override
    protected void startUp() {
        log("Gauntlet Helper plugin started!");
        if (GH_script != null) GH_script.run();
        if (overlayManager != null) overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        log("Gauntlet Helper plugin stopped!");
        try {
            if (GH_script != null) GH_script.shutdown();
        } finally {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (GH_script == null) return;

        int projectileId = event.getProjectile().getId();
        switch (projectileId) {
            case MAGE_PROJECTILE:
            case CG_MAGE_PROJECTILE:
            case MAGE_PROJECTILE_MINIBOSS:
                GH_script.onEnemyMagic();
                break;

            case RANGE_PROJECTILE:
            case CG_RANGE_PROJECTILE:
            case RANGE_PROJECTILE_MINIBOSS:
                GH_script.onEnemyRange();
                break;

            default:
                break;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (GH_script == null) return;
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (!HUNLLEF_IDS.contains(npc.getId())) return;

        switch (npc.getAnimation()) {
            case MAGE_ANIMATION:
                GH_script.onEnemyMagic();
                break;
            case RANGE_ANIMATION:
                GH_script.onEnemyRange();
                break;
            default:
                break;
        }
    }
}
