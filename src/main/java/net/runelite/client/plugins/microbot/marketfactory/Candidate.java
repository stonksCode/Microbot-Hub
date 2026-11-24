package net.runelite.client.plugins.microbot.marketfactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.marketfactory.enums.CandidateType;
import net.runelite.client.plugins.microbot.marketfactory.enums.RejectReason;

/**
 * One actionable idea: either a same-item flip or one full processing cycle. The engine
 * produces candidates already sized against capital, buy limits and liquidity, so the
 * allocator only has to pick between them.
 */
public final class Candidate
{
	private final CandidateType type;
	private final String label;
	private final int itemId;
	private final FactoryRecipe recipe;
	private final int quantity;
	private final long capital;
	private final long revenue;
	private final long profit;
	private final double roiPercent;
	private final double fillHours;
	private final double gpPerHour;
	private final double score;
	private final double confidence;
	private final double stability;
	private final long maxHoldMinutes;
	private final int buyPrice;
	private final int sellPrice;
	private final int buyLimit;
	private final double cycleHours;
	private final Map<RejectReason, String> rejects;
	private final List<String> notes;

	private Candidate(Builder builder)
	{
		this.type = builder.type;
		this.label = builder.label;
		this.itemId = builder.itemId;
		this.recipe = builder.recipe;
		this.quantity = builder.quantity;
		this.capital = builder.capital;
		this.revenue = builder.revenue;
		this.profit = builder.profit;
		this.roiPercent = builder.roiPercent;
		this.fillHours = builder.fillHours;
		this.gpPerHour = builder.gpPerHour;
		this.score = builder.score;
		this.confidence = builder.confidence;
		this.stability = builder.stability;
		this.maxHoldMinutes = builder.maxHoldMinutes;
		this.buyPrice = builder.buyPrice;
		this.sellPrice = builder.sellPrice;
		this.buyLimit = builder.buyLimit;
		this.cycleHours = builder.cycleHours;
		this.rejects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.rejects));
		this.notes = Collections.unmodifiableList(builder.notes);
	}

	public static Builder builder(CandidateType type, String label, int itemId)
	{
		return new Builder(type, label, itemId);
	}

	public CandidateType getType()
	{
		return type;
	}

	public String getLabel()
	{
		return label;
	}

	public int getItemId()
	{
		return itemId;
	}

	public FactoryRecipe getRecipe()
	{
		return recipe;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public long getCapital()
	{
		return capital;
	}

	public long getRevenue()
	{
		return revenue;
	}

	public long getProfit()
	{
		return profit;
	}

	public double getRoiPercent()
	{
		return roiPercent;
	}

	public double getFillHours()
	{
		return fillHours;
	}

	public double getGpPerHour()
	{
		return gpPerHour;
	}

	public double getScore()
	{
		return score;
	}

	public double getConfidence()
	{
		return confidence;
	}

	public double getStability()
	{
		return stability;
	}

	/** Holding tolerance derived from predicted fill time and the risk profile. */
	public long getMaxHoldMinutes()
	{
		return maxHoldMinutes;
	}

	/** Planned entry price per unit (flips only; 0 for processes). */
	public int getBuyPrice()
	{
		return buyPrice;
	}

	/** Planned exit price per unit before tax (flips only; 0 for processes). */
	public int getSellPrice()
	{
		return sellPrice;
	}

	/** Four-hour Grand Exchange buy limit, or 0 when unknown. */
	public int getBuyLimit()
	{
		return buyLimit;
	}

	/**
	 * Hours between repeating this trade: the round-trip fill time, or the time the buy limit
	 * needs to reset if that is longer. gp/h is profit divided by this.
	 */
	public double getCycleHours()
	{
		return cycleHours;
	}

	public Map<RejectReason, String> getRejects()
	{
		return rejects;
	}

	public List<String> getNotes()
	{
		return notes;
	}

	public boolean isViable()
	{
		return rejects.isEmpty() && quantity > 0 && profit > 0;
	}

	public String rejectionSummary()
	{
		return MarketAnalyzer.describe(rejects);
	}

	@Override
	public String toString()
	{
		return type + " " + label + "{qty=" + quantity + ", capital=" + capital
			+ ", profit=" + profit + ", score=" + Math.round(score) + '}';
	}

	public static final class Builder
	{
		private final CandidateType type;
		private final String label;
		private final int itemId;
		private FactoryRecipe recipe;
		private int quantity;
		private long capital;
		private long revenue;
		private long profit;
		private double roiPercent;
		private double fillHours;
		private double gpPerHour;
		private double score;
		private double confidence;
		private double stability;
		private long maxHoldMinutes;
		private int buyPrice;
		private int sellPrice;
		private int buyLimit;
		private double cycleHours;
		private final Map<RejectReason, String> rejects = new LinkedHashMap<>();
		private final java.util.List<String> notes = new java.util.ArrayList<>();

		private Builder(CandidateType type, String label, int itemId)
		{
			this.type = type;
			this.label = label;
			this.itemId = itemId;
		}

		public Builder recipe(FactoryRecipe value)
		{
			this.recipe = value;
			return this;
		}

		public Builder sizing(int quantity, long capital, long revenue, long profit,
			double roiPercent, double fillHours, double gpPerHour, double score)
		{
			this.quantity = quantity;
			this.capital = capital;
			this.revenue = revenue;
			this.profit = profit;
			this.roiPercent = roiPercent;
			this.fillHours = fillHours;
			this.gpPerHour = gpPerHour;
			this.score = score;
			return this;
		}

		public Builder quality(double confidence, double stability)
		{
			this.confidence = confidence;
			this.stability = stability;
			return this;
		}

		public Builder execution(int buyPrice, int sellPrice, int buyLimit, double cycleHours)
		{
			this.buyPrice = buyPrice;
			this.sellPrice = sellPrice;
			this.buyLimit = Math.max(0, buyLimit);
			this.cycleHours = cycleHours;
			return this;
		}

		public Builder maxHoldMinutes(long value)
		{
			this.maxHoldMinutes = Math.max(0L, value);
			return this;
		}

		public Builder addReject(RejectReason reason, String detail)
		{
			rejects.putIfAbsent(reason, detail == null ? "" : detail);
			return this;
		}

		public Builder addNote(String note)
		{
			if (note != null && !note.isEmpty())
			{
				notes.add(note);
			}
			return this;
		}

		public Candidate build()
		{
			return new Candidate(this);
		}
	}
}
