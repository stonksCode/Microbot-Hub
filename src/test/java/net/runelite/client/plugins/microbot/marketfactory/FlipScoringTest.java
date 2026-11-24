package net.runelite.client.plugins.microbot.marketfactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;
import net.runelite.client.plugins.microbot.marketfactory.trade.FlipPricing;
import org.junit.jupiter.api.Test;

class FlipScoringTest
{
	private static final long NOW = TestMarket.NOW;

	private static Candidate flip(ScanReport report, String label)
	{
		for (Candidate candidate : report.getFlips())
		{
			if (candidate.getLabel().equals(label))
			{
				return candidate;
			}
		}
		return null;
	}

	@Test
	void taxIsTwoPercentRoundedDownWithFloorAndCap()
	{
		assertEquals(0, MarketAnalyzer.tax(49));
		assertEquals(1, MarketAnalyzer.tax(50));
		assertEquals(214, MarketAnalyzer.tax(10_746));
		assertEquals(5_000_000, MarketAnalyzer.tax(250_000_000));
	}

	@Test
	void taxDoesNotOverflowForVeryExpensiveItems()
	{
		assertEquals(5_000_000, MarketAnalyzer.tax(1_500_000_000));
		assertEquals(5_000_000, MarketAnalyzer.tax(Integer.MAX_VALUE));
	}

	@Test
	void buyLimitStopsGpPerHourBeingCountedAsRepeatable()
	{
		TestMarket market = new TestMarket()
			.add(1, "Alpha", 10_000, 10_000, 10_800, 4000)
			.add(2, "Beta", 100, 10_000, 10_800, 4000);
		ScanReport report = new CandidateEngine().scan(market.context(20_000_000L, NOW, 60));

		Candidate alpha = flip(report, "Alpha");
		Candidate beta = flip(report, "Beta");
		assertNotNull(alpha);
		assertNotNull(beta);

		// Volume allows 400 units per leg; Beta's limit allows only 100.
		assertEquals(400, alpha.getQuantity());
		assertEquals(100, beta.getQuantity());

		// 200 units/h per side: Alpha needs 2h to buy and 2h to sell. Beta fills in 1h but its
		// limit does not reset for 4h, so the cycle is 4h, not 1h.
		assertEquals(4D, alpha.getCycleHours(), 0.001D);
		assertEquals(4D, beta.getCycleHours(), 0.001D);

		// Same margin and cycle time, a quarter of the units, so a quarter of the gp/h.
		assertEquals(0.25D, beta.getGpPerHour() / alpha.getGpPerHour(), 0.001D);
	}

	@Test
	void flipsCarryTheirExecutionPrices()
	{
		TestMarket market = new TestMarket().add(1, "Alpha", 10_000, 10_000, 10_800, 4000);
		Candidate alpha = flip(new CandidateEngine().scan(market.context(20_000_000L, NOW, 60)), "Alpha");

		assertNotNull(alpha);
		assertTrue(alpha.getBuyPrice() > 0);
		assertTrue(alpha.getSellPrice() > alpha.getBuyPrice());
		assertEquals(10_000, alpha.getBuyLimit());
	}

	@Test
	void shortlistRanksOnNetLimitCappedMarginNotGrossMargin()
	{
		// "Gross" has the wider spread and the same volume, so a gross-margin ranking picks it.
		// Its buy limit of 100 means it cannot repeat that margin, so Alpha must win the only slot.
		TestMarket market = new TestMarket()
			.add(1, "Alpha", 10_000, 10_000, 10_800, 4000)
			.add(2, "Gross", 100, 10_000, 11_000, 4000);
		ScanReport report = new CandidateEngine().scan(market.context(20_000_000L, NOW, 1));

		assertEquals(1, report.getItemsDeepScanned());
		assertEquals(1, report.getFlips().size());
		assertEquals("Alpha", report.getFlips().get(0).getLabel());
	}

	@Test
	void freshPrintsTightenBothSidesOfThePlan()
	{
		TestMarket market = new TestMarket().add(1, "Alpha", 10_000, 10_000, 10_800, 4000);
		Candidate alpha = flip(new CandidateEngine().scan(market.context(20_000_000L, NOW, 60)), "Alpha");
		assertNotNull(alpha);

		PricePoint latest = market.snapshot(NOW).price(1);
		FlipPricing.Plan plan = FlipPricing.plan(alpha, latest, RiskProfile.preset(RiskLevel.BALANCED), NOW);

		assertNotNull(plan);
		assertEquals(10_001, plan.getBuyPrice());
		assertEquals(10_799, plan.getSellPrice());
		assertEquals(10_799 - 10_001 - 215, plan.getNetMargin());
	}

	@Test
	void stalePrintsFallBackToTheScannedPrices()
	{
		TestMarket market = new TestMarket().add(1, "Alpha", 10_000, 10_000, 10_800, 4000);
		Candidate alpha = flip(new CandidateEngine().scan(market.context(20_000_000L, NOW, 60)), "Alpha");
		assertNotNull(alpha);

		PricePoint latest = market.snapshot(NOW).price(1);
		long threeHoursLater = NOW + 3L * 3_600_000L;
		FlipPricing.Plan plan = FlipPricing.plan(alpha, latest, RiskProfile.preset(RiskLevel.BALANCED),
			threeHoursLater);

		assertNotNull(plan);
		assertEquals(alpha.getBuyPrice(), plan.getBuyPrice());
		assertEquals(alpha.getSellPrice(), plan.getSellPrice());
	}

	@Test
	void flipIsDroppedWhenTheFreshSpreadHasCollapsed()
	{
		TestMarket market = new TestMarket().add(1, "Alpha", 10_000, 10_000, 10_800, 4000);
		Candidate alpha = flip(new CandidateEngine().scan(market.context(20_000_000L, NOW, 60)), "Alpha");
		assertNotNull(alpha);

		// The spread has narrowed to 300 gp: after tax that is under the 2% ROI floor.
		long nowSeconds = NOW / 1000L;
		PricePoint narrowed = new PricePoint(10_300, 10_000, nowSeconds - 60, nowSeconds - 60);
		assertNull(FlipPricing.plan(alpha, narrowed, RiskProfile.preset(RiskLevel.BALANCED), NOW));
	}

	@Test
	void presetsKeepTheirOwnVolumeShare()
	{
		assertEquals(2.0D, RiskProfile.preset(RiskLevel.CONSERVATIVE).getParticipationCapPercent(), 0.0001D);
		assertEquals(5.0D, RiskProfile.preset(RiskLevel.BALANCED).getParticipationCapPercent(), 0.0001D);
		assertEquals(10.0D, RiskProfile.preset(RiskLevel.AGGRESSIVE).getParticipationCapPercent(), 0.0001D);
	}
}
