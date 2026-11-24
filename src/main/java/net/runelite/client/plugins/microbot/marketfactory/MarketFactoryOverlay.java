package net.runelite.client.plugins.microbot.marketfactory;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Lightweight overlay showing scan freshness, capital and the plugin's current top picks.
 */
public class MarketFactoryOverlay extends OverlayPanel
{
	private final MarketFactoryPlugin plugin;

	@Inject
	public MarketFactoryOverlay(MarketFactoryPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setPreferredSize(new Dimension(290, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		MarketFactoryScript script = plugin.getScript();
		if (script == null || !plugin.getConfig().showOverlay())
		{
			return null;
		}

		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Market Factory")
			.color(Color.CYAN)
			.build());

		if (!script.isRunning())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status")
				.right("Stopped")
				.build());
			return super.render(graphics);
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status")
			.right(script.getStatus())
			.build());

		CapitalSnapshot capital = script.getLastCapital();
		if (capital != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Capital")
				.right(formatGp(capital.getTotalCoins()))
				.build());
		}

		net.runelite.client.plugins.microbot.marketfactory.trade.TradeManager trades = script.getTradeManager();
		if (trades != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(trades.isPaper() ? "Mode (paper)" : "Mode (LIVE)")
				.right(trades.activeCount() + " open / " + trades.getCompletedCount() + " done")
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Realised")
				.right(MarketFactoryScript.formatGp(trades.getRealizedProfit()) + " gp")
				.build());
			for (net.runelite.client.plugins.microbot.marketfactory.trade.ActiveTrade trade : trades.activeTrades())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(truncate(trade.getItemName(), 16))
					.right(truncate(trade.getStatus(), 28))
					.build());
			}
		}

		ScanReport report = script.getLastReport();
		if (report != null)
		{
			if (!report.getProcesses().isEmpty())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Processes")
					.right(String.valueOf(report.getProcesses().size()))
					.build());

				int procCount = Math.min(3, report.getProcesses().size());
				for (int i = 0; i < procCount; i++)
				{
					Candidate c = report.getProcesses().get(i);
					panelComponent.getChildren().add(LineComponent.builder()
						.left((i + 1) + ") " + truncate(c.getLabel(), 16))
						.right(formatGp(c.getProfit()) + " gp (" + formatGp((long) c.getGpPerHour()) + "/h)")
						.build());
				}
			}

			if (!report.getFlips().isEmpty())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Flips")
					.right(String.valueOf(report.getFlips().size()))
					.build());

				int flipCount = Math.min(3, report.getFlips().size());
				for (int i = 0; i < flipCount; i++)
				{
					Candidate c = report.getFlips().get(i);
					panelComponent.getChildren().add(LineComponent.builder()
						.left((i + 1) + ") " + truncate(c.getLabel(), 16))
						.right(formatGp(c.getProfit()) + " gp (" + formatGp((long) c.getGpPerHour()) + "/h)")
						.build());
				}
			}

			if (report.getGeneratedAt() > 0)
			{
				long secondsAgo = (System.currentTimeMillis() - report.getGeneratedAt()) / 1000L;
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Age")
					.right(secondsAgo + "s")
					.build());
			}
		}

		return super.render(graphics);
	}

	private static String truncate(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		if (value.length() <= max)
		{
			return value;
		}
		return value.substring(0, max - 3) + "...";
	}

	private static String formatGp(long gp)
	{
		if (gp >= 1_000_000_000L)
		{
			return String.format("%.1fB", gp / 1_000_000_000D);
		}
		if (gp >= 1_000_000L)
		{
			return String.format("%.1fM", gp / 1_000_000D);
		}
		if (gp >= 1000L)
		{
			return String.format("%.1fK", gp / 1000D);
		}
		return String.valueOf(gp);
	}
}
