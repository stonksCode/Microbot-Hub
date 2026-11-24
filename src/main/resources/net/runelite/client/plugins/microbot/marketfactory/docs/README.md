# Market Factory

Market Factory is a fully automatic OSRS Grand Exchange plugin for Microbot-Hub. It scans the [OSRS Wiki prices API](https://prices.runescape.wiki/), evaluates same-item flips and input-to-output processing recipes, sizes trades against capital and buy limits, and only acts on candidates it judges profitable after GE tax.

## Features

- **Two routes, one engine**: flip-only, process-only, flip-first-then-process, process-first-then-flip, or best-of-both
- **Smarter pricing**: conservative percentile pricing over recent history, 2% GE tax (5M cap, <50 gp exempt)
- **Risk controls**: presets (Conservative/Balanced/Aggressive) plus a Custom section; filters on volume, ROI, margin, volatility, spread stability, trend and freshness
- **Membership aware**: auto-detects account membership and world membership, with F2P/members enforcement
- **Capital aware**: auto-detects coins in inventory/bank (with bank mirror support) or use a fixed GP limit; reserves cash
- **Processing catalogue**: starter set of cooking/crafting/herblore recipes with tool/fractional/byproduct handling
- **Break handling**: integrates with BreakHandler V2 and Rs2Antiban, with a thin plugin guard
- **Offline test harness**: `MarketScanHarness` runs against live wiki data without the game client
- **Export**: save the latest scan to CSV from the config panel

## Execution modes

Set **Execution mode** in the Execution section:

- **Off** - scan only. Trades that are already open keep being managed.
- **Paper** (default) - runs the full trade state machine against a simulated Grand Exchange. Nothing is clicked in the game. Use it to check the flow and the journal before going live. Fills are simulated at your volume share, so it proves the logic, not the fill rate.
- **Live** - places real GE offers. **Flips only**: processing recipes are scanned and shown but not executed yet.

A flip runs buy, collect, sell, collect. Before entering, the scan is re-priced against the freshest prints (bid 1 gp above the last instant-sell, ask 1 gp under the last instant-buy) and the margin is re-checked, so a flip that only looked good on stale history is dropped. Unfilled buys are cancelled after a timeout; unfilled sells are repriced toward break-even in steps, and liquidated at the last instant-sell price if the stop-loss or the hold limit is hit. Trades in a finished state are appended to `~/Microbot/MarketFactory/trades-paper.csv` or `trades-live.csv` with predicted and realised profit, which is how the risk presets should be calibrated.

Live mode limits:

- Keep the trading coins in your **inventory**; banked coins are not withdrawn.
- Open trades are not saved. If you stop the plugin mid-trade, the offers stay in the Grand Exchange and you collect them yourself.
- Stand near the Grand Exchange or let the plugin walk there.

## Quick start

1. Enable **Market Factory** in the Microbot plugin list
2. Set your route mode and capital source in **General**
3. Pick a **Risk level** (Balanced is a safe starting point)
4. Click **Rescan now** in **Actions** to build your first scan
5. Leave **Execution mode** on Paper first and check `trades-paper.csv`, then switch to Live with a small stack
6. Watch the overlay for open trades, realised profit, top flips and top recipes

## Safety notes

- **Live mode spends real in-game gold.** Start with a small stack and watch the first few trades.
- **Market data is third-party**: prices come from the OSRS Wiki; treat them as estimates, not guarantees
- **No overnight guarantee**: holding tolerance is derived from predicted fill time, not assumed
- **No copied KSP code**: this plugin is a clean implementation; `f2pprocessingfactory` was used only as a behavioral reference

## Files

- `MarketFactoryPlugin` - plugin entry point
- `MarketFactoryScript` - scan loop and break awareness
- `MarketFactoryConfig` - all settings
- `MarketFactoryOverlay` - on-screen status
- `CandidateEngine` / `MarketAnalyzer` / `MarketDataService` - core logic
- `MarketScanHarness` - offline tester
- `breaks/BreakGuard` - BreakHandler V2 integration
