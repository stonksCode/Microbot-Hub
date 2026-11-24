package net.runelite.client.plugins.microbot.templetrekking;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class TempleTrekkingOverlay extends OverlayPanel {
    private final TempleTrekkingPlugin plugin;

    @Inject
    TempleTrekkingOverlay(TempleTrekkingPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            TempleTrekkingScript.OverlaySnapshot snapshot = plugin.getOverlaySnapshot();
            panelComponent.setPreferredSize(new Dimension(240, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Temple Trekking")
                    .color(java.awt.Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(snapshot.state)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Side:")
                    .right(snapshot.side)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Event:")
                    .right(snapshot.event)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Treks:")
                    .right(snapshot.maxTreks > 0
                            ? snapshot.treksCompleted + " / " + snapshot.maxTreks
                            : String.valueOf(snapshot.treksCompleted))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Events handled:")
                    .right(String.valueOf(snapshot.eventsHandled))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Reward tokens:")
                    .right(snapshot.rewardTarget > 0
                            ? snapshot.rewardTokens + " / " + snapshot.rewardTarget
                            : String.valueOf(snapshot.rewardTokens))
                    .build());
            if (snapshot.stalled) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("NO PROGRESS")
                        .rightColor(java.awt.Color.RED)
                        .build());
            }
            return super.render(graphics);
        } catch (Exception ex) {
            return null;
        }
    }
}
