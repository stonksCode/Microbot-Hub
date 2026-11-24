package net.runelite.client.plugins.microbot.marketfactory.model;

/**
 * Last traded prints for one item, as returned by /api/v1/osrs/latest.
 * A missing side is stored as {@link #UNKNOWN} because the API returns JSON null for items
 * that have never traded on that side.
 */
public final class PricePoint
{
	public static final int UNKNOWN = -1;

	private final int high;
	private final int low;
	private final long highTime;
	private final long lowTime;

	public PricePoint(int high, int low, long highTime, long lowTime)
	{
		this.high = high;
		this.low = low;
		this.highTime = highTime;
		this.lowTime = lowTime;
	}

	public int getHigh()
	{
		return high;
	}

	public int getLow()
	{
		return low;
	}

	public long getHighTime()
	{
		return highTime;
	}

	public long getLowTime()
	{
		return lowTime;
	}

	public boolean hasHigh()
	{
		return high > 0;
	}

	public boolean hasLow()
	{
		return low > 0;
	}

	public boolean isComplete()
	{
		return hasHigh() && hasLow();
	}

	/** Oldest print timestamp in epoch seconds, or 0 when both sides are unknown. */
	public long oldestPrintTime()
	{
		long oldest = Long.MAX_VALUE;
		if (hasHigh() && highTime > 0)
		{
			oldest = Math.min(oldest, highTime);
		}
		if (hasLow() && lowTime > 0)
		{
			oldest = Math.min(oldest, lowTime);
		}
		return oldest == Long.MAX_VALUE ? 0L : oldest;
	}

	/** Newest print timestamp in epoch seconds, or 0 when both sides are unknown. */
	public long newestPrintTime()
	{
		long newest = 0L;
		if (hasHigh() && highTime > 0)
		{
			newest = Math.max(newest, highTime);
		}
		if (hasLow() && lowTime > 0)
		{
			newest = Math.max(newest, lowTime);
		}
		return newest;
	}

	@Override
	public String toString()
	{
		return "PricePoint{high=" + high + ", low=" + low + '}';
	}
}
