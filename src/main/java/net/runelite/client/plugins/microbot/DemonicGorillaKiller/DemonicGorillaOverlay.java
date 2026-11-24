package net.runelite.client.plugins.microbot.DemonicGorillaKiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.awt.*;

public class DemonicGorillaOverlay extends OverlayPanel {

    private final DemonicGorillaPlugin plugin;

    @Inject
    DemonicGorillaOverlay(DemonicGorillaPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Demonic Gorilla - v" + DemonicGorillaPlugin.version)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            addRow("Running: " + plugin.getTimeRunning());
            addRow("Script status: " + DemonicGorillaScript.BOT_STATUS);
            addRow("Microbot status: " + Microbot.status);
            addRow("Kill count: " + DemonicGorillaScript.killCount);
            addRow("Current trip kill count: " + DemonicGorillaScript.currentTripKillCount);
            addRow("Total loot: " + QuantityFormatter.formatNumber(DemonicGorillaScript.totalLootValue));
            addRow("Current defensive prayer: " + DemonicGorillaScript.currentDefensivePrayer);
            addRow("Current gear: " + DemonicGorillaScript.currentGear);
        } catch (Exception ex) {
            System.out.println("Demonic Gorilla overlay: " + ex.getMessage());
        }
        return super.render(graphics);
    }

    private void addRow(String text) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(text)
                .leftColor(Color.WHITE)
                .build());
    }
}
