package net.runelite.client.plugins.microbot.marketfactory.trade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.marketfactory.trade.ActiveTrade.Phase;

/**
 * Bookkeeping for trades: what is open, what finished, realised profit, and a CSV journal
 * that records predicted against realised numbers so the risk presets can be calibrated.
 *
 * <p>Thread-safe: the trade thread mutates it and the overlay reads it.
 */
@Slf4j
public final class TradeManager
{
	private static final int MAX_FINISHED = 50;
	private static final String JOURNAL_HEADER = "timestamp,mode,item,itemId,qtyPlanned,bought,sold,"
		+ "plannedBuy,plannedSell,avgBuy,avgSellNet,predictedProfit,realizedProfit,minutes,repriced,outcome";

	private final Map<Integer, ActiveTrade> active = new LinkedHashMap<>();
	private final Deque<ActiveTrade> finished = new ArrayDeque<>();
	private final boolean paper;
	private final Path journal;
	private long realizedProfit;
	private int completedCount;
	private int failedCount;

	/** @param journal CSV file to append finished trades to, or null for none */
	public TradeManager(boolean paper, Path journal)
	{
		this.paper = paper;
		this.journal = journal;
	}

	public boolean isPaper()
	{
		return paper;
	}

	synchronized void add(ActiveTrade trade)
	{
		active.put(trade.getItemId(), trade);
	}

	public synchronized boolean isActive(int itemId)
	{
		return active.containsKey(itemId);
	}

	public synchronized int activeCount()
	{
		return active.size();
	}

	public synchronized List<ActiveTrade> activeTrades()
	{
		return new ArrayList<>(active.values());
	}

	public synchronized List<ActiveTrade> recentFinished()
	{
		return new ArrayList<>(finished);
	}

	public synchronized long getRealizedProfit()
	{
		return realizedProfit;
	}

	public synchronized int getCompletedCount()
	{
		return completedCount;
	}

	public synchronized int getFailedCount()
	{
		return failedCount;
	}

	synchronized void finish(ActiveTrade trade, boolean success, String outcome, long now)
	{
		long costOfSold = trade.bought > 0
			? Math.round((double) trade.spent * trade.sold / trade.bought)
			: 0L;
		long profit = trade.revenue - costOfSold;

		trade.realizedProfit = profit;
		trade.outcome = outcome == null ? "" : outcome;
		trade.finishedAt = now;
		trade.phase = success ? Phase.DONE : Phase.FAILED;
		trade.status = trade.outcome;

		realizedProfit += profit;
		if (success)
		{
			completedCount++;
		}
		else
		{
			failedCount++;
		}

		active.remove(trade.getItemId());
		finished.addFirst(trade);
		while (finished.size() > MAX_FINISHED)
		{
			finished.removeLast();
		}
		writeJournal(trade, now);
	}

	private void writeJournal(ActiveTrade trade, long now)
	{
		if (journal == null)
		{
			return;
		}
		try
		{
			Path parent = journal.getParent();
			if (parent != null)
			{
				Files.createDirectories(parent);
			}
			StringBuilder out = new StringBuilder();
			if (!Files.exists(journal))
			{
				out.append(JOURNAL_HEADER).append('\n');
			}
			long avgSellNet = trade.sold > 0 ? Math.round((double) trade.revenue / trade.sold) : 0L;
			long minutes = Math.max(0L, (now - trade.getStartedAt()) / 60_000L);
			out.append(String.format(Locale.ROOT, "%d,%s,\"%s\",%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,\"%s\"%n",
				now,
				paper ? "PAPER" : "LIVE",
				trade.getItemName().replace("\"", "\"\""),
				trade.getItemId(),
				trade.plannedQuantity,
				trade.bought,
				trade.sold,
				trade.plannedBuyPrice,
				trade.plannedSellPrice,
				trade.averageBuyPrice(),
				avgSellNet,
				trade.predictedProfit,
				trade.realizedProfit,
				minutes,
				trade.repriceCount,
				trade.outcome.replace("\"", "'")));
			Files.write(journal, out.toString().getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException | RuntimeException ex)
		{
			log.debug("Could not write trade journal", ex);
		}
	}
}
