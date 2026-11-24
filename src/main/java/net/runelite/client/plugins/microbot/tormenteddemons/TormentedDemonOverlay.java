package net.runelite.client.plugins.microbot.tormenteddemons;


import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TormentedDemonOverlay extends OverlayPanel {

    private static final Color TITLE_COLOR = new Color(0xFF4500);
    private static final Color LABEL_COLOR = new Color(0xB8B8B8);
    private static final Color VALUE_COLOR = Color.WHITE;
    private static final Color STATUS_COLOR = new Color(0x66FF66);
    private static final Color LOOT_COLOR = new Color(0xFFD966);
    private static final String TITLE = "FYRE Tormented Demons";
    // Minimum panel width. The real width is measured from the title each frame so the centred
    // title can never reach past the panel background.
    private static final int MIN_PANEL_WIDTH = 180;

    private final TormentedDemonConfig config;

    @Inject
    TormentedDemonOverlay(TormentedDemonPlugin plugin, TormentedDemonConfig config) {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }


    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            // The panel width is measured from the title so the centred title always fits inside
            // the background, regardless of the client's font or the version string length.
            int width = Math.max(MIN_PANEL_WIDTH, graphics.getFontMetrics().stringWidth(TITLE) + 12);
            panelComponent.setPreferredSize(new Dimension(width, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text(TITLE)
                    .color(TITLE_COLOR)
                    .build());
            panelComponent.getChildren().add(row("Version", TormentedDemonPlugin.version, Color.GRAY));

            List<LineComponent> stats = buildStatRows();
            if (!stats.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder().build());
                panelComponent.getChildren().addAll(stats);
            }

        } catch (Exception ex) {
            log.debug("Unable to render Tormented Demon overlay", ex);
        }
        return super.render(graphics);
    }

    /**
     * Builds the stat rows that are enabled in the Overlay config section. Returning the rows in
     * a list keeps the separator from being drawn when every stat is hidden.
     */
    private List<LineComponent> buildStatRows() {
        List<LineComponent> rows = new ArrayList<>();
        if (config.showStatus()) {
            rows.add(row("Status", TormentedDemonScript.getStatusDisplay(), STATUS_COLOR));
        }
        if (config.showKillCount()) {
            rows.add(row("Kill count", String.valueOf(TormentedDemonScript.killCount), VALUE_COLOR));
        }
        if (config.showAverageKillTime()) {
            rows.add(row("Avg kill", formatDuration(TormentedDemonScript.getAverageKillTimeMs()), VALUE_COLOR));
        }
        if (config.showLastKillTime()) {
            rows.add(row("Last kill", formatDuration(TormentedDemonScript.lastKillTimeMs), VALUE_COLOR));
        }
        if (config.showTripLoot()) {
            rows.add(row("Loot (trip)", formatGp(TormentedDemonScript.getTripLootValue()), LOOT_COLOR));
        }
        if (config.showAverageLoot()) {
            rows.add(row("Avg loot / kill", formatGp(TormentedDemonScript.getAverageLootPerKill()), LOOT_COLOR));
        }
        return rows;
    }

    private LineComponent row(String label, String value, Color valueColor) {
        return LineComponent.builder()
                .left(label)
                .leftColor(LABEL_COLOR)
                .right(value)
                .rightColor(valueColor)
                .build();
    }

    /**
     * Formats a fight duration as {@code 42.3s} under a minute and {@code 2:05} above it.
     */
    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "-";
        }
        long seconds = millis / 1000L;
        if (seconds < 60) {
            return String.format("%.1fs", millis / 1000.0);
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private String formatGp(long value) {
        return value <= 0 ? "-" : QuantityFormatter.formatNumber(value);
    }
}
