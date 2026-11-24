package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.enums.RouteMode;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;
import net.runelite.client.plugins.microbot.marketfactory.model.ItemMeta;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;

/**
 * Deterministic market fixture for the Market Factory tests: flat, fresh, symmetric markets,
 * so every number a test asserts can be derived by hand.
 */
public final class TestMarket
{
	public static final long NOW = 1_800_000_000_000L;
	private static final int HISTORY_HOURS = 72;

	private static final class Item
	{
		private final int id;
		private final String name;
		private final int limit;
		private final int low;
		private final int high;
		private final long volume;

		private Item(int id, String name, int limit, int low, int high, long volume)
		{
			this.id = id;
			this.name = name;
			this.limit = limit;
			this.low = low;
			this.high = high;
			this.volume = volume;
		}
	}

	private final Map<Integer, Item> items = new LinkedHashMap<>();

	/** @param volume items traded per hour on each side */
	public TestMarket add(int id, String name, int limit, int low, int high, long volume)
	{
		items.put(id, new Item(id, name, limit, low, high, volume));
		return this;
	}

	/** Snapshot whose latest prints are one minute old at {@code nowMillis}. */
	public MarketSnapshot snapshot(long nowMillis)
	{
		long nowSeconds = nowMillis / 1000L;
		Map<Integer, PricePoint> latest = new HashMap<>();
		Map<Integer, ItemMeta> mapping = new HashMap<>();
		Map<Integer, HourlyPoint> hourly = new HashMap<>();
		for (Item item : items.values())
		{
			latest.put(item.id, new PricePoint(item.high, item.low, nowSeconds - 60, nowSeconds - 60));
			mapping.put(item.id, new ItemMeta(item.id, item.name, false, item.limit, 0, 0, 0));
			hourly.put(item.id, new HourlyPoint(nowSeconds - 1800, item.high, item.low, item.volume, item.volume));
		}
		return new MarketSnapshot(nowMillis, latest, mapping, hourly, "");
	}

	public List<HourlyPoint> history(int itemId, long nowMillis)
	{
		Item item = items.get(itemId);
		if (item == null)
		{
			return Collections.emptyList();
		}
		long nowSeconds = nowMillis / 1000L;
		List<HourlyPoint> points = new ArrayList<>();
		for (int i = 0; i < HISTORY_HOURS; i++)
		{
			long timestamp = nowSeconds - (long) (HISTORY_HOURS - i) * 3600L;
			points.add(new HourlyPoint(timestamp, item.high, item.low, item.volume, item.volume));
		}
		return points;
	}

	public ScanContext context(long capital, long nowMillis, int deepScanLimit)
	{
		return ScanContext.builder()
			.snapshot(snapshot(nowMillis))
			.risk(RiskProfile.preset(RiskLevel.BALANCED))
			.capital(capital)
			.account(true, true)
			.routeMode(RouteMode.FLIP_ONLY)
			.deepScanLimit(deepScanLimit)
			.historyProvider(itemId -> history(itemId, nowMillis))
			.now(nowMillis)
			.build();
	}
}
