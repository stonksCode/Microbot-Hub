package net.runelite.client.plugins.microbot.marketfactory.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.marketfactory.MarketAnalyzer;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;

/**
 * Simulated Grand Exchange. Offers fill gradually at a fixed share of the item's hourly
 * volume, and only while the market trades through the offer price: a bid fills while the
 * last instant-sell print is at or below it, an ask while the last instant-buy print is at
 * or above it. Sells pay the 2% tax.
 *
 * <p>This validates the state machine end to end (placing, partial fills, timeouts, repricing,
 * collecting, accounting). It cannot validate the fill-rate assumption itself, because the
 * share of volume is the same number the planner uses; only live trades can do that.
 */
public final class PaperGeGateway implements GeGateway
{
	private static final double MAX_TICK_HOURS = 0.5D;

	private final Supplier<MarketSnapshot> snapshots;
	private final LongSupplier clock;
	private final double volumeShare;
	private final int slots;
	private final Map<String, Long> inventory = new HashMap<>();
	private final List<PaperOffer> offers = new ArrayList<>();
	private long coins;
	private long lastTick;

	/**
	 * @param volumeShare fraction of hourly volume captured by an offer, e.g. 0.05
	 */
	public PaperGeGateway(Supplier<MarketSnapshot> snapshots, LongSupplier clock, double volumeShare,
		long startingCoins, int slots)
	{
		this.snapshots = snapshots;
		this.clock = clock;
		this.volumeShare = Math.max(0D, volumeShare);
		this.coins = Math.max(0L, startingCoins);
		this.slots = Math.max(1, slots);
		this.lastTick = clock.getAsLong();
	}

	private static final class PaperOffer
	{
		private final int itemId;
		private final String name;
		private final boolean buy;
		private final int price;
		private final int total;
		private int filled;
		private double carry;
		private long escrow;
		private long proceeds;
		private boolean cancelled;

		private PaperOffer(int itemId, String name, boolean buy, int price, int total)
		{
			this.itemId = itemId;
			this.name = name;
			this.buy = buy;
			this.price = price;
			this.total = total;
		}

		private boolean isComplete()
		{
			return filled >= total;
		}
	}

	@Override
	public boolean isReady()
	{
		return true;
	}

	@Override
	public boolean isPaper()
	{
		return true;
	}

	@Override
	public long freeCoins()
	{
		return coins;
	}

	@Override
	public int freeSlots()
	{
		return slots - offers.size();
	}

	@Override
	public boolean place(boolean buy, int itemId, String itemName, int price, int quantity)
	{
		if (offers.size() >= slots || price <= 0 || quantity <= 0)
		{
			return false;
		}
		PaperOffer offer = new PaperOffer(itemId, itemName, buy, price, quantity);
		if (buy)
		{
			long cost = (long) price * quantity;
			if (cost > coins)
			{
				return false;
			}
			coins -= cost;
			offer.escrow = cost;
		}
		else
		{
			String key = key(itemName);
			long held = inventory.getOrDefault(key, 0L);
			if (held < quantity)
			{
				return false;
			}
			inventory.put(key, held - quantity);
		}
		offers.add(offer);
		return true;
	}

	@Override
	public OfferView offer(int itemId, boolean buy)
	{
		for (int i = 0; i < offers.size(); i++)
		{
			PaperOffer offer = offers.get(i);
			if (offer.itemId != itemId || offer.buy != buy)
			{
				continue;
			}
			OfferView.Status status = offer.cancelled ? OfferView.Status.CANCELLED
				: offer.isComplete() ? OfferView.Status.COMPLETE
				: OfferView.Status.ACTIVE;
			return new OfferView(i, itemId, buy, status, offer.filled, offer.total, offer.price);
		}
		return null;
	}

	@Override
	public boolean abort(int itemId, String itemName, boolean buy)
	{
		for (PaperOffer offer : offers)
		{
			if (offer.itemId == itemId && offer.buy == buy && !offer.isComplete())
			{
				offer.cancelled = true;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean collect()
	{
		Iterator<PaperOffer> iterator = offers.iterator();
		while (iterator.hasNext())
		{
			PaperOffer offer = iterator.next();
			if (!offer.cancelled && !offer.isComplete())
			{
				continue;
			}
			String key = key(offer.name);
			if (offer.buy)
			{
				inventory.merge(key, (long) offer.filled, Long::sum);
				coins += offer.escrow;
			}
			else
			{
				coins += offer.proceeds;
				long unsold = offer.total - offer.filled;
				if (unsold > 0)
				{
					inventory.merge(key, unsold, Long::sum);
				}
			}
			iterator.remove();
		}
		return true;
	}

	@Override
	public int inventoryCount(String itemName)
	{
		return (int) Math.min(Integer.MAX_VALUE, inventory.getOrDefault(key(itemName), 0L));
	}

	@Override
	public void tick()
	{
		long now = clock.getAsLong();
		double hours = Math.min(MAX_TICK_HOURS, Math.max(0L, now - lastTick) / 3_600_000D);
		lastTick = now;
		if (hours <= 0D)
		{
			return;
		}

		MarketSnapshot snapshot = snapshots.get();
		if (snapshot == null)
		{
			return;
		}
		for (PaperOffer offer : offers)
		{
			if (offer.cancelled || offer.isComplete())
			{
				continue;
			}
			PricePoint price = snapshot.price(offer.itemId);
			HourlyPoint hour = snapshot.hour(offer.itemId);
			if (price == null || hour == null)
			{
				continue;
			}

			long volume;
			if (offer.buy)
			{
				if (!price.hasLow() || price.getLow() > offer.price)
				{
					continue;
				}
				volume = hour.getLowPriceVolume();
			}
			else
			{
				if (!price.hasHigh() || price.getHigh() < offer.price)
				{
					continue;
				}
				volume = hour.getHighPriceVolume();
			}

			offer.carry += volume * volumeShare * hours;
			int units = (int) Math.min(Math.floor(offer.carry), (long) offer.total - offer.filled);
			if (units <= 0)
			{
				continue;
			}
			offer.carry -= units;
			offer.filled += units;
			if (offer.buy)
			{
				offer.escrow -= (long) offer.price * units;
			}
			else
			{
				offer.proceeds += (long) units * (offer.price - MarketAnalyzer.tax(offer.price));
			}
		}
	}

	private static String key(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
	}
}
