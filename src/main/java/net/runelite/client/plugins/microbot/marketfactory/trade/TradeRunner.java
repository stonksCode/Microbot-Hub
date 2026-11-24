package net.runelite.client.plugins.microbot.marketfactory.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.marketfactory.Candidate;
import net.runelite.client.plugins.microbot.marketfactory.CandidateEngine;
import net.runelite.client.plugins.microbot.marketfactory.MarketAnalyzer;
import net.runelite.client.plugins.microbot.marketfactory.ScanContext;
import net.runelite.client.plugins.microbot.marketfactory.ScanReport;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;
import net.runelite.client.plugins.microbot.marketfactory.trade.ActiveTrade.Phase;

/**
 * The flip state machine. Each {@link #step(ScanReport)} advances open trades by at most one
 * Grand Exchange action, then, if there is room, starts a new trade from the latest scan.
 *
 * <pre>
 *   BUYING -> COLLECTING_BUY -> SELLING -> COLLECTING_SELL -> done
 * </pre>
 *
 * <p>Offer progress is read from the gateway (free, no UI), so the GE is only opened when
 * something needs placing, cancelling or collecting. The inventory is the source of truth
 * for how many units were really bought and sold, which keeps the accounting right when an
 * offer disappears after a collect or an abort.
 *
 * <p>Sells are repriced toward break-even in steps, never below it unless the stop-loss or
 * the hold limit forces a liquidation at the last instant-sell price.
 */
@Slf4j
public final class TradeRunner
{
	private static final long COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(30);
	private static final long MAX_REPORT_AGE_MILLIS = TimeUnit.MINUTES.toMillis(15);
	private static final long PLACEMENT_GRACE_MILLIS = TimeUnit.SECONDS.toMillis(6);
	private static final long START_RETRY_MILLIS = TimeUnit.SECONDS.toMillis(20);
	private static final long SLOT_WAIT_LIMIT_MILLIS = TimeUnit.MINUTES.toMillis(10);
	private static final int CANDIDATES_PER_ATTEMPT = 6;
	private static final int MAX_PLACE_ATTEMPTS = 3;
	private static final int MAX_COLLECT_ATTEMPTS = 6;
	private static final int MAX_ERRORS = 10;
	private static final double COIN_SAFETY = 0.98D;

	private final GeGateway gateway;
	private final CandidateEngine engine;
	private final LongFunction<ScanContext> contexts;
	private final Supplier<MarketSnapshot> snapshots;
	private final IntSupplier maxConcurrent;
	private final LongSupplier clock;
	private final TradeManager manager;
	private final Map<Integer, Long> cooldowns = new HashMap<>();
	private volatile boolean acceptNew = true;
	private long nextStartAttempt;

	/**
	 * @param contexts builds a fresh scan context for a given amount of free coins
	 * @param snapshots latest market data, used for stop-loss and repricing decisions
	 */
	public TradeRunner(GeGateway gateway, CandidateEngine engine, LongFunction<ScanContext> contexts,
		IntSupplier maxConcurrent, Supplier<MarketSnapshot> snapshots, LongSupplier clock,
		TradeManager manager)
	{
		this.gateway = gateway;
		this.engine = engine;
		this.contexts = contexts;
		this.maxConcurrent = maxConcurrent;
		this.snapshots = snapshots;
		this.clock = clock;
		this.manager = manager;
	}

	public TradeManager getManager()
	{
		return manager;
	}

	public GeGateway getGateway()
	{
		return gateway;
	}

	/** When false, open trades are finished but no new ones are started. */
	public void setAcceptNew(boolean acceptNew)
	{
		this.acceptNew = acceptNew;
	}

	public void step(ScanReport report)
	{
		gateway.tick();
		if (!gateway.isReady())
		{
			return;
		}

		long now = clock.getAsLong();
		boolean acted = false;
		for (ActiveTrade trade : manager.activeTrades())
		{
			try
			{
				if (advance(trade, now))
				{
					acted = true;
					break;
				}
			}
			catch (RuntimeException ex)
			{
				trade.errors++;
				trade.status = "Error: " + ex.getClass().getSimpleName();
				log.warn("Trade step failed for {} ({} errors)", trade.getItemName(), trade.errors, ex);
				if (trade.errors >= MAX_ERRORS)
				{
					finish(trade, false, "too many errors", now);
				}
				acted = true;
				break;
			}
		}

		if (!acted && acceptNew && report != null)
		{
			acted = startNext(report, now);
		}
		if (!acted)
		{
			gateway.idle();
		}
	}

	private boolean advance(ActiveTrade trade, long now)
	{
		switch (trade.phase)
		{
			case BUYING:
				return advanceBuying(trade, now);
			case COLLECTING_BUY:
				return advanceCollectBuy(trade, now);
			case SELLING:
				return advanceSelling(trade, now);
			case COLLECTING_SELL:
				return advanceCollectSell(trade, now);
			default:
				return false;
		}
	}

	// ------------------------------------------------------------------
	// Buying
	// ------------------------------------------------------------------

	private boolean advanceBuying(ActiveTrade t, long now)
	{
		OfferView offer = gateway.offer(t.getItemId(), true);
		if (offer == null)
		{
			if (!t.offerPlaced)
			{
				return placeBuy(t, now);
			}
			if (now - t.lastPlacedAt < PLACEMENT_GRACE_MILLIS)
			{
				return false;
			}
			// The offer is gone: it was collected, or cancelled and collected in one go.
			return settleBuy(t, now);
		}

		t.bought = offer.getFilled();
		if (offer.isComplete() || offer.isCancelled())
		{
			t.status = "Buy finished";
			t.setPhase(Phase.COLLECTING_BUY, now);
			return false;
		}

		t.status = "Buying " + offer.getFilled() + "/" + offer.getTotal() + " @ " + offer.getPrice();
		if (now - t.phaseStartedAt > t.buyPatienceMillis)
		{
			t.status = "Buy timed out, cancelling";
			if (!gateway.abort(t.getItemId(), t.getItemName(), true) && ++t.collectAttempts > MAX_COLLECT_ATTEMPTS)
			{
				t.phaseStartedAt = now;
				t.collectAttempts = 0;
			}
			return true;
		}
		return false;
	}

	private boolean placeBuy(ActiveTrade t, long now)
	{
		if (now - t.getStartedAt() > SLOT_WAIT_LIMIT_MILLIS)
		{
			return failQuietly(t, "could not place the buy offer in time", now);
		}
		long cost = (long) t.plannedBuyPrice * t.plannedQuantity;
		if (gateway.freeCoins() < cost)
		{
			return failQuietly(t, "not enough coins for the buy", now);
		}
		if (gateway.freeSlots() <= 0)
		{
			t.status = "Waiting for a free GE slot";
			return false;
		}

		t.status = "Placing buy offer";
		if (gateway.place(true, t.getItemId(), t.getItemName(), t.plannedBuyPrice, t.plannedQuantity))
		{
			t.offerPlaced = true;
			t.lastPlacedAt = now;
			t.phaseStartedAt = now;
			t.status = "Buy offer placed";
			return true;
		}
		if (++t.placeAttempts >= MAX_PLACE_ATTEMPTS)
		{
			return failQuietly(t, "could not place the buy offer", now);
		}
		return true;
	}

	private boolean advanceCollectBuy(ActiveTrade t, long now)
	{
		OfferView offer = gateway.offer(t.getItemId(), true);
		if (offer == null)
		{
			return settleBuy(t, now);
		}
		if (++t.collectAttempts > MAX_COLLECT_ATTEMPTS)
		{
			return failQuietly(t, "could not collect the bought items", now);
		}
		t.status = "Collecting bought items";
		gateway.collect();
		return true;
	}

	/** The buy offer is gone; whatever is in the inventory above the baseline was bought. */
	private boolean settleBuy(ActiveTrade t, long now)
	{
		int held = remainingUnits(t);
		if (held <= 0)
		{
			return failQuietly(t, "buy did not fill", now);
		}
		t.bought = held;
		t.spent = (long) held * t.plannedBuyPrice;
		t.sellPrice = t.plannedSellPrice;
		t.offerPlaced = false;
		t.status = "Bought " + held;
		t.setPhase(Phase.SELLING, now);
		return false;
	}

	// ------------------------------------------------------------------
	// Selling
	// ------------------------------------------------------------------

	private boolean advanceSelling(ActiveTrade t, long now)
	{
		OfferView offer = gateway.offer(t.getItemId(), false);
		if (offer == null)
		{
			if (!t.offerPlaced)
			{
				return placeSell(t, now);
			}
			if (now - t.lastPlacedAt < PLACEMENT_GRACE_MILLIS)
			{
				return false;
			}
			return settleSell(t, now);
		}

		if (offer.isComplete() || offer.isCancelled())
		{
			t.status = "Sell finished";
			t.setPhase(Phase.COLLECTING_SELL, now);
			return false;
		}

		t.status = "Selling " + offer.getFilled() + "/" + offer.getTotal() + " @ " + offer.getPrice();
		return manageActiveSell(t, offer, now);
	}

	private boolean placeSell(ActiveTrade t, long now)
	{
		int remaining = remainingUnits(t);
		if (remaining <= 0)
		{
			return settleSell(t, now);
		}
		if (gateway.freeSlots() <= 0)
		{
			t.status = "Waiting for a free GE slot";
			return false;
		}

		t.status = "Placing sell offer";
		if (gateway.place(false, t.getItemId(), t.getItemName(), t.sellPrice, remaining))
		{
			t.offerPlaced = true;
			t.lastPlacedAt = now;
			t.phaseStartedAt = now;
			t.status = "Sell offer placed";
			return true;
		}
		if (++t.placeAttempts >= MAX_PLACE_ATTEMPTS * 2)
		{
			log.warn("Could not list {} x{}; it is still in the inventory", t.getItemName(), remaining);
			return failQuietly(t, "could not place the sell offer (items held)", now);
		}
		return true;
	}

	private boolean manageActiveSell(ActiveTrade t, OfferView offer, long now)
	{
		if (t.liquidating)
		{
			return false;
		}

		long age = now - t.phaseStartedAt;
		boolean holdExpired = now >= t.maxHoldUntil && age > t.sellStepMillis;
		if (stopLossHit(t) || holdExpired)
		{
			return startReprice(t, liquidationPrice(t), true, now);
		}

		if (age > t.sellStepMillis && t.repriceCount < t.risk.getRepriceSteps())
		{
			int target = glidePrice(t);
			t.repriceCount++;
			if (target < offer.getPrice())
			{
				return startReprice(t, target, false, now);
			}
			t.phaseStartedAt = now;
		}
		return false;
	}

	private boolean startReprice(ActiveTrade t, int price, boolean liquidate, long now)
	{
		t.pendingSellPrice = Math.max(1, price);
		t.liquidating = liquidate;
		t.status = liquidate ? "Liquidating at " + t.pendingSellPrice : "Repricing sell to " + t.pendingSellPrice;
		if (!gateway.abort(t.getItemId(), t.getItemName(), false) && ++t.collectAttempts > MAX_COLLECT_ATTEMPTS)
		{
			// The cancel keeps failing; leave the offer as it is and try again later.
			t.pendingSellPrice = 0;
			t.liquidating = false;
			t.phaseStartedAt = now;
			t.collectAttempts = 0;
		}
		return true;
	}

	private boolean advanceCollectSell(ActiveTrade t, long now)
	{
		OfferView offer = gateway.offer(t.getItemId(), false);
		if (offer == null)
		{
			return settleSell(t, now);
		}
		if (++t.collectAttempts > MAX_COLLECT_ATTEMPTS)
		{
			return failQuietly(t, "could not collect the sale", now);
		}
		t.status = "Collecting sale";
		gateway.collect();
		return true;
	}

	/**
	 * The sell offer is gone. Units still in the inventory were not sold: relist them (at the
	 * pending repriced price if there is one). Units no longer there were sold at the price
	 * of the offer that just ended.
	 */
	private boolean settleSell(ActiveTrade t, long now)
	{
		int remaining = remainingUnits(t);
		int soldNow = Math.max(0, t.bought - remaining);
		if (soldNow > t.sold)
		{
			long netPerUnit = t.sellPrice - MarketAnalyzer.tax(t.sellPrice);
			t.revenue += (soldNow - t.sold) * netPerUnit;
			t.sold = soldNow;
		}

		if (remaining <= 0)
		{
			finish(t, true, "sold " + t.sold, now);
			return false;
		}

		if (t.pendingSellPrice > 0)
		{
			t.sellPrice = t.pendingSellPrice;
			t.pendingSellPrice = 0;
		}
		t.offerPlaced = false;
		t.status = "Relisting " + remaining + " @ " + t.sellPrice;
		t.setPhase(Phase.SELLING, now);
		return false;
	}

	// ------------------------------------------------------------------
	// Pricing helpers
	// ------------------------------------------------------------------

	private boolean stopLossHit(ActiveTrade t)
	{
		double stop = t.risk.getStopLossPercent();
		if (stop <= 0D)
		{
			return false;
		}
		PricePoint latest = latest(t.getItemId());
		return latest != null && latest.hasHigh()
			&& latest.getHigh() < t.averageBuyPrice() * (1D - stop / 100D);
	}

	private int liquidationPrice(ActiveTrade t)
	{
		PricePoint latest = latest(t.getItemId());
		if (latest != null && latest.hasLow())
		{
			return Math.max(1, latest.getLow());
		}
		double stop = Math.max(0D, t.risk.getStopLossPercent());
		return Math.max(1, (int) Math.floor(t.averageBuyPrice() * (1D - stop / 100D)));
	}

	/** Next ask when repricing: a share of the remaining distance down to break-even. */
	private static int glidePrice(ActiveTrade t)
	{
		int breakeven = breakevenSell(t.averageBuyPrice());
		if (t.sellPrice <= breakeven)
		{
			return t.sellPrice;
		}
		int steps = Math.max(1, t.risk.getRepriceSteps());
		double fraction = (t.repriceCount + 1D) / (steps + 1D);
		int target = (int) Math.floor(t.sellPrice - (t.sellPrice - breakeven) * fraction);
		return Math.max(breakeven, target);
	}

	/** Lowest ask that still returns the average buy price after tax. */
	static int breakevenSell(int averageBuy)
	{
		// Tax is 2% rounded down and capped, so the answer sits within a few gp of avg / 0.98
		// (or avg + the cap for very expensive items). Start just below that and step up.
		long start = Math.min((long) Math.floor(averageBuy / 0.98D) - 2L, (long) averageBuy + 5_000_000L - 2L);
		long price = Math.max(1L, start);
		for (int i = 0; i < 64 && price - MarketAnalyzer.tax((int) Math.min(price, Integer.MAX_VALUE)) < averageBuy; i++)
		{
			price++;
		}
		return (int) Math.min(price, Integer.MAX_VALUE);
	}

	private PricePoint latest(int itemId)
	{
		MarketSnapshot snapshot = snapshots.get();
		return snapshot == null ? null : snapshot.price(itemId);
	}

	private int remainingUnits(ActiveTrade t)
	{
		return Math.max(0, gateway.inventoryCount(t.getItemName()) - t.baselineInventory);
	}

	// ------------------------------------------------------------------
	// Starting and finishing
	// ------------------------------------------------------------------

	private boolean startNext(ScanReport report, long now)
	{
		if (now < nextStartAttempt)
		{
			return false;
		}
		if (manager.activeCount() >= Math.max(1, maxConcurrent.getAsInt()) || gateway.freeSlots() <= 0)
		{
			return false;
		}
		if (report.getFlips().isEmpty() || report.getGeneratedAt() <= 0
			|| now - report.getGeneratedAt() > MAX_REPORT_AGE_MILLIS)
		{
			return false;
		}
		long coins = gateway.freeCoins();
		if (coins <= 0)
		{
			return false;
		}

		ScanContext context = contexts.apply(coins);
		MarketSnapshot snapshot = context.getSnapshot();
		int tried = 0;
		for (Candidate candidate : report.getFlips())
		{
			if (tried >= CANDIDATES_PER_ATTEMPT)
			{
				break;
			}
			int itemId = candidate.getItemId();
			if (manager.isActive(itemId) || onCooldown(itemId, now))
			{
				continue;
			}
			tried++;

			Candidate fresh = engine.reevaluateFlip(itemId, context);
			if (!fresh.isViable())
			{
				continue;
			}
			FlipPricing.Plan plan = FlipPricing.plan(fresh, snapshot.price(itemId), context.getRisk(),
				context.getNowMillis());
			if (plan == null)
			{
				continue;
			}
			long affordable = (long) (coins * COIN_SAFETY) / plan.getBuyPrice();
			int quantity = (int) Math.min(fresh.getQuantity(), affordable);
			if (quantity <= 0)
			{
				continue;
			}
			// A finished offer for this item that was never collected would be mistaken for ours.
			if (gateway.offer(itemId, true) != null || gateway.offer(itemId, false) != null)
			{
				continue;
			}

			long roundTripMinutes = Math.max(1L, Math.round(fresh.getFillHours() * 60D));
			long buyPatience = TimeUnit.MINUTES.toMillis(clamp(roundTripMinutes, 10L, 90L));
			long sellStep = TimeUnit.MINUTES.toMillis(clamp(roundTripMinutes, 10L, 60L));
			long maxHold = TimeUnit.MINUTES.toMillis(Math.max(30L, fresh.getMaxHoldMinutes()));
			ActiveTrade trade = new ActiveTrade(UUID.randomUUID().toString().substring(0, 8), fresh,
				context.getRisk(), quantity, plan.getBuyPrice(), plan.getSellPrice(),
				(long) quantity * plan.getNetMargin(), gateway.inventoryCount(fresh.getLabel()),
				now, buyPatience, sellStep, now + maxHold);
			manager.add(trade);
			log.info("Starting flip {} x{} buy {} sell {} (net {} each, predicted {} gp)",
				fresh.getLabel(), quantity, plan.getBuyPrice(), plan.getSellPrice(),
				plan.getNetMargin(), trade.predictedProfit);
			return true;
		}

		nextStartAttempt = now + START_RETRY_MILLIS;
		return false;
	}

	private boolean onCooldown(int itemId, long now)
	{
		Long until = cooldowns.get(itemId);
		if (until == null)
		{
			return false;
		}
		if (now >= until)
		{
			cooldowns.remove(itemId);
			return false;
		}
		return true;
	}

	private void finish(ActiveTrade trade, boolean success, String outcome, long now)
	{
		manager.finish(trade, success, outcome, now);
		if (!success || trade.getRealizedProfit() < 0)
		{
			cooldowns.put(trade.getItemId(), now + COOLDOWN_MILLIS);
		}
		log.info("Trade {} {}: {} (predicted {} gp, realised {} gp)", trade.getItemName(),
			success ? "done" : "failed", outcome, trade.predictedProfit, trade.getRealizedProfit());
	}

	/** Fails the trade and reports that no Grand Exchange action was taken this step. */
	private boolean failQuietly(ActiveTrade trade, String reason, long now)
	{
		finish(trade, false, reason, now);
		return false;
	}

	private static long clamp(long value, long min, long max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
