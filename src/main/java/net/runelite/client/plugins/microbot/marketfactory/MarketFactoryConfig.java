package net.runelite.client.plugins.microbot.marketfactory;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.marketfactory.enums.CapitalSource;
import net.runelite.client.plugins.microbot.marketfactory.enums.ExecutionMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.MembershipMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.plugins.microbot.marketfactory.enums.RouteMode;

/**
 * Configuration for Market Factory. The config panel maps every item to its keyName; the
 * plugin uses {@link net.runelite.api.events.ConfigChanged} to react to {@link ConfigButton}
 * presses (the panel creates a new button id on each click rather than firing a special
 * event type in this Microbot fork).
 */
@ConfigGroup(MarketFactoryConfig.GROUP)
public interface MarketFactoryConfig extends Config
{
	String GROUP = "marketFactory";

	// ------------------------------------------------------------------
	// Sections
	// ------------------------------------------------------------------

	@ConfigSection(
		name = "General",
		description = "Mode, account scope and capital",
		position = 0
	)
	String general = "general";

	@ConfigSection(
		name = "Analysis",
		description = "Market filters, history and scan limits",
		position = 1
	)
	String analysis = "analysis";

	@ConfigSection(
		name = "Risk (Preset)",
		description = "Choose a starting risk profile or switch to Custom to tune below",
		position = 2
	)
	String riskPreset = "riskPreset";

	@ConfigSection(
		name = "Risk (Custom)",
		description = "Only used when Risk Level is set to Custom",
		position = 3,
		closedByDefault = true
	)
	String riskCustom = "riskCustom";

	@ConfigSection(
		name = "Processing",
		description = "Recipe catalogue controls",
		position = 4
	)
	String processing = "processing";

	@ConfigSection(
		name = "Execution",
		description = "Scan cadence and holding behaviour",
		position = 5
	)
	String execution = "execution";

	@ConfigSection(
		name = "Breaks",
		description = "BreakHandler V2 + Rs2Antiban integration",
		position = 6
	)
	String breaks = "breaks";

	@ConfigSection(
		name = "Overlay",
		description = "On-screen status display",
		position = 7
	)
	String overlay = "overlay";

	@ConfigSection(
		name = "Actions",
		description = "Manual triggers",
		position = 8
	)
	String actions = "actions";

	// ------------------------------------------------------------------
	// General
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "routeMode",
		name = "Route mode",
		description = "How to split time between same-item flips and input-to-output processing",
		position = 0,
		section = general
	)
	default RouteMode routeMode()
	{
		return RouteMode.FLIP_ONLY;
	}

	@ConfigItem(
		keyName = "membershipMode",
		name = "Item scope",
		description = "Enforce F2P/members filtering in addition to auto-detect",
		position = 1,
		section = general
	)
	default MembershipMode membershipMode()
	{
		return MembershipMode.AUTO;
	}

	@ConfigItem(
		keyName = "capitalSource",
		name = "Capital source",
		description = "Use detected liquid GP or a fixed GP limit",
		position = 2,
		section = general
	)
	default CapitalSource capitalSource()
	{
		return CapitalSource.AUTO_DETECT;
	}

	@ConfigItem(
		keyName = "gpLimit",
		name = "GP limit",
		description = "Capital to use when Capital source is set to 'Use configured GP limit'",
		position = 3,
		section = general
	)
	@Range(min = 1000, max = 2_000_000_000)
	default long gpLimit()
	{
		return 5_000_000L;
	}

	@ConfigItem(
		keyName = "autoDetectMembership",
		name = "Auto-detect membership",
		description = "Detect if the account is members and whether the current world is members",
		position = 4,
		section = general
	)
	default boolean autoDetectMembership()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Analysis
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "watchlistOnly",
		name = "Watchlist only",
		description = "Only consider items/recipes on the watchlist",
		position = 0,
		section = analysis
	)
	default boolean watchlistOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "watchlist",
		name = "Watchlist",
		description = "Comma or newline separated item/recipe names to prefer or restrict to",
		position = 1,
		section = analysis
	)
	default String watchlist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "blacklist",
		name = "Blacklist",
		description = "Comma or newline separated item/recipe names to never trade",
		position = 2,
		section = analysis
	)
	default String blacklist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "deepScanLimit",
		name = "Deep scan limit",
		description = "How many top flips get hourly history fetched and fully analysed per scan",
		position = 3,
		section = analysis
	)
	@Range(min = 10, max = 200)
	default int deepScanLimit()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "enforceHistory",
		name = "Require history",
		description = "Reject flips with too few history points (overridden by Custom risk)",
		position = 4,
		section = analysis
	)
	default boolean enforceHistory()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Risk preset
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "riskLevel",
		name = "Risk level",
		description = "Aggressive finds more trades with wider tolerances; Conservative is stricter",
		position = 0,
		section = riskPreset
	)
	default RiskLevel riskLevel()
	{
		return RiskLevel.AGGRESSIVE;
	}

	// ------------------------------------------------------------------
	// Risk custom
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "minRoiPercent",
		name = "Min ROI %",
		description = "Minimum return on capital after GE tax",
		position = 0,
		section = riskCustom
	)
	@Range(min = 0, max = 50)
	default double minRoiPercent()
	{
		return 2.0;
	}

	@ConfigItem(
		keyName = "minProfitPerUnit",
		name = "Min profit per unit (gp)",
		description = "Floor on profit per item/cycle",
		position = 1,
		section = riskCustom
	)
	@Range(min = 0, max = 10000)
	default int minProfitPerUnit()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "minHourlyVolume",
		name = "Min hourly volume",
		description = "Bottleneck volume across buy/sell (or input/output) per hour",
		position = 2,
		section = riskCustom
	)
	@Range(min = 0, max = 100000)
	default int minHourlyVolume()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "maxVolatilityCv",
		name = "Max volatility (CV)",
		description = "Coefficient of variation of hourly mid prices (lower = more stable)",
		position = 3,
		section = riskCustom
	)
	@Range(min = 1, max = 100)
	default int maxVolatilityCv()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "maxFillHours",
		name = "Max fill time per leg (minutes)",
		description = "Longest acceptable time for each of the buy and the sell to fill",
		position = 4,
		section = riskCustom
	)
	@Range(min = 1, max = 240)
	default int maxFillHours()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "priceBufferPercent",
		name = "Price buffer %",
		description = "Shave margin to be conservative against slippage when setting offers",
		position = 5,
		section = riskCustom
	)
	@Range(min = 0, max = 25)
	default int priceBufferPercent()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "cashReservePercent",
		name = "Cash reserve %",
		description = "GP kept in reserve and not committed to trades",
		position = 6,
		section = riskCustom
	)
	@Range(min = 0, max = 90)
	default int cashReservePercent()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "maxHoldCapMinutes",
		name = "Max hold cap (minutes)",
		description = "Absolute ceiling on holding tolerance regardless of fill time",
		position = 7,
		section = riskCustom
	)
	@Range(min = 30, max = 2880)
	default int maxHoldCapMinutes()
	{
		return 720;
	}

	@ConfigItem(
		keyName = "participationCapPercent",
		name = "Volume share %",
		description = "Share of each side's hourly volume the plugin assumes its offers capture",
		position = 8,
		section = riskCustom
	)
	@Range(min = 1, max = 50)
	default int participationCapPercent()
	{
		return 5;
	}

	// ------------------------------------------------------------------
	// Processing
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "enableProcessing",
		name = "Enable processing",
		description = "Score input-to-output recipes alongside flips",
		position = 0,
		section = processing
	)
	default boolean enableProcessing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "processingRateFactor",
		name = "Processing rate factor %",
		description = "Scale estimated actions-per-hour when sizing recipes",
		position = 1,
		section = processing
	)
	@Range(min = 25, max = 200)
	default int processingRateFactor()
	{
		return 100;
	}

	// ------------------------------------------------------------------
	// Execution
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "scanIntervalSeconds",
		name = "Scan interval (seconds)",
		description = "How often the engine re-scans the market (0 = manual only)",
		position = 0,
		section = execution
	)
	@Range(min = 0, max = 600)
	default int scanIntervalSeconds()
	{
		return 120;
	}

	@ConfigItem(
		keyName = "autoStart",
		name = "Auto-start",
		description = "Start scanning when the plugin is enabled",
		position = 1,
		section = execution
	)
	default boolean autoStart()
	{
		return false;
	}

	@ConfigItem(
		keyName = "executionMode",
		name = "Execution mode",
		description = "Off: scan only. Paper: simulate trades, nothing is clicked. Live: place real Grand Exchange offers (flips only; keep the trading coins in your inventory).",
		position = 2,
		section = execution
	)
	default ExecutionMode executionMode()
	{
		return ExecutionMode.PAPER;
	}

	@ConfigItem(
		keyName = "maxConcurrentTrades",
		name = "Max concurrent trades",
		description = "Maximum number of active GE offers at once",
		position = 4,
		section = execution
	)
	@Range(min = 1, max = 8)
	default int maxConcurrentTrades()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "enableAntiBan",
		name = "Enable Rs2Antiban",
		description = "Use Rs2Antiban helpers when actions are taken",
		position = 5,
		section = execution
	)
	default boolean enableAntiBan()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Breaks
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "useBreakHandler",
		name = "Use BreakHandler V2",
		description = "Respect BreakHandlerScript break state when idle/between actions",
		position = 0,
		section = breaks
	)
	default boolean useBreakHandler()
	{
		return true;
	}

	@ConfigItem(
		keyName = "breakGuardEnabled",
		name = "Break guard",
		description = "Thin plugin guard on top of BreakHandlerV2 (does not replace it)",
		position = 1,
		section = breaks
	)
	default boolean breakGuardEnabled()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Overlay
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Display scan status, capital and top candidates",
		position = 0,
		section = overlay
	)
	default boolean showOverlay()
	{
		return true;
	}

	// ------------------------------------------------------------------
	// Actions
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "rescanNow",
		name = "Rescan now",
		description = "Force an immediate market scan",
		position = 0,
		section = actions
	)
	default ConfigButton rescanNow()
	{
		return new ConfigButton();
	}

	@ConfigItem(
		keyName = "exportCsv",
		name = "Export scan to CSV",
		description = "Write the latest scan report to a CSV file in the plugin folder",
		position = 1,
		section = actions
	)
	default ConfigButton exportCsv()
	{
		return new ConfigButton();
	}
}
