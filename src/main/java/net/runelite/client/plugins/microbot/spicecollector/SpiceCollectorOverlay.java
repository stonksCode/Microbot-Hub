package net.runelite.client.plugins.microbot.spicecollector;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class SpiceCollectorOverlay extends OverlayPanel {

    private final SpiceCollectorScript script;

    @Inject
    SpiceCollectorOverlay(SpiceCollectorPlugin plugin, SpiceCollectorScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(230, 165));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Spice Collector " + SpiceCollectorPlugin.version)
                    .color(Color.CYAN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState().toString())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Spice:")
                    .right(script.getSpiceType().getItemName())
                    .build());

            int target = script.getTargetSpices();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Collected:")
                    .right(script.getCollectedSpices() + " / " + (target > 0 ? target : "\u221E"))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Fights:")
                    .right(String.valueOf(script.getFightsCompleted()))
                    .build());

            if (!script.getCatName().isEmpty()) {
                String health = script.getCatHealthPercent() < 0
                        ? "?"
                        : script.getCatHealthPercent() + "%";
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(script.getCatName() + ":")
                        .right(health)
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Cat food:")
                    .right(String.valueOf(script.getFoodCount()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status == null ? "" : Microbot.status)
                    .build());

            if (!script.getStopReason().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stopped:")
                        .right(script.getStopReason())
                        .build());
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
