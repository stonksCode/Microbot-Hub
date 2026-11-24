package net.runelite.client.plugins.microbot.marketfactory.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable view of every market table the plugin needs in one scan: latest prints,
 * item metadata and the current hourly aggregates. Later phases add history lookups
 * through {@code MarketDataService}, which are deliberately not part of this snapshot
 * because they are fetched on demand for shortlisted items only.
 */
public final class MarketSnapshot
{
	private final long fetchedAtMillis;
	private final Map<Integer, PricePoint> latest;
	private final Map<Integer, ItemMeta> mapping;
	private final Map<Integer, HourlyPoint> hourly;
	private final Map<String, Integer> idByName;
	private final String warning;

	public MarketSnapshot(long fetchedAtMillis,
		Map<Integer, PricePoint> latest,
		Map<Integer, ItemMeta> mapping,
		Map<Integer, HourlyPoint> hourly,
		String warning)
	{
		this.fetchedAtMillis = fetchedAtMillis;
		this.latest = Collections.unmodifiableMap(new HashMap<>(latest));
		this.mapping = Collections.unmodifiableMap(new HashMap<>(mapping));
		this.hourly = Collections.unmodifiableMap(new HashMap<>(hourly));
		this.warning = warning == null ? "" : warning;

		Map<String, Integer> index = new HashMap<>();
		for (ItemMeta meta : this.mapping.values())
		{
			if (meta.getName() != null && !meta.getName().isEmpty())
			{
				index.put(normalise(meta.getName()), meta.getId());
			}
		}
		this.idByName = Collections.unmodifiableMap(index);
	}

	public static MarketSnapshot empty()
	{
		return new MarketSnapshot(0L, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), "no market data loaded");
	}

	public long getFetchedAtMillis()
	{
		return fetchedAtMillis;
	}

	public boolean isEmpty()
	{
		return mapping.isEmpty() || latest.isEmpty();
	}

	public Collection<ItemMeta> getItems()
	{
		return mapping.values();
	}

	public ItemMeta item(int id)
	{
		return mapping.get(id);
	}

	public PricePoint price(int id)
	{
		return latest.get(id);
	}

	public HourlyPoint hour(int id)
	{
		return hourly.get(id);
	}

	public Integer idFor(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		return idByName.get(normalise(itemName));
	}

	public String getWarning()
	{
		return warning;
	}

	private static String normalise(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
