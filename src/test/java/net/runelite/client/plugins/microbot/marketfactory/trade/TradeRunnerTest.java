package net.runelite.client.plugins.microbot.marketfactory.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.plugins.microbot.marketfactory.CandidateEngine;
import net.runelite.client.plugins.microbot.marketfactory.ScanReport;
import net.runelite.client.plugins.microbot.marketfactory.TestMarket;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import org.junit.jupiter.api.Test;

/**
 * Drives the whole flip state machine against the paper gateway with a hand-stepped clock.
 * Market: one item, 10,000 low / 10,800 high, 4,000 traded per hour on each side. At 5% volume
 * share an offer fills at 200 units/h.
 */
class TradeRunnerTest
{
	private static final long START_COINS = 20_000_000L;
	private static final long STEP_MILLIS = 60_000L;

	private final AtomicLong clock = new AtomicLong(TestMarket.NOW);
	private final TestMarket market = new TestMarket().add(1, "Alpha", 10_000, 10_000, 10_800, 4000);
	private final AtomicReference<MarketSnapshot> exchangeView =
		new AtomicReference<>(market.snapshot(TestMarket.NOW));
	private final CandidateEngine engine = new CandidateEngine();

	private PaperGeGateway gateway(long coins)
	{
		return new PaperGeGateway(exchangeView::get, clock::get, 0.05D, coins, 8);
	}

	private TradeRunner runner(PaperGeGateway gateway, TradeManager manager)
	{
		return new TradeRunner(gateway, engine, capital -> market.context(capital, clock.get(), 60),
			() -> 2, () -> market.snapshot(clock.get()), clock::get, manager);
	}

	private ScanReport scan()
	{
		return engine.scan(market.context(START_COINS, clock.get(), 60));
	}

	@Test
	void completesAProfitableFlipEndToEnd()
	{
		PaperGeGateway gateway = gateway(START_COINS);
		TradeManager manager = new TradeManager(true, null);
		TradeRunner runner = runner(gateway, manager);
		ScanReport report = scan();
		assertEquals(1, report.getFlips().size());

		for (int i = 0; i < 900 && manager.getCompletedCount() == 0 && manager.getFailedCount() == 0; i++)
		{
			runner.step(report);
			clock.addAndGet(STEP_MILLIS);
		}

		assertEquals(1, manager.getCompletedCount());
		assertEquals(0, manager.getFailedCount());
		assertEquals(0, manager.activeCount());
		assertTrue(manager.getRealizedProfit() > 0, "realised " + manager.getRealizedProfit());
		assertTrue(gateway.freeCoins() > START_COINS);
		assertEquals(0, gateway.inventoryCount("Alpha"));
	}

	@Test
	void cancelsABuyThatNeverFillsAndRefundsTheCoins()
	{
		PaperGeGateway gateway = gateway(START_COINS);
		TradeManager manager = new TradeManager(true, null);
		TradeRunner runner = runner(gateway, manager);
		ScanReport report = scan();

		// Let the runner plan the trade and place its bid, then move the market away from it.
		runner.step(report);
		clock.addAndGet(STEP_MILLIS);
		runner.step(report);
		clock.addAndGet(STEP_MILLIS);
		assertEquals(1, manager.activeCount());
		assertTrue(gateway.freeCoins() < START_COINS, "the bid should have escrowed coins");

		TestMarket moved = new TestMarket().add(1, "Alpha", 10_000, 12_000, 12_800, 4000);
		exchangeView.set(moved.snapshot(clock.get()));

		for (int i = 0; i < 400 && manager.getFailedCount() == 0; i++)
		{
			runner.step(report);
			clock.addAndGet(STEP_MILLIS);
		}

		assertEquals(1, manager.getFailedCount());
		assertEquals(0, manager.getCompletedCount());
		assertEquals(0, manager.activeCount());
		assertEquals(START_COINS, gateway.freeCoins());
		assertEquals(0L, manager.getRealizedProfit());
	}

	@Test
	void doesNotStartATradeItCannotAfford()
	{
		PaperGeGateway gateway = gateway(100L);
		TradeManager manager = new TradeManager(true, null);
		TradeRunner runner = runner(gateway, manager);

		runner.step(scan());

		assertEquals(0, manager.activeCount());
		assertEquals(100L, gateway.freeCoins());
	}

	@Test
	void doesNotStartNewTradesWhenDraining()
	{
		PaperGeGateway gateway = gateway(START_COINS);
		TradeManager manager = new TradeManager(true, null);
		TradeRunner runner = runner(gateway, manager);
		runner.setAcceptNew(false);

		runner.step(scan());

		assertEquals(0, manager.activeCount());
		assertEquals(START_COINS, gateway.freeCoins());
	}

	@Test
	void neverHoldsTwoTradesInTheSameItem()
	{
		PaperGeGateway gateway = gateway(START_COINS);
		TradeManager manager = new TradeManager(true, null);
		TradeRunner runner = runner(gateway, manager);
		ScanReport report = scan();

		for (int i = 0; i < 30; i++)
		{
			runner.step(report);
			clock.addAndGet(STEP_MILLIS);
		}

		assertEquals(1, manager.activeCount());
		assertFalse(manager.activeTrades().isEmpty());
	}

	@Test
	void breakevenAskStillReturnsTheBuyPriceAfterTax()
	{
		int breakeven = TradeRunner.breakevenSell(10_001);
		assertTrue(breakeven - net.runelite.client.plugins.microbot.marketfactory.MarketAnalyzer.tax(breakeven) >= 10_001);
		assertTrue(breakeven - 1
			- net.runelite.client.plugins.microbot.marketfactory.MarketAnalyzer.tax(breakeven - 1) < 10_001);
	}
}
