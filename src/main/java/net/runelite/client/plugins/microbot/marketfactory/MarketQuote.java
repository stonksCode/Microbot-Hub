package net.runelite.client.plugins.microbot.marketfactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import net.runelite.client.plugins.microbot.marketfactory.enums.RejectReason;

/**
 * Unit economics for one item at one point in time. A quote is either viable or carries
 * one or more {@link RejectReason}s explaining why it was filtered out. Nothing here
 * depends on how many units the plugin intends to trade: quantity only affects capital
 * use and fill time, not the per-unit margin.
 */
public final class MarketQuote
{
	private final int itemId;
	private final String itemName;
	private final int buyPrice;
	private final int sellPrice;
	private final int taxPerUnit;
	private final int netMargin;
	private final double roiPercent;
	private final double buyVolumePerHour;
	private final double sellVolumePerHour;
	private final double effectiveUnitsPerHour;
	private final double gpPerHourPotential;
	private final double volatilityCv;
	private final double trendPercentPerHour;
	private final double spreadRatio;
	private final long printAgeMinutes;
	private final int historyPoints;
	private final double confidence;
	private final double stability;
	private final double score;
	private final Map<RejectReason, String> rejects;

	private MarketQuote(Builder builder)
	{
		this.itemId = builder.itemId;
		this.itemName = builder.itemName;
		this.buyPrice = builder.buyPrice;
		this.sellPrice = builder.sellPrice;
		this.taxPerUnit = builder.taxPerUnit;
		this.netMargin = builder.netMargin;
		this.roiPercent = builder.roiPercent;
		this.buyVolumePerHour = builder.buyVolumePerHour;
		this.sellVolumePerHour = builder.sellVolumePerHour;
		this.effectiveUnitsPerHour = builder.effectiveUnitsPerHour;
		this.gpPerHourPotential = builder.gpPerHourPotential;
		this.volatilityCv = builder.volatilityCv;
		this.trendPercentPerHour = builder.trendPercentPerHour;
		this.spreadRatio = builder.spreadRatio;
		this.printAgeMinutes = builder.printAgeMinutes;
		this.historyPoints = builder.historyPoints;
		this.confidence = builder.confidence;
		this.stability = builder.stability;
		this.score = builder.score;
		this.rejects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.rejects));
	}

	public static Builder builder(int itemId, String itemName)
	{
		return new Builder(itemId, itemName);
	}

	/** No usable price at all; used when an analysis cannot even start. */
	public static MarketQuote missing(int itemId, String itemName, RejectReason reason, String detail)
	{
		return builder(itemId, itemName)
			.addReject(reason, detail)
			.build();
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getItemName()
	{
		return itemName;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getTaxPerUnit()
	{
		return taxPerUnit;
	}

	public int getNetMargin()
	{
		return netMargin;
	}

	public double getRoiPercent()
	{
		return roiPercent;
	}

	public double getBuyVolumePerHour()
	{
		return buyVolumePerHour;
	}

	public double getSellVolumePerHour()
	{
		return sellVolumePerHour;
	}

	public double getEffectiveUnitsPerHour()
	{
		return effectiveUnitsPerHour;
	}

	public double getGpPerHourPotential()
	{
		return gpPerHourPotential;
	}

	/** Coefficient of variation of hourly mid prices, or -1 when there was no history. */
	public double getVolatilityCv()
	{
		return volatilityCv;
	}

	public double getTrendPercentPerHour()
	{
		return trendPercentPerHour;
	}

	public double getSpreadRatio()
	{
		return spreadRatio;
	}

	public long getPrintAgeMinutes()
	{
		return printAgeMinutes;
	}

	public int getHistoryPoints()
	{
		return historyPoints;
	}

	public double getConfidence()
	{
		return confidence;
	}

	public double getStability()
	{
		return stability;
	}

	public double getScore()
	{
		return score;
	}

	public boolean hasBuyPrice()
	{
		return buyPrice > 0;
	}

	public boolean hasSellPrice()
	{
		return sellPrice > 0;
	}

	public boolean isViable()
	{
		return rejects.isEmpty() && hasBuyPrice() && hasSellPrice();
	}

	public Map<RejectReason, String> getRejects()
	{
		return rejects;
	}

	public String rejectionSummary()
	{
		if (rejects.isEmpty())
		{
			return "";
		}
		StringJoiner joiner = new StringJoiner("; ");
		for (Map.Entry<RejectReason, String> entry : rejects.entrySet())
		{
			joiner.add(entry.getKey().getLabel() + " (" + entry.getValue() + ')');
		}
		return joiner.toString();
	}

	@Override
	public String toString()
	{
		return itemName + "{buy=" + buyPrice + ", sell=" + sellPrice + ", margin=" + netMargin
			+ ", roi=" + String.format("%.2f", roiPercent) + "%, viable=" + isViable() + '}';
	}

	public static final class Builder
	{
		private final int itemId;
		private final String itemName;
		private int buyPrice;
		private int sellPrice;
		private int taxPerUnit;
		private int netMargin;
		private double roiPercent;
		private double buyVolumePerHour;
		private double sellVolumePerHour;
		private double effectiveUnitsPerHour;
		private double gpPerHourPotential;
		private double volatilityCv = -1D;
		private double trendPercentPerHour;
		private double spreadRatio;
		private long printAgeMinutes;
		private int historyPoints;
		private double confidence;
		private double stability;
		private double score;
		private final Map<RejectReason, String> rejects = new LinkedHashMap<>();

		private Builder(int itemId, String itemName)
		{
			this.itemId = itemId;
			this.itemName = itemName == null ? ("item " + itemId) : itemName;
		}

		public Builder prices(int buyPrice, int sellPrice, int taxPerUnit, int netMargin, double roiPercent)
		{
			this.buyPrice = buyPrice;
			this.sellPrice = sellPrice;
			this.taxPerUnit = taxPerUnit;
			this.netMargin = netMargin;
			this.roiPercent = roiPercent;
			return this;
		}

		public Builder volumes(double buyVolumePerHour, double sellVolumePerHour,
			double effectiveUnitsPerHour, double gpPerHourPotential)
		{
			this.buyVolumePerHour = buyVolumePerHour;
			this.sellVolumePerHour = sellVolumePerHour;
			this.effectiveUnitsPerHour = effectiveUnitsPerHour;
			this.gpPerHourPotential = gpPerHourPotential;
			return this;
		}

		public Builder risk(double volatilityCv, double trendPercentPerHour, double spreadRatio,
			long printAgeMinutes, int historyPoints, double confidence, double stability, double score)
		{
			this.volatilityCv = volatilityCv;
			this.trendPercentPerHour = trendPercentPerHour;
			this.spreadRatio = spreadRatio;
			this.printAgeMinutes = printAgeMinutes;
			this.historyPoints = historyPoints;
			this.confidence = confidence;
			this.stability = stability;
			this.score = score;
			return this;
		}

		public Builder addReject(RejectReason reason, String detail)
		{
			rejects.putIfAbsent(reason, detail == null ? "" : detail);
			return this;
		}

		public MarketQuote build()
		{
			return new MarketQuote(this);
		}
	}
}
