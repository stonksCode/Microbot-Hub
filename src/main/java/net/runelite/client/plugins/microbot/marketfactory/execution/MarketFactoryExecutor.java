package net.runelite.client.plugins.microbot.marketfactory.execution;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.marketfactory.CapitalSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.MarketFactoryConfig;
import net.runelite.client.plugins.microbot.marketfactory.trade.GeGateway;
import net.runelite.client.plugins.microbot.marketfactory.trade.OfferView;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Live Grand Exchange access. Offer progress is read from the client's offer array, which the
 * server keeps current even while the GE window is closed, so the window is only opened when
 * an offer has to be placed, cancelled or collected.
 *
 * <p>Runs on the script thread. Never call it from the client thread: the underlying
 * {@code Rs2GrandExchange} helpers sleep.
 *
 * <p>Money is read from the inventory only. Keep the trading stack in the inventory; banked
 * coins are not withdrawn.
 */
@Slf4j
public class MarketFactoryExecutor implements GeGateway
{
	private static final int NEAR_EXCHANGE_TILES = 12;

	private final MarketFactoryConfig config;

	public MarketFactoryExecutor(MarketFactoryConfig config)
	{
		this.config = config;
	}

	@Override
	public boolean isPaper()
	{
		return false;
	}

	@Override
	public boolean isReady()
	{
		if (!Microbot.isLoggedIn())
		{
			return false;
		}
		if (config.useBreakHandler())
		{
			try
			{
				if (BreakHandlerScript.isBreakActive())
				{
					return false;
				}
			}
			catch (Throwable ignored)
			{
				// Break handler not available; carry on.
			}
		}
		return true;
	}

	@Override
	public long freeCoins()
	{
		return CapitalSnapshot.detect().getInventoryCoins();
	}

	@Override
	public int freeSlots()
	{
		return Rs2GrandExchange.getAvailableSlotsCount();
	}

	@Override
	public boolean place(boolean buy, int itemId, String itemName, int price, int quantity)
	{
		if (!ensureAtExchange())
		{
			return false;
		}
		GrandExchangeRequest request = GrandExchangeRequest.builder()
			.action(buy ? GrandExchangeAction.BUY : GrandExchangeAction.SELL)
			.itemName(itemName)
			.exact(true)
			.price(price)
			.quantity(quantity)
			.build();
		boolean placed = Rs2GrandExchange.processOffer(request);
		if (!placed)
		{
			log.warn("Could not place {} offer for {} x{} @ {}", buy ? "buy" : "sell", itemName, quantity, price);
		}
		return placed;
	}

	@Override
	public OfferView offer(int itemId, boolean buy)
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}
		for (int i = 0; i < offers.length; i++)
		{
			GrandExchangeOffer offer = offers[i];
			if (offer == null || offer.getItemId() != itemId)
			{
				continue;
			}

			boolean isBuy = false;
			OfferView.Status status = null;
			switch (offer.getState())
			{
				case BUYING:
					isBuy = true;
					status = OfferView.Status.ACTIVE;
					break;
				case BOUGHT:
					isBuy = true;
					status = OfferView.Status.COMPLETE;
					break;
				case CANCELLED_BUY:
					isBuy = true;
					status = OfferView.Status.CANCELLED;
					break;
				case SELLING:
					status = OfferView.Status.ACTIVE;
					break;
				case SOLD:
					status = OfferView.Status.COMPLETE;
					break;
				case CANCELLED_SELL:
					status = OfferView.Status.CANCELLED;
					break;
				default:
					continue;
			}
			if (isBuy != buy)
			{
				continue;
			}
			return new OfferView(i, itemId, isBuy, status, (int) offer.getQuantitySold(),
				(int) offer.getTotalQuantity(), (int) offer.getPrice());
		}
		return null;
	}

	@Override
	public boolean abort(int itemId, String itemName, boolean buy)
	{
		if (!ensureAtExchange())
		{
			return false;
		}
		// Aborts and then collects, so the offer is gone from the array shortly afterwards.
		boolean done = Rs2GrandExchange.abortOffer(itemName, false);
		sleep(600, 1000);
		return done;
	}

	@Override
	public boolean collect()
	{
		if (!ensureAtExchange())
		{
			return false;
		}
		boolean done = Rs2GrandExchange.collectAll(false);
		sleep(700, 1100);
		return done;
	}

	@Override
	public int inventoryCount(String itemName)
	{
		return (int) Math.min(Integer.MAX_VALUE, Rs2Inventory.items()
			.filter(item -> item.getName() != null && item.getName().equalsIgnoreCase(itemName))
			.mapToLong(item -> item.getQuantity())
			.sum());
	}

	@Override
	public void idle()
	{
		try
		{
			if (Rs2GrandExchange.isOpen())
			{
				Rs2GrandExchange.closeExchange();
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not close the Grand Exchange", ex);
		}
	}

	/** Walks to the Grand Exchange when too far away; the offer helpers open the window. */
	private boolean ensureAtExchange()
	{
		if (Rs2GrandExchange.isOpen())
		{
			return true;
		}
		WorldPoint exchange = BankLocation.GRAND_EXCHANGE.getWorldPoint();
		WorldPoint here = null;
		try
		{
			here = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getLocalPlayer().getWorldLocation())
				.orElse(null);
		}
		catch (RuntimeException ex)
		{
			log.debug("Could not read the player location", ex);
		}
		if (here == null)
		{
			return false;
		}
		if (here.distanceTo(exchange) > NEAR_EXCHANGE_TILES)
		{
			Rs2Walker.walkTo(exchange);
			return false;
		}
		return true;
	}
}
