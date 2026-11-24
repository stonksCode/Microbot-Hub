package net.runelite.client.plugins.microbot.marketfactory.trade;

import net.runelite.client.plugins.microbot.marketfactory.Candidate;
import net.runelite.client.plugins.microbot.marketfactory.MarketAnalyzer;
import net.runelite.client.plugins.microbot.marketfactory.RiskProfile;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;

/**
 * Turns a scanned flip into the prices the offers will actually use.
 *
 * <p>The analyzer prices a flip from history percentiles, which is deliberately conservative
 * and slow to react. Right before entering, the freshest print is allowed to tighten both
 * sides: bid one gp above the last instant-sell print and ask one gp under the last
 * instant-buy print. The margin is then re-checked against the risk profile, so a flip that
 * only looked good against stale history is dropped here instead of being bought.
 */
public final class FlipPricing
{
	/** Upper bound on how far the ask may sit above the analyzer's sell price. */
	private static final double MAX_SELL_UPLIFT = 1.10D;

	private FlipPricing()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static final class Plan
	{
		private final int buyPrice;
		private final int sellPrice;
		private final int netMargin;
		private final double roiPercent;

		Plan(int buyPrice, int sellPrice, int netMargin, double roiPercent)
		{
			this.buyPrice = buyPrice;
			this.sellPrice = sellPrice;
			this.netMargin = netMargin;
			this.roiPercent = roiPercent;
		}

		public int getBuyPrice()
		{
			return buyPrice;
		}

		public int getSellPrice()
		{
			return sellPrice;
		}

		/** Profit per unit after the Grand Exchange tax. */
		public int getNetMargin()
		{
			return netMargin;
		}

		public double getRoiPercent()
		{
			return roiPercent;
		}
	}

	/**
	 * @return the entry/exit prices, or null when the margin no longer clears the risk profile
	 */
	public static Plan plan(Candidate candidate, PricePoint latest, RiskProfile risk, long nowMillis)
	{
		int buy = candidate.getBuyPrice();
		int sell = candidate.getSellPrice();
		if (buy <= 0 || sell <= 0)
		{
			return null;
		}

		if (latest != null && latest.isComplete() && isFresh(latest, risk, nowMillis))
		{
			buy = Math.min(buy, latest.getLow() + 1);
			int ceiling = (int) Math.floor(candidate.getSellPrice() * MAX_SELL_UPLIFT);
			sell = Math.min(latest.getHigh() - 1, ceiling);
		}

		if (buy <= 0 || sell <= buy)
		{
			return null;
		}

		int net = sell - buy - MarketAnalyzer.tax(sell);
		double roi = net * 100D / buy;
		if (net < risk.getMinProfitPerUnit() || roi < risk.getMinRoiPercent())
		{
			return null;
		}
		return new Plan(buy, sell, net, roi);
	}

	private static boolean isFresh(PricePoint latest, RiskProfile risk, long nowMillis)
	{
		long oldest = latest.oldestPrintTime();
		if (oldest <= 0)
		{
			return false;
		}
		long ageMinutes = Math.max(0L, (nowMillis / 1000L - oldest) / 60L);
		return ageMinutes <= risk.getMaxPrintAgeMinutes();
	}
}
