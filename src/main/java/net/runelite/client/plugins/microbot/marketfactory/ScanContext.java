package net.runelite.client.plugins.microbot.marketfactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.plugins.microbot.marketfactory.enums.MembershipMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.enums.RouteMode;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;

/**
 * Everything one scan needs that is not part of the market data itself: capital, account
 * membership, the risk profile, filters and the two environment-facing providers.
 *
 * <p>Use {@link #builder()} so future parameters do not break call sites. Watchlist and
 * blacklist entries are stored lower-case and trimmed so callers can pass raw config text.
 */
public final class ScanContext
{
	private final MarketSnapshot snapshot;
	private final RiskProfile risk;
	private final long capital;
	private final boolean membersAccount;
	private final boolean memberWorld;
	private final MembershipMode membershipMode;
	private final RouteMode routeMode;
	private final Set<String> watchlist;
	private final Set<String> blacklist;
	private final boolean watchlistOnly;
	private final int deepScanLimit;
	private final long nowMillis;
	private final HistoryProvider historyProvider;
	private final SkillSource skillSource;

	private ScanContext(Builder builder)
	{
		this.snapshot = builder.snapshot;
		this.risk = builder.risk;
		this.capital = Math.max(0L, builder.capital);
		this.membersAccount = builder.membersAccount;
		this.memberWorld = builder.memberWorld;
		this.membershipMode = builder.membershipMode;
		this.routeMode = builder.routeMode;
		this.watchlist = normalise(builder.watchlist);
		this.blacklist = normalise(builder.blacklist);
		this.watchlistOnly = builder.watchlistOnly;
		this.deepScanLimit = Math.max(0, builder.deepScanLimit);
		this.nowMillis = builder.nowMillis > 0 ? builder.nowMillis : System.currentTimeMillis();
		this.historyProvider = builder.historyProvider;
		this.skillSource = builder.skillSource;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public MarketSnapshot getSnapshot()
	{
		return snapshot;
	}

	public RiskProfile getRisk()
	{
		return risk;
	}

	public long getCapital()
	{
		return capital;
	}

	public boolean isMembersAccount()
	{
		return membersAccount;
	}

	public boolean isMemberWorld()
	{
		return memberWorld;
	}

	public MembershipMode getMembershipMode()
	{
		return membershipMode;
	}

	public RouteMode getRouteMode()
	{
		return routeMode;
	}

	public Set<String> getWatchlist()
	{
		return watchlist;
	}

	public Set<String> getBlacklist()
	{
		return blacklist;
	}

	public boolean isWatchlistOnly()
	{
		return watchlistOnly;
	}

	public int getDeepScanLimit()
	{
		return deepScanLimit;
	}

	public long getNowMillis()
	{
		return nowMillis;
	}

	public HistoryProvider getHistoryProvider()
	{
		return historyProvider;
	}

	public SkillSource getSkillSource()
	{
		return skillSource;
	}

	/** Splits a comma or newline separated list into normalised item names. */
	public static Set<String> parseNameList(String raw)
	{
		Set<String> names = new LinkedHashSet<>();
		if (raw == null || raw.trim().isEmpty())
		{
			return names;
		}
		for (String part : raw.split("[,;\\r\\n]+"))
		{
			String name = part.trim();
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return normalise(names);
	}

	private static Set<String> normalise(Set<String> names)
	{
		if (names == null || names.isEmpty())
		{
			return Collections.emptySet();
		}
		Set<String> result = new LinkedHashSet<>();
		for (String name : names)
		{
			if (name != null && !name.trim().isEmpty())
			{
				result.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}
		return Collections.unmodifiableSet(result);
	}

	public static final class Builder
	{
		private MarketSnapshot snapshot = MarketSnapshot.empty();
		private RiskProfile risk = RiskProfile.preset(RiskLevel.BALANCED);
		private long capital;
		private boolean membersAccount;
		private boolean memberWorld;
		private MembershipMode membershipMode = MembershipMode.AUTO;
		private RouteMode routeMode = RouteMode.BEST_OF_BOTH;
		private Set<String> watchlist = Collections.emptySet();
		private Set<String> blacklist = Collections.emptySet();
		private boolean watchlistOnly;
		private int deepScanLimit = 60;
		private long nowMillis;
		private HistoryProvider historyProvider = itemId -> Collections.emptyList();
		private SkillSource skillSource = skill -> 99;

		public Builder snapshot(MarketSnapshot value)
		{
			this.snapshot = value == null ? MarketSnapshot.empty() : value;
			return this;
		}

		public Builder risk(RiskProfile value)
		{
			this.risk = value == null ? RiskProfile.preset(RiskLevel.BALANCED) : value;
			return this;
		}

		public Builder capital(long value)
		{
			this.capital = value;
			return this;
		}

		public Builder account(boolean membersAccount, boolean memberWorld)
		{
			this.membersAccount = membersAccount;
			this.memberWorld = memberWorld;
			return this;
		}

		public Builder membershipMode(MembershipMode value)
		{
			this.membershipMode = value == null ? MembershipMode.AUTO : value;
			return this;
		}

		public Builder routeMode(RouteMode value)
		{
			this.routeMode = value == null ? RouteMode.BEST_OF_BOTH : value;
			return this;
		}

		public Builder watchlist(Set<String> value)
		{
			this.watchlist = value;
			return this;
		}

		public Builder watchlist(String csv)
		{
			this.watchlist = parseNameList(csv);
			return this;
		}

		public Builder blacklist(Set<String> value)
		{
			this.blacklist = value;
			return this;
		}

		public Builder blacklist(String csv)
		{
			this.blacklist = parseNameList(csv);
			return this;
		}

		public Builder watchlistOnly(boolean value)
		{
			this.watchlistOnly = value;
			return this;
		}

		public Builder deepScanLimit(int value)
		{
			this.deepScanLimit = value;
			return this;
		}

		public Builder now(long epochMillis)
		{
			this.nowMillis = epochMillis;
			return this;
		}

		public Builder historyProvider(HistoryProvider value)
		{
			this.historyProvider = value == null ? itemId -> Collections.emptyList() : value;
			return this;
		}

		public Builder skillSource(SkillSource value)
		{
			this.skillSource = value == null ? skill -> 99 : value;
			return this;
		}

		public ScanContext build()
		{
			return new ScanContext(this);
		}
	}
}
