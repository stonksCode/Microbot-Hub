package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.marketfactory.enums.RejectReason;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;
import net.runelite.client.plugins.microbot.marketfactory.model.ItemMeta;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;

/**
 * Pure market math. No game state, no client calls, no I/O: given prices, history and a
 * risk profile it produces a {@link MarketQuote}. That makes the whole model replayable
 * and unit-testable offline.
 *
 * <p>Conservative pricing uses a high percentile of recent low prints for the buy side
 * and a low percentile of recent high prints for the sell side, so a single stale or
 * manipulated print cannot invent a margin.
 */
public final class MarketAnalyzer
{
	private static final int MIN_HISTORY_FOR_STATS = 8;
	private static final int PERCENTILE_WINDOW = 72;
	private static final int TREND_WINDOW = 168;
	private static final int VOLUME_WINDOW = 24;

	private static final double BUY_PERCENTILE = 0.75;
	private static final double SELL_PERCENTILE = 0.25;

	private static final int TAX_RATE_PERCENT = 2;
	private static final int TAX_CAP_PER_ITEM = 5_000_000;
	private static final int TAX_FREE_THRESHOLD = 50;

	/**
	 * @param enforceHistory when false, a short history lowers confidence but does not
	 *                       reject the item. The engine uses this for its cheap first pass
	 *                       and then re-analyses shortlisted items with history enforced.
	 */
	public MarketQuote analyze(ItemMeta meta, PricePoint latest, HourlyPoint hourly,
		List<HourlyPoint> history, RiskProfile risk, long nowMillis, boolean enforceHistory)
	{
		int itemId = meta == null ? 0 : meta.getId();
		String itemName = meta == null ? "" : meta.getName();
		MarketQuote.Builder quote = MarketQuote.builder(itemId, itemName);

		if (latest == null)
		{
			return quote.addReject(RejectReason.NO_PRICE, "no latest print").build();
		}
		if (!latest.isComplete())
		{
			return quote.addReject(RejectReason.NO_PRICE, "missing one side of the spread").build();
		}

		long nowSeconds = nowMillis / 1000L;
		long oldestPrint = latest.oldestPrintTime();
		long ageMinutes = oldestPrint > 0 ? Math.max(0L, (nowSeconds - oldestPrint) / 60L) : 99_999L;
		if (ageMinutes > risk.getMaxPrintAgeMinutes())
		{
			quote.addReject(RejectReason.STALE_PRINT,
				ageMinutes + "m old, limit " + risk.getMaxPrintAgeMinutes() + "m");
		}

		List<HourlyPoint> usable = completeHistory(history);
		int historyPoints = usable.size();
		int window = Math.min(PERCENTILE_WINDOW, historyPoints);

		double buyReference;
		double sellReference;
		double volatility = -1D;
		double trendPercentPerHour = 0D;
		double spreadRatio = 1D;

		if (historyPoints >= MIN_HISTORY_FOR_STATS)
		{
			double[] lows = new double[window];
			double[] highs = new double[window];
			double[] mids = new double[window];
			double[] spreads = new double[window];
			int offset = historyPoints - window;
			for (int i = 0; i < window; i++)
			{
				HourlyPoint point = usable.get(offset + i);
				lows[i] = point.getAvgLowPrice();
				highs[i] = point.getAvgHighPrice();
				mids[i] = point.midPrice();
				spreads[i] = mids[i] > 0 ? (point.getAvgHighPrice() - point.getAvgLowPrice()) / mids[i] : 0D;
			}

			buyReference = percentile(lows, BUY_PERCENTILE);
			sellReference = percentile(highs, SELL_PERCENTILE);
			volatility = coefficientOfVariation(mids);
			trendPercentPerHour = trendPercentPerHour(usable);
			double medianSpread = percentile(spreads, 0.5);
			double currentSpread = (latest.getHigh() - latest.getLow()) / ((latest.getHigh() + latest.getLow()) / 2D);
			spreadRatio = medianSpread > 0 ? currentSpread / medianSpread : 1D;
		}
		else
		{
			buyReference = latest.getLow();
			sellReference = latest.getHigh();
		}

		double buffer = risk.getPriceBuffer();
		int buyPrice = (int) Math.ceil(buyReference * (1D + buffer));
		int sellPrice = (int) Math.floor(sellReference * (1D - buffer));
		if (buyPrice <= 0)
		{
			buyPrice = (int) Math.ceil(buyReference);
		}
		if (sellPrice <= 0)
		{
			sellPrice = (int) Math.floor(sellReference);
		}

		int tax = tax(sellPrice);
		int netMargin = sellPrice - buyPrice - tax;
		double roiPercent = buyPrice > 0 ? (netMargin * 100D) / buyPrice : 0D;

		double buyVolume = averageVolume(usable, VOLUME_WINDOW, true);
		double sellVolume = averageVolume(usable, VOLUME_WINDOW, false);
		if (buyVolume <= 0 && hourly != null)
		{
			buyVolume = hourly.getLowPriceVolume();
		}
		if (sellVolume <= 0 && hourly != null)
		{
			sellVolume = hourly.getHighPriceVolume();
		}

		if (buyVolume <= 0 || sellVolume <= 0)
		{
			quote.addReject(RejectReason.NO_VOLUME, "no trades in the last " + VOLUME_WINDOW + "h");
		}

		double bottleneckVolume = Math.min(buyVolume, sellVolume);
		if (bottleneckVolume > 0 && bottleneckVolume < risk.getMinHourlyVolume())
		{
			quote.addReject(RejectReason.LOW_VOLUME,
				String.format("%.0f/h, minimum %.0f/h", bottleneckVolume, risk.getMinHourlyVolume()));
		}

		if (netMargin < risk.getMinProfitPerUnit())
		{
			quote.addReject(RejectReason.LOW_MARGIN,
				netMargin + " gp/unit, minimum " + (long) risk.getMinProfitPerUnit());
		}
		if (roiPercent < risk.getMinRoiPercent())
		{
			quote.addReject(RejectReason.LOW_ROI,
				String.format("%.2f%%, minimum %.2f%%", roiPercent, risk.getMinRoiPercent()));
		}
		if (volatility >= 0 && volatility > risk.getMaxVolatilityCv())
		{
			quote.addReject(RejectReason.HIGH_VOLATILITY,
				String.format("CV %.3f, limit %.3f", volatility, risk.getMaxVolatilityCv()));
		}
		if (spreadRatio > risk.getMaxSpreadRatio())
		{
			quote.addReject(RejectReason.UNSTABLE_SPREAD,
				String.format("%.2fx median spread", spreadRatio));
		}
		if (trendPercentPerHour < -risk.getMaxDowntrendPercentPerHour())
		{
			quote.addReject(RejectReason.DOWNTREND,
				String.format("%.2f%%/h", trendPercentPerHour));
		}
		if (enforceHistory && historyPoints < risk.getMinHistoryPoints())
		{
			quote.addReject(RejectReason.INSUFFICIENT_HISTORY,
				historyPoints + " points, need " + risk.getMinHistoryPoints());
		}

		double participation = risk.getParticipationCap();
		double effectiveUnitsPerHour = Math.max(0D, bottleneckVolume * participation);
		double gpPerHour = Math.max(0, netMargin) * effectiveUnitsPerHour;

		double cvLimit = risk.getMaxVolatilityCv();
		double cvFactor = volatility >= 0 ? clamp(1D - volatility / cvLimit, 0D, 1D) : 0.5D;
		double freshnessFactor = clamp(1D - ageMinutes / (double) risk.getMaxPrintAgeMinutes(), 0D, 1D);
		double historyFactor = clamp(historyPoints / 48D, 0D, 1D);
		double trendFactor = clamp(1D + trendPercentPerHour / 10D, 0.5D, 1D);
		double stability = cvFactor * trendFactor * (0.5D + 0.5D * freshnessFactor);
		double confidence = clamp(0.5D * cvFactor + 0.2D * freshnessFactor + 0.3D * historyFactor, 0D, 1D);
		double score = gpPerHour * stability;

		return quote
			.prices(buyPrice, sellPrice, tax, netMargin, roiPercent)
			.volumes(buyVolume, sellVolume, effectiveUnitsPerHour, gpPerHour)
			.risk(volatility, trendPercentPerHour, spreadRatio, ageMinutes, historyPoints,
				confidence, stability, score)
			.build();
	}

	/** OSRS Grand Exchange sale tax: 2% rounded down, 5M cap, items under 50 gp exempt. */
	public static int tax(int sellPrice)
	{
		if (sellPrice < TAX_FREE_THRESHOLD)
		{
			return 0;
		}
		long tax = (sellPrice * (long) TAX_RATE_PERCENT) / 100L;
		return (int) Math.min(TAX_CAP_PER_ITEM, tax);
	}

	public static double percentile(double[] values, double percentile)
	{
		if (values == null || values.length == 0)
		{
			return 0D;
		}
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		double rank = clamp(percentile, 0D, 1D) * (sorted.length - 1);
		int lower = (int) Math.floor(rank);
		int upper = (int) Math.ceil(rank);
		if (lower == upper)
		{
			return sorted[lower];
		}
		double weight = rank - lower;
		return sorted[lower] * (1D - weight) + sorted[upper] * weight;
	}

	private static List<HourlyPoint> completeHistory(List<HourlyPoint> history)
	{
		List<HourlyPoint> usable = new ArrayList<>();
		if (history == null)
		{
			return usable;
		}
		for (HourlyPoint point : history)
		{
			if (point != null && point.isComplete())
			{
				usable.add(point);
			}
		}
		return usable;
	}

	private static double averageVolume(List<HourlyPoint> history, int hours, boolean lowSide)
	{
		if (history.isEmpty())
		{
			return 0D;
		}
		int start = Math.max(0, history.size() - hours);
		long total = 0L;
		for (int i = start; i < history.size(); i++)
		{
			HourlyPoint point = history.get(i);
			total += lowSide ? point.getLowPriceVolume() : point.getHighPriceVolume();
		}
		return total / (double) (history.size() - start);
	}

	private static double coefficientOfVariation(double[] values)
	{
		if (values.length < 2)
		{
			return 0D;
		}
		double mean = 0D;
		for (double value : values)
		{
			mean += value;
		}
		mean /= values.length;
		if (mean <= 0)
		{
			return 0D;
		}
		double variance = 0D;
		for (double value : values)
		{
			double diff = value - mean;
			variance += diff * diff;
		}
		variance /= values.length;
		return Math.sqrt(variance) / mean;
	}

	/** Least-squares slope of hourly mid prices, expressed as percent of the mean per hour. */
	private static double trendPercentPerHour(List<HourlyPoint> history)
	{
		int start = Math.max(0, history.size() - TREND_WINDOW);
		int count = history.size() - start;
		if (count < MIN_HISTORY_FOR_STATS)
		{
			return 0D;
		}

		double sumX = 0D;
		double sumY = 0D;
		for (int i = 0; i < count; i++)
		{
			sumX += i;
			sumY += history.get(start + i).midPrice();
		}
		double meanX = sumX / count;
		double meanY = sumY / count;

		double numerator = 0D;
		double denominator = 0D;
		for (int i = 0; i < count; i++)
		{
			double dx = i - meanX;
			numerator += dx * (history.get(start + i).midPrice() - meanY);
			denominator += dx * dx;
		}
		if (denominator <= 0 || meanY <= 0)
		{
			return 0D;
		}
		return (numerator / denominator) * 100D / meanY;
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}

	/** Convenience for overlays and the harness. */
	public static String describe(Map<RejectReason, String> rejects)
	{
		if (rejects == null || rejects.isEmpty())
		{
			return "ok";
		}
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<RejectReason, String> entry : rejects.entrySet())
		{
			if (builder.length() > 0)
			{
				builder.append("; ");
			}
			builder.append(entry.getKey().getLabel()).append(" (").append(entry.getValue()).append(')');
		}
		return builder.toString();
	}
}
