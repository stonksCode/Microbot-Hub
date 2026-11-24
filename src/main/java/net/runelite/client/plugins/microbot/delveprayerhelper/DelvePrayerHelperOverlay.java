package net.runelite.client.plugins.microbot.delveprayerhelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class DelvePrayerHelperOverlay extends OverlayPanel {

    @Inject
    private DelvePrayerHelperScript delvePrayerHelperScript;

    @Inject
    DelvePrayerHelperOverlay(DelvePrayerHelperPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Engin's Delve Prayer Helper V" + DelvePrayerHelperPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current projectile: " + Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Offensive Prayer on: " + delvePrayerHelperScript.isOffensivePrayerOn())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer points: " + delvePrayerHelperScript.getPrayerPoints())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rock impact in: " + (delvePrayerHelperScript.isRockFuseActive()
                            ? delvePrayerHelperScript.getTicksUntilRockImpact() + "t"
                            : "-"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("On acid: " + delvePrayerHelperScript.isOnAcid())
                    .build());

            WorldPoint shield = delvePrayerHelperScript.getEarthenShieldTile();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Earth shield: " + (shield == null
                            ? "-"
                            : shield.getX() + "," + shield.getY()
                            + "  totems: " + delvePrayerHelperScript.getVolatileEarthCount()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Fight log: " + delvePrayerHelperScript.getFightLogName())
                    .build());


        } catch (Exception ex) {
            log.debug("Error rendering Delve Prayer Helper overlay", ex);
        }
        return super.render(graphics);
    }
}
