package net.runelite.client.plugins.microbot.marketfactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;
import net.runelite.client.plugins.microbot.marketfactory.model.ItemMeta;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches and caches OSRS Wiki market data. Deliberately free of RuneLite client
 * references so the offline scan harness can drive it without a game client.
 *
 * <p>All methods are blocking and must be called from a script thread, never from the
 * client thread. Bulk endpoints are refreshed on their own TTLs; per-item history is
 * fetched on demand and cached because it is only needed for shortlisted candidates.
 */
@Slf4j
public final class MarketDataService
{
	private static final String API_BASE = "https://prices.runescape.wiki/api/v1/osrs";
	private static final String USER_AGENT = "Microbot-MarketFactory/0.1 (+https://github.com/Microbot-Hub)";
	private static final long MIN_REQUEST_GAP_MILLIS = 250L;
	private static final long HISTORY_TTL_MILLIS = TimeUnit.MINUTES.toMillis(60);
	private static final long HISTORY_FAILURE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(3);
	private static final int MAX_HISTORY_POINTS = 168;

	private final OkHttpClient httpClient;
	private final Map<Integer, HistoryEntry> histories = new ConcurrentHashMap<>();

	private Map<Integer, PricePoint> latest = Collections.emptyMap();
	private Map<Integer, ItemMeta> mapping = Collections.emptyMap();
	private Map<Integer, HourlyPoint> hourly = Collections.emptyMap();
	private long latestFetchedAt;
	private long mappingFetchedAt;
	private long hourlyFetchedAt;
	private long lastRequestAt;
	private String lastWarning = "";
	private volatile MarketSnapshot snapshot = MarketSnapshot.empty();

	public MarketDataService()
	{
		this.httpClient = new OkHttpClient.Builder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.callTimeout(45, TimeUnit.SECONDS)
			.build();
	}

	/**
	 * Refreshes whichever bulk tables are stale and returns an immutable snapshot.
	 * A failed endpoint keeps its previous data and is reported through the warning field.
	 */
	public synchronized MarketSnapshot refreshIfStale(long latestTtlMillis, long mappingTtlMillis, long hourlyTtlMillis)
	{
		long now = System.currentTimeMillis();
		StringBuilder warnings = new StringBuilder();

		if (mapping.isEmpty() || now - mappingFetchedAt >= mappingTtlMillis)
		{
			try
			{
				mapping = fetchMapping();
				mappingFetchedAt = now;
			}
			catch (Exception ex)
			{
				log.debug("Item mapping refresh failed", ex);
				warnings.append("mapping: ").append(ex.getMessage()).append("; ");
			}
		}

		if (latest.isEmpty() || now - latestFetchedAt >= latestTtlMillis)
		{
			try
			{
				latest = fetchLatest();
				latestFetchedAt = now;
			}
			catch (Exception ex)
			{
				log.debug("Latest price refresh failed", ex);
				warnings.append("prices: ").append(ex.getMessage()).append("; ");
			}
		}

		if (hourly.isEmpty() || now - hourlyFetchedAt >= hourlyTtlMillis)
		{
			try
			{
				hourly = fetchHourly();
				hourlyFetchedAt = now;
			}
			catch (Exception ex)
			{
				log.debug("Hourly aggregate refresh failed", ex);
				warnings.append("volume: ").append(ex.getMessage()).append("; ");
			}
		}

		lastWarning = warnings.toString();
		snapshot = new MarketSnapshot(now, latest, mapping, hourly, lastWarning);
		return snapshot;
	}

	/** Most recently built snapshot without triggering a refresh. */
	public MarketSnapshot currentSnapshot()
	{
		return snapshot;
	}

	/**
	 * Hourly history for one item, newest last, capped to seven days. Returns an empty
	 * list when the item has no history; failures are cached briefly to avoid hammering
	 * the API for a permanently broken item.
	 */
	public List<HourlyPoint> history(int itemId)
	{
		if (itemId <= 0)
		{
			return Collections.emptyList();
		}

		long now = System.currentTimeMillis();
		HistoryEntry entry = histories.get(itemId);
		if (entry != null)
		{
			long ttl = entry.failed ? HISTORY_FAILURE_TTL_MILLIS : HISTORY_TTL_MILLIS;
			if (now - entry.fetchedAt < ttl)
			{
				return entry.points;
			}
		}

		List<HourlyPoint> points;
		boolean failed = false;
		try
		{
			points = fetchTimeseries(itemId);
		}
		catch (Exception ex)
		{
			log.debug("History fetch failed for item {}", itemId, ex);
			points = Collections.emptyList();
			failed = true;
		}

		histories.put(itemId, new HistoryEntry(now, points, failed));
		return points;
	}

	public String getLastWarning()
	{
		return lastWarning;
	}

	private Map<Integer, PricePoint> fetchLatest() throws IOException
	{
		JsonObject root = request("/latest").getAsJsonObject();
		JsonObject data = root.getAsJsonObject("data");
		if (data == null)
		{
			throw new IOException("missing data object");
		}

		Map<Integer, PricePoint> result = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int id = parseInt(entry.getKey(), -1);
			if (id <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject price = entry.getValue().getAsJsonObject();
			int high = optInt(price, "high", PricePoint.UNKNOWN);
			int low = optInt(price, "low", PricePoint.UNKNOWN);
			long highTime = optLong(price, "highTime", 0L);
			long lowTime = optLong(price, "lowTime", 0L);
			if (high <= 0 && low <= 0)
			{
				continue;
			}
			result.put(id, new PricePoint(high, low, highTime, lowTime));
		}
		return result;
	}

	private Map<Integer, ItemMeta> fetchMapping() throws IOException
	{
		JsonArray array = request("/mapping").getAsJsonArray();
		Map<Integer, ItemMeta> result = new HashMap<>();
		for (JsonElement element : array)
		{
			if (!element.isJsonObject())
			{
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			int id = optInt(item, "id", -1);
			String name = optString(item, "name", "");
			if (id <= 0 || name.isEmpty())
			{
				continue;
			}
			result.put(id, new ItemMeta(
				id,
				name,
				optBoolean(item, "members", false),
				optInt(item, "limit", 0),
				optInt(item, "highalch", 0),
				optInt(item, "lowalch", 0),
				optInt(item, "value", 0)));
		}
		return result;
	}

	private Map<Integer, HourlyPoint> fetchHourly() throws IOException
	{
		JsonObject root = request("/1h").getAsJsonObject();
		JsonObject data = root.getAsJsonObject("data");
		if (data == null)
		{
			throw new IOException("missing data object");
		}
		long timestamp = optLong(root, "timestamp", 0L);

		Map<Integer, HourlyPoint> result = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int id = parseInt(entry.getKey(), -1);
			if (id <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject hour = entry.getValue().getAsJsonObject();
			int avgHigh = optInt(hour, "avgHighPrice", HourlyPoint.UNKNOWN);
			int avgLow = optInt(hour, "avgLowPrice", HourlyPoint.UNKNOWN);
			if (avgHigh <= 0 && avgLow <= 0)
			{
				continue;
			}
			result.put(id, new HourlyPoint(
				timestamp,
				avgHigh,
				avgLow,
				optLong(hour, "highPriceVolume", 0L),
				optLong(hour, "lowPriceVolume", 0L)));
		}
		return result;
	}

	private List<HourlyPoint> fetchTimeseries(int itemId) throws IOException
	{
		JsonObject root = request("/timeseries?timestep=1h&id=" + itemId).getAsJsonObject();
		JsonArray data = root.getAsJsonArray("data");
		if (data == null)
		{
			return Collections.emptyList();
		}

		List<HourlyPoint> points = new ArrayList<>();
		for (JsonElement element : data)
		{
			if (!element.isJsonObject())
			{
				continue;
			}
			JsonObject hour = element.getAsJsonObject();
			int avgHigh = optInt(hour, "avgHighPrice", HourlyPoint.UNKNOWN);
			int avgLow = optInt(hour, "avgLowPrice", HourlyPoint.UNKNOWN);
			if (avgHigh <= 0 && avgLow <= 0)
			{
				continue;
			}
			points.add(new HourlyPoint(
				optLong(hour, "timestamp", 0L),
				avgHigh,
				avgLow,
				optLong(hour, "highPriceVolume", 0L),
				optLong(hour, "lowPriceVolume", 0L)));
		}

		points.sort(Comparator.comparingLong(HourlyPoint::getTimestamp));
		if (points.size() > MAX_HISTORY_POINTS)
		{
			return new ArrayList<>(points.subList(points.size() - MAX_HISTORY_POINTS, points.size()));
		}
		return points;
	}

	private JsonElement request(String path) throws IOException
	{
		throttle();

		Request httpRequest = new Request.Builder()
			.url(API_BASE + path)
			.header("User-Agent", USER_AGENT)
			.get()
			.build();

		try (Response response = httpClient.newCall(httpRequest).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("HTTP " + response.code());
			}
			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("empty response body");
			}
			return new JsonParser().parse(body.string());
		}
	}

	private synchronized void throttle()
	{
		long now = System.currentTimeMillis();
		long waitMillis = lastRequestAt + MIN_REQUEST_GAP_MILLIS - now;
		if (waitMillis > 0)
		{
			try
			{
				Thread.sleep(waitMillis);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}
		lastRequestAt = System.currentTimeMillis();
	}

	private static int optInt(JsonObject object, String key, int fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull())
		{
			return fallback;
		}
		try
		{
			return element.getAsInt();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static long optLong(JsonObject object, String key, long fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull())
		{
			return fallback;
		}
		try
		{
			return element.getAsLong();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static boolean optBoolean(JsonObject object, String key, boolean fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull())
		{
			return fallback;
		}
		try
		{
			return element.getAsBoolean();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static String optString(JsonObject object, String key, String fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull())
		{
			return fallback;
		}
		try
		{
			return element.getAsString();
		}
		catch (RuntimeException ex)
		{
			return fallback;
		}
	}

	private static int parseInt(String value, int fallback)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static final class HistoryEntry
	{
		private final long fetchedAt;
		private final List<HourlyPoint> points;
		private final boolean failed;

		private HistoryEntry(long fetchedAt, List<HourlyPoint> points, boolean failed)
		{
			this.fetchedAt = fetchedAt;
			this.points = points;
			this.failed = failed;
		}
	}
}
