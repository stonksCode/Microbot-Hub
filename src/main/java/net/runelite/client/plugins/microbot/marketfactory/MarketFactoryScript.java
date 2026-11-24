package net.runelite.client.plugins.microbot.marketfactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.marketfactory.enums.CapitalSource;
import net.runelite.client.plugins.microbot.marketfactory.enums.ExecutionMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.execution.MarketFactoryExecutor;
import net.runelite.client.plugins.microbot.marketfactory.trade.GeGateway;
import net.runelite.client.plugins.microbot.marketfactory.trade.PaperGeGateway;
import net.runelite.client.plugins.microbot.marketfactory.trade.TradeManager;
import net.runelite.client.plugins.microbot.marketfactory.trade.TradeRunner;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Market Factory script. Two loops:
 *
 * <ul>
 *   <li>a scan loop on its own thread that pulls market data and ranks candidates;</li>
 *   <li>a trade loop on the standard Microbot script thread that drives {@link TradeRunner}
 *       against either the live Grand Exchange or a paper simulation.</li>
 * </ul>
 *
 * <p>Nothing here touches the game from the client thread, and trading never happens inside
 * an event subscriber, because the Grand Exchange helpers sleep.
 */
@Slf4j
public class MarketFactoryScript extends Script
{
	private static final int PAPER_SLOTS = 8;

	private final MarketFactoryPlugin plugin;
	private final MarketFactoryConfig config;
	private final MarketDataService dataService = new MarketDataService();
	private final CandidateEngine engine = new CandidateEngine();

	private final ScheduledExecutorService scanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "MarketFactory-Scan");
		thread.setDaemon(true);
		return thread;
	});

	private ScheduledFuture<?> scheduledScan;
	private final AtomicBoolean scanning = new AtomicBoolean(false);
	private volatile ScanReport lastReport = ScanReport.empty();
	private volatile CapitalSnapshot lastCapital;
	private volatile String scanStatus = "Idle";
	private volatile String tradeStatus = "";

	private volatile TradeRunner runner;
	private volatile ExecutionMode runnerMode;
	private volatile PaperGeGateway paperGateway;
	private MarketFactoryExecutor liveGateway;

	public MarketFactoryScript(MarketFactoryPlugin plugin, MarketFactoryConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	/** Starts the scan schedule and the trade loop. */
	public boolean start()
	{
		rescheduleScan();
		if (mainScheduledFuture == null || mainScheduledFuture.isDone())
		{
			mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
				try
				{
					if (!super.run())
					{
						return;
					}
					tradeTick();
				}
				catch (Exception ex)
				{
					log.error("Market Factory trade loop error", ex);
				}
			}, 2000, 1500, TimeUnit.MILLISECONDS);
		}
		return true;
	}

	@Override
	public void shutdown()
	{
		if (scheduledScan != null)
		{
			scheduledScan.cancel(true);
			scheduledScan = null;
		}
		scanExecutor.shutdownNow();
		try
		{
			scanExecutor.awaitTermination(3, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}

		TradeRunner current = runner;
		if (current != null && current.getManager().activeCount() > 0)
		{
			log.warn("Market Factory stopped with {} open trade(s); any live GE offers stay in the exchange",
				current.getManager().activeCount());
		}
		runner = null;
		super.shutdown();
		scheduledExecutorService.shutdownNow();
	}

	public void onConfigUpdated()
	{
		rescheduleScan();
	}

	public void requestScan()
	{
		scanExecutor.submit(() -> runScan("start"));
	}

	public void forceRescan()
	{
		if (config.useBreakHandler() && config.breakGuardEnabled() && isInBreak())
		{
			scanStatus = "On break";
			return;
		}
		scanExecutor.submit(() -> runScan("manual"));
	}

	public ScanReport getLastReport()
	{
		return lastReport;
	}

	public CapitalSnapshot getLastCapital()
	{
		return lastCapital;
	}

	public String getStatus()
	{
		String trades = tradeStatus;
		return trades.isEmpty() ? scanStatus : trades;
	}

	public String getScanStatus()
	{
		return scanStatus;
	}

	/** The open and finished trades of the current runner, or null before any trading mode ran. */
	public TradeManager getTradeManager()
	{
		TradeRunner current = runner;
		return current == null ? null : current.getManager();
	}

	public ExecutionMode getRunnerMode()
	{
		return runnerMode;
	}

	// ------------------------------------------------------------------
	// Trade loop
	// ------------------------------------------------------------------

	private void tradeTick()
	{
		ExecutionMode desired = config.executionMode();
		TradeRunner current = runner;

		boolean swap = current == null || (desired != runnerMode && current.getManager().activeCount() == 0);
		if (desired != ExecutionMode.OFF && swap)
		{
			current = buildRunner(desired);
		}
		if (current == null)
		{
			tradeStatus = "";
			return;
		}

		boolean accept = desired != ExecutionMode.OFF && desired == runnerMode;
		current.setAcceptNew(accept);
		current.step(lastReport);

		TradeManager manager = current.getManager();
		String mode = runnerMode == ExecutionMode.PAPER ? "Paper" : "Live";
		if (!accept && manager.activeCount() > 0)
		{
			mode += " (finishing)";
		}
		tradeStatus = String.format(Locale.ROOT, "%s: %d open, %s", mode, manager.activeCount(),
			formatGp(manager.getRealizedProfit()));
	}

	private TradeRunner buildRunner(ExecutionMode mode)
	{
		GeGateway gateway;
		if (mode == ExecutionMode.LIVE)
		{
			if (liveGateway == null)
			{
				liveGateway = new MarketFactoryExecutor(config);
			}
			gateway = liveGateway;
			paperGateway = null;
		}
		else
		{
			paperGateway = new PaperGeGateway(dataService::currentSnapshot, System::currentTimeMillis,
				buildRiskProfile().getParticipationCap(), Math.max(0L, config.gpLimit()), PAPER_SLOTS);
			gateway = paperGateway;
		}

		boolean paper = mode == ExecutionMode.PAPER;
		Path journal = Paths.get(System.getProperty("user.home"), "Microbot", "MarketFactory",
			paper ? "trades-paper.csv" : "trades-live.csv");
		TradeRunner created = new TradeRunner(gateway, engine, this::contextFor, config::maxConcurrentTrades,
			dataService::currentSnapshot, System::currentTimeMillis, new TradeManager(paper, journal));
		runner = created;
		runnerMode = mode;
		log.info("Market Factory trade runner started in {} mode", mode);
		return created;
	}

	/** Fresh scan context for a given amount of free coins; used right before a trade starts. */
	private ScanContext contextFor(long capital)
	{
		dataService.refreshIfStale(TimeUnit.SECONDS.toMillis(30), TimeUnit.HOURS.toMillis(24),
			TimeUnit.MINUTES.toMillis(10));
		long usable = capital;
		if (config.capitalSource() == CapitalSource.FIXED_GP)
		{
			usable = Math.min(usable, Math.max(0L, config.gpLimit()));
		}
		return buildContext(usable);
	}

	// ------------------------------------------------------------------
	// Scan loop
	// ------------------------------------------------------------------

	private void rescheduleScan()
	{
		if (scheduledScan != null)
		{
			scheduledScan.cancel(true);
			scheduledScan = null;
		}
		int seconds = config.scanIntervalSeconds();
		if (seconds <= 0)
		{
			scanStatus = "Manual scans only";
			return;
		}
		scheduledScan = scanExecutor.scheduleWithFixedDelay(() -> runScan("auto"), 1, seconds, TimeUnit.SECONDS);
		scanStatus = "Scanning every " + seconds + "s";
	}

	private void runScan(String reason)
	{
		if (!scanning.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			if (config.useBreakHandler() && config.breakGuardEnabled() && isInBreak())
			{
				scanStatus = "On break";
				return;
			}

			scanStatus = "Scanning...";
			dataService.refreshIfStale(TimeUnit.SECONDS.toMillis(60), TimeUnit.HOURS.toMillis(24),
				TimeUnit.MINUTES.toMillis(10));

			CapitalSnapshot capital = CapitalSnapshot.detect();
			lastCapital = capital;
			long tradingCapital = scanCapital(capital);
			if (tradingCapital <= 0)
			{
				scanStatus = "No capital";
				return;
			}

			ScanReport report = engine.scan(buildContext(tradingCapital));
			lastReport = report;
			scanStatus = String.format(Locale.ROOT, "Scan: %d flips, %d processes", report.getFlips().size(),
				report.getProcesses().size());
			log.info("Market Factory scan ({}): {}", reason, report.getNote());
			logTop("flips", report.getFlips());
			logTop("processes", report.getProcesses());
		}
		catch (Exception ex)
		{
			log.error("Market Factory scan failed", ex);
			scanStatus = "Error: " + ex.getClass().getSimpleName();
		}
		finally
		{
			scanning.set(false);
		}
	}

	/** Capital the scan should size trades for, matching what the executor can actually spend. */
	private long scanCapital(CapitalSnapshot capital)
	{
		ExecutionMode mode = config.executionMode();
		long total;
		if (mode == ExecutionMode.PAPER)
		{
			PaperGeGateway paper = paperGateway;
			total = paper != null ? paper.freeCoins() : Math.max(0L, config.gpLimit());
		}
		else if (mode == ExecutionMode.LIVE)
		{
			total = capital.getInventoryCoins();
		}
		else
		{
			total = capital.getTotalCoins();
		}

		if (config.capitalSource() == CapitalSource.FIXED_GP)
		{
			long limit = Math.max(0L, config.gpLimit());
			total = mode == ExecutionMode.OFF ? limit : Math.min(total, limit);
		}
		return Math.max(0L, total);
	}

	private ScanContext buildContext(long capital)
	{
		boolean membersAccount = true;
		boolean memberWorld = true;
		if (config.autoDetectMembership())
		{
			try
			{
				membersAccount = Rs2Player.isMember();
				memberWorld = Rs2Player.isInMemberWorld();
			}
			catch (Exception ex)
			{
				log.debug("Membership detection failed, assuming accessible", ex);
			}
		}

		return ScanContext.builder()
			.snapshot(dataService.currentSnapshot())
			.risk(buildRiskProfile())
			.capital(capital)
			.account(membersAccount, memberWorld)
			.membershipMode(config.membershipMode())
			.routeMode(config.routeMode())
			.watchlist(config.watchlist())
			.blacklist(config.blacklist())
			.watchlistOnly(config.watchlistOnly())
			.deepScanLimit(config.deepScanLimit())
			.historyProvider(dataService::history)
			.skillSource(MarketFactoryScript::skillLevel)
			.now(System.currentTimeMillis())
			.build();
	}

	private static void logTop(String label, java.util.List<Candidate> candidates)
	{
		if (candidates.isEmpty())
		{
			return;
		}
		StringBuilder line = new StringBuilder("Top ").append(label).append(": ");
		int count = Math.min(3, candidates.size());
		for (int i = 0; i < count; i++)
		{
			Candidate c = candidates.get(i);
			line.append(String.format(Locale.ROOT, "%d) %s qty=%d profit=%dk gp/h=%dk (%.1f%%) | ",
				i + 1, c.getLabel(), c.getQuantity(), c.getProfit() / 1000L,
				(long) (c.getGpPerHour() / 1000D), c.getRoiPercent()));
		}
		log.info(line.toString());
	}

	RiskProfile buildRiskProfile()
	{
		RiskLevel level = config.riskLevel();
		if (level == RiskLevel.CUSTOM)
		{
			return RiskProfile.builder(RiskLevel.CUSTOM)
				.minRoiPercent(config.minRoiPercent())
				.minProfitPerUnit(config.minProfitPerUnit())
				.minHourlyVolume(config.minHourlyVolume())
				.maxVolatilityCv(config.maxVolatilityCv() / 100D)
				.participationCapPercent(config.participationCapPercent())
				.maxFillHours(Math.max(3, config.maxFillHours()) / 60D)
				.priceBufferPercent(config.priceBufferPercent())
				.cashReservePercent(config.cashReservePercent())
				.maxHoldCapMinutes(config.maxHoldCapMinutes())
				.requireHistory(config.enforceHistory())
				.build();
		}

		RiskProfile preset = RiskProfile.preset(level);
		return RiskProfile.builder(preset.getLevel())
			.minRoiPercent(preset.getMinRoiPercent())
			.minProfitPerUnit(preset.getMinProfitPerUnit())
			.minHourlyVolume(preset.getMinHourlyVolume())
			.maxVolatilityCv(preset.getMaxVolatilityCv())
			.participationCapPercent(preset.getParticipationCapPercent())
			.maxFillHours(preset.getMaxFillHours())
			.maxPrintAgeMinutes(preset.getMaxPrintAgeMinutes())
			.maxSpreadRatio(preset.getMaxSpreadRatio())
			.maxDowntrendPercentPerHour(preset.getMaxDowntrendPercentPerHour())
			.requireHistory(preset.isRequireHistory() && config.enforceHistory())
			.minHistoryPoints(preset.getMinHistoryPoints())
			.priceBufferPercent(preset.getPriceBufferPercent())
			.repriceSteps(preset.getRepriceSteps())
			.stopLossPercent(preset.getStopLossPercent())
			.maxCapitalPerItemPercent(preset.getMaxCapitalPerItemPercent())
			.maxConcurrentPositions(preset.getMaxConcurrentPositions())
			.cashReservePercent(preset.getCashReservePercent())
			.maxHoldCapMinutes(preset.getMaxHoldCapMinutes())
			.allowOvernightHold(preset.isAllowOvernightHold())
			.build();
	}

	private static int skillLevel(Skill skill)
	{
		try
		{
			if (Microbot.getClient() == null)
			{
				return 99;
			}
			Integer level = Microbot.getClientThread().runOnClientThreadOptional(
				() -> Microbot.getClient().getRealSkillLevel(skill)
			).orElse(null);
			return level == null ? 1 : level;
		}
		catch (Exception ex)
		{
			return 99;
		}
	}

	private static boolean isInBreak()
	{
		try
		{
			return BreakHandlerScript.isBreakActive();
		}
		catch (Throwable ex)
		{
			return false;
		}
	}

	static String formatGp(long gp)
	{
		long abs = Math.abs(gp);
		String sign = gp < 0 ? "-" : "";
		if (abs >= 1_000_000_000L)
		{
			return sign + String.format(Locale.ROOT, "%.1fB", abs / 1_000_000_000D);
		}
		if (abs >= 1_000_000L)
		{
			return sign + String.format(Locale.ROOT, "%.1fM", abs / 1_000_000D);
		}
		if (abs >= 1000L)
		{
			return sign + String.format(Locale.ROOT, "%.1fK", abs / 1000D);
		}
		return sign + abs;
	}
}
