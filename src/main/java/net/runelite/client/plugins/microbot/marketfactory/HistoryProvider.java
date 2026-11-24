package net.runelite.client.plugins.microbot.marketfactory;

import java.util.List;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;

/**
 * Supplies hourly history for one item. In the plugin this is backed by
 * {@link MarketDataService}; the offline harness can return recorded data without
 * touching the network.
 */
@FunctionalInterface
public interface HistoryProvider
{
	/** Newest last, capped by the provider. Never returns null; use an empty list instead. */
	List<HourlyPoint> history(int itemId);
}
