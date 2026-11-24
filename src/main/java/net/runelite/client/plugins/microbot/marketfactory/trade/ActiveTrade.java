package net.runelite.client.plugins.microbot.marketfactory.trade;

import net.runelite.client.plugins.microbot.marketfactory.Candidate;
import net.runelite.client.plugins.microbot.marketfactory.RiskProfile;

/**
 * One flip in flight: buy, collect, sell, collect. All mutation happens on the trade thread
 * through {@link TradeRunner}; the overlay only reads the public getters, so the display
 * fields are volatile.
 */
public final class ActiveTrade
{
	public enum Phase
	{
		BUYING,
		COLLECTING_BUY,
		SELLING,
		COLLECTING_SELL,
		DONE,
		FAILED
	}

	private final String tradeId;
	private final String itemName;
	private final int itemId;
	private final long startedAt;

	// Plan, fixed when the trade starts.
	final Candidate candidate;
	final RiskProfile risk;
	final int plannedQuantity;
	final int plannedBuyPrice;
	final int plannedSellPrice;
	final long predictedProfit;
	final int baselineInventory;
	final long buyPatienceMillis;
	final long sellStepMillis;
	final long maxHoldUntil;

	// Progress, owned by the trade thread.
	volatile Phase phase = Phase.BUYING;
	volatile String status = "Starting";
	long phaseStartedAt;
	long lastPlacedAt;
	boolean offerPlaced;
	int placeAttempts;
	int collectAttempts;
	int errors;
	int bought;
	long spent;
	int sold;
	long revenue;
	int sellPrice;
	int pendingSellPrice;
	int repriceCount;
	boolean liquidating;

	// Result.
	volatile long realizedProfit;
	volatile String outcome = "";
	long finishedAt;

	ActiveTrade(String tradeId, Candidate candidate, RiskProfile risk, int plannedQuantity,
		int plannedBuyPrice, int plannedSellPrice, long predictedProfit, int baselineInventory,
		long now, long buyPatienceMillis, long sellStepMillis, long maxHoldUntil)
	{
		this.tradeId = tradeId;
		this.candidate = candidate;
		this.risk = risk;
		this.itemId = candidate.getItemId();
		this.itemName = candidate.getLabel();
		this.plannedQuantity = plannedQuantity;
		this.plannedBuyPrice = plannedBuyPrice;
		this.plannedSellPrice = plannedSellPrice;
		this.predictedProfit = predictedProfit;
		this.baselineInventory = baselineInventory;
		this.startedAt = now;
		this.phaseStartedAt = now;
		this.buyPatienceMillis = buyPatienceMillis;
		this.sellStepMillis = sellStepMillis;
		this.maxHoldUntil = maxHoldUntil;
		this.sellPrice = plannedSellPrice;
	}

	void setPhase(Phase next, long now)
	{
		this.phase = next;
		this.phaseStartedAt = now;
		this.placeAttempts = 0;
		this.collectAttempts = 0;
	}

	/** Average price paid per unit; the planned price until something has been bought. */
	int averageBuyPrice()
	{
		if (bought <= 0)
		{
			return plannedBuyPrice;
		}
		return (int) Math.ceil((double) spent / bought);
	}

	public String getTradeId()
	{
		return tradeId;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getItemId()
	{
		return itemId;
	}

	public long getStartedAt()
	{
		return startedAt;
	}

	public long getFinishedAt()
	{
		return finishedAt;
	}

	public Phase getPhase()
	{
		return phase;
	}

	public String getStatus()
	{
		return status;
	}

	public int getPlannedQuantity()
	{
		return plannedQuantity;
	}

	public int getPlannedBuyPrice()
	{
		return plannedBuyPrice;
	}

	public int getPlannedSellPrice()
	{
		return plannedSellPrice;
	}

	public long getPredictedProfit()
	{
		return predictedProfit;
	}

	public int getBought()
	{
		return bought;
	}

	public int getSold()
	{
		return sold;
	}

	public long getRevenue()
	{
		return revenue;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getRepriceCount()
	{
		return repriceCount;
	}

	public long getRealizedProfit()
	{
		return realizedProfit;
	}

	public String getOutcome()
	{
		return outcome;
	}

	public boolean isActive()
	{
		return phase != Phase.DONE && phase != Phase.FAILED;
	}
}
