package net.runelite.client.plugins.microbot.autokraken;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class AutoKrakenOverlay extends OverlayPanel {

    private final AutoKrakenScript script;

    @Inject
    AutoKrakenOverlay(AutoKrakenPlugin plugin, AutoKrakenScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(220, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Auto Kraken " + AutoKrakenPlugin.version)
                    .color(Color.CYAN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState().name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(String.valueOf(script.getKillCount()))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Explosives:")
                    .right(String.valueOf(Rs2Inventory.count(ItemID.FISHING_EXPLOSIVE)))
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
