package net.runelite.client.plugins.microbot.marketfactory.model;

/**
 * One hourly aggregate from /api/v1/osrs/1h or /api/v1/osrs/timeseries.
 * Volumes are the number of items traded at the high (buyer-initiated) and low
 * (seller-initiated) price during that hour.
 */
public final class HourlyPoint
{
	public static final int UNKNOWN = -1;

	private final long timestamp;
	private final int avgHighPrice;
	private final int avgLowPrice;
	private final long highPriceVolume;
	private final long lowPriceVolume;

	public HourlyPoint(long timestamp, int avgHighPrice, int avgLowPrice,
		long highPriceVolume, long lowPriceVolume)
	{
		this.timestamp = timestamp;
		this.avgHighPrice = avgHighPrice;
		this.avgLowPrice = avgLowPrice;
		this.highPriceVolume = highPriceVolume;
		this.lowPriceVolume = lowPriceVolume;
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public int getAvgHighPrice()
	{
		return avgHighPrice;
	}

	public int getAvgLowPrice()
	{
		return avgLowPrice;
	}

	public long getHighPriceVolume()
	{
		return highPriceVolume;
	}

	public long getLowPriceVolume()
	{
		return lowPriceVolume;
	}

	public boolean hasHigh()
	{
		return avgHighPrice > 0;
	}

	public boolean hasLow()
	{
		return avgLowPrice > 0;
	}

	public boolean isComplete()
	{
		return hasHigh() && hasLow();
	}

	/** Mid price, or 0 when either side is missing. */
	public double midPrice()
	{
		if (!isComplete())
		{
			return 0D;
		}
		return (avgHighPrice + avgLowPrice) / 2D;
	}

	public long totalVolume()
	{
		return highPriceVolume + lowPriceVolume;
	}
}
