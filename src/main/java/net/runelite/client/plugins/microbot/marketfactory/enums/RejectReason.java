package net.runelite.client.plugins.microbot.marketfactory.enums;

/**
 * Why a candidate was filtered out. The detail string attached to each reason carries the
 * measured value and the configured limit so the overlay and logs are self-explanatory.
 */
public enum RejectReason
{
	NO_PRICE("no usable price"),
	STALE_PRINT("stale print"),
	NO_VOLUME("no traded volume"),
	LOW_VOLUME("volume below minimum"),
	LOW_MARGIN("margin below minimum"),
	LOW_ROI("ROI below minimum"),
	HIGH_VOLATILITY("volatility above limit"),
	UNSTABLE_SPREAD("spread unstable"),
	DOWNTREND("price trending down"),
	INSUFFICIENT_HISTORY("not enough price history"),
	INSUFFICIENT_CAPITAL("capital below one unit"),
	NO_BUY_LIMIT("buy limit is zero"),
	MEMBERS_ONLY("members item"),
	BLACKLISTED("blacklisted"),
	NOT_WATCHLISTED("not on watchlist"),
	MISSING_INPUT("missing input price"),
	MISSING_OUTPUT("missing output price"),
	OUTPUT_ILLIQUID("output too illiquid"),
	NO_OUTPUT("no saleable output"),
	SKILL_TOO_LOW("skill level too low");

	private final String label;

	RejectReason(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
