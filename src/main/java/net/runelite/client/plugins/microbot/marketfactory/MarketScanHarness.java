package net.runelite.client.plugins.microbot.marketfactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.marketfactory.enums.CandidateType;
import net.runelite.client.plugins.microbot.marketfactory.enums.MembershipMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.enums.RouteMode;

/**
 * Offline harness for exercising the scanner without a RuneLite client. It loads nothing
 * from the game - it just feeds a synthetic {@link net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot}
 * and synthetic capital/skills into {@link CandidateEngine}.
 *
 * <p>Run with: {@code ./gradlew run} or simply {@code java ... MarketScanHarness} once the
 * classpath is built. The goal is to iterate on risk thresholds and recipes using real
 * wiki-shaped data before wiring live GE calls.
 */
public final class MarketScanHarness
{
	private MarketScanHarness()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static void main(String[] args) throws Exception
	{
		MarketDataService dataService = new MarketDataService();
		// Pull a fresh snapshot from the wiki so the harness matches live market shape.
		dataService.refreshIfStale(60_000, 86_400_000, 600_000);
		var snapshot = dataService.currentSnapshot();

		RiskProfile risk = RiskProfile.preset(RiskLevel.BALANCED);
		ScanContext context = ScanContext.builder()
			.snapshot(snapshot)
			.risk(risk)
			.capital(10_000_000L)
			.account(true, true)
			.membershipMode(MembershipMode.AUTO)
			.routeMode(RouteMode.BEST_OF_BOTH)
			.deepScanLimit(40)
			.historyProvider(dataService::history)
			.skillSource(skill -> 99)
			.now(System.currentTimeMillis())
			.build();

		CandidateEngine engine = new CandidateEngine();
		ScanReport report = engine.scan(context);

		System.out.println("Market Factory - Scan Harness");
		System.out.println("=============================");
		System.out.println(report.getNote());
		System.out.println();

		printSection("TOP FLIPS", report.getFlips());
		printSection("TOP PROCESSES", report.getProcesses());

		writeCsv(report);
	}

	private static void printSection(String title, List<Candidate> candidates)
	{
		System.out.println(title);
		System.out.println("-".repeat(title.length()));
		if (candidates.isEmpty())
		{
			System.out.println("(none viable)");
			System.out.println();
			return;
		}

		int count = Math.min(20, candidates.size());
		for (int i = 0; i < count; i++)
		{
			Candidate c = candidates.get(i);
			System.out.printf("%2d. %-30s qty=%4d cap=%s profit=%s gp/h=%s roi=%.1f%% score=%d hold=%dm%n",
				i + 1,
				truncate(c.getLabel(), 30),
				c.getQuantity(),
				format(c.getCapital()),
				format(c.getProfit()),
				format((long) c.getGpPerHour()),
				c.getRoiPercent(),
				(long) c.getScore(),
				c.getMaxHoldMinutes());
			if (!c.getNotes().isEmpty())
			{
				System.out.println("     " + String.join(" | ", c.getNotes()));
			}
			if (!c.isViable() && !c.getRejects().isEmpty())
			{
				System.out.println("     " + c.rejectionSummary());
			}
		}
		System.out.println();
	}

	private static void writeCsv(ScanReport report) throws IOException
	{
		Path path = Paths.get("market-scan-harness.csv");
		try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
		{
			writer.write("type,label,itemId,quantity,capital,revenue,profit,roiPercent,fillHours,gpPerHour,score,confidence,stability,maxHoldMinutes");
			writer.newLine();
			write(writer, CandidateType.FLIP, report.getFlips());
			write(writer, CandidateType.PROCESS, report.getProcesses());
		}
		System.out.println("Wrote " + path.toAbsolutePath());
	}

	private static void write(BufferedWriter writer, CandidateType type, List<Candidate> candidates) throws IOException
	{
		for (Candidate c : candidates)
		{
			writer.write(String.format(java.util.Locale.ROOT,
				"%s,\"%s\",%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%d",
				type, c.getLabel().replace("\"", "\"\""), c.getItemId(), c.getQuantity(),
				c.getCapital(), c.getRevenue(), c.getProfit(), c.getRoiPercent(),
				c.getFillHours(), c.getGpPerHour(), c.getScore(), c.getConfidence(),
				c.getStability(), c.getMaxHoldMinutes()));
			writer.newLine();
		}
	}

	private static String truncate(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		if (value.length() <= max)
		{
			return value;
		}
		return value.substring(0, max - 3) + "...";
	}

	private static String format(long value)
	{
		if (value >= 1_000_000_000)
		{
			return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000D);
		}
		if (value >= 1_000_000)
		{
			return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000D);
		}
		if (value >= 1000)
		{
			return String.format(java.util.Locale.ROOT, "%.1fK", value / 1000D);
		}
		return String.valueOf(value);
	}
}
