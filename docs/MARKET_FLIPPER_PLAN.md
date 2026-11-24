# Market Factory — Design & Implementation Plan

Status: **proposal, awaiting review** (no Java written yet)
Target: `Microbot-Hub/src/main/java/net/runelite/client/plugins/microbot/marketfactory/`
Plugin: `MarketFactoryPlugin`
Config group: `marketFactory`

A self-directed Grand Exchange plugin that either **flips** items (buy low, sell high on the same
item) or **processes** items (buy inputs, transform, sell outputs), choosing between the two from
live market data. Unlike `geflipper`, it does not depend on the external Flipping Copilot plugin for
item selection. Unlike `gecooker`, it handles the buy and sell sides of the GE itself.

---

## 1. Goals

- Scan the market continuously and rank opportunities by **risk-adjusted profit per capital-hour**,
  not by raw margin.
- Estimate whether a trade will actually be profitable: fill probability, time to fill, slippage,
  volatility, trend, and liquidation risk.
- Support five operating modes: flip only, process only, flip then process, process then flip, and
  best-of-both in a single allocation pass.
- Membership-aware: detect account type and world type, then gate recipes and items accordingly,
  the same way `f2pprocessingfactory` does.
- Capital-aware: auto-detect liquid GP or use a user-specified limit.
- Configurable risk appetite, watchlist and blacklist.
- Never risk a half-finished transaction: every state is recoverable and reconciled on startup.

## 2. Non-goals

- No public GE order book exists. The plugin must present **expected** profit with a confidence
  band, never a guarantee.
- No combat, no questing, no mule/trade automation in v1.
- No third-party plugin dependency (no Copilot, no KSP support jars).
- No license-bearing code copied from the KSP release repo. It has **no license**, so it is used
  only as a behavioral reference; all code here is original.

---

## 3. Architecture

```
MarketDataService ─→ MarketAnalyzer ─→ CandidateEngine ─→ PortfolioAllocator ─→ Route
      (HTTP/cache)     (pure metrics)   (flip + process)    (slots/capital/risk)   │
                                                                                   ├→ FlipRoute
                                                                                   └→ ProcessRoute
                     shared services: PositionLedger, BuyLimitLedger, GeExecutor,
                                      Watchdog, BreakGuard, Humanizer, SessionStats
```

| Module | Responsibility | Depends on |
|---|---|---|
| `MarketDataService` | Fetch and cache latest prices, item mapping, hourly aggregates, per-item history. Single-flight, rate-limited, off client thread. | `OkHttp`/`HttpClient`, `Gson` |
| `MarketAnalyzer` | Pure functions: quotes, tax, ROI, volatility, trend, liquidity, stability, score. Unit-testable with fixtures, no game state. | none |
| `CandidateEngine` | Build ranked flip candidates and process candidates; apply hard filters; attach rejection reasons. | `MarketAnalyzer`, `RecipeCatalog` |
| `PortfolioAllocator` | Choose how many GE slots and how much capital each candidate gets, subject to caps and reserve. | `CandidateEngine`, `PositionLedger` |
| `FlipRoute` | Acquire → hold → exit for same-item trades. | `GeExecutor`, `PositionLedger` |
| `ProcessRoute` | Recipe select → buy inputs → bank → prepare → produce → bank → sell outputs/byproducts. | `GeExecutor`, `RecipeCatalog`, `BuyLimitLedger` |
| `PositionLedger` | Persisted per-profile positions: item, qty, cost basis, target, route, timestamps, realised/unrealised P&L. | `ConfigManager` |
| `BuyLimitLedger` | Persisted per-profile 4-hour GE buy-limit windows, per item. | `ConfigManager` |
| `GeExecutor` | Thin, guarded wrapper over `Rs2GrandExchange` plus widget-level Modify/Abort and Collect-all accounting. | `Rs2GrandExchange`, `Rs2Widget` |
| `RecipeCatalog` | Declarative recipe data: inputs, outputs, tool, fractional quantities, skill gate, members flag. | none |
| `Watchdog` | No-progress detection, state-specific recovery, backoff. | all routes |
| `BreakGuard` | Break-aware parking; `BreakHandlerScript` lock state around atomic GE actions. | `BreakHandlerScript` |
| `Humanizer` | `Rs2Antiban`, mouse-away during GE waits, optional soft breaks. | `Rs2Antiban` |
| `SessionStats` | P&L, fees, capital deployed, fills, prediction-vs-actual records. | none |

Folder layout (~15 files, plus `enums/`):

```
marketfactory/
  MarketFactoryPlugin.java
  MarketFactoryConfig.java
  MarketFactoryOverlay.java
  MarketFactoryScript.java
  MarketDataService.java
  MarketAnalyzer.java
  MarketQuote.java
  RiskProfile.java
  Candidate.java
  CandidateEngine.java
  PortfolioAllocator.java
  PositionLedger.java
  Position.java
  BuyLimitLedger.java
  GeExecutor.java
  FlipRoute.java
  ProcessRoute.java
  RecipeCatalog.java
  FactoryRecipe.java
  RecipeInput.java
  Watchdog.java
  BreakGuard.java
  Humanizer.java
  SessionStats.java
  enums/RouteMode.java
  enums/RiskLevel.java
  enums/MarketState.java
  enums/RejectReason.java
```

---

## 4. Market data

All pricing comes from the OSRS Wiki prices API. Requests are made off the client thread, with a
descriptive `User-Agent`, a shared cache, and single-flight de-duplication so parallel callers do not
issue duplicate requests.

| Endpoint | Fields used | Refresh | Notes |
|---|---|---|---|
| `/api/v1/osrs/latest` | `high`, `highTime`, `low`, `lowTime` | 60 s | Whole-market snapshot, one request |
| `/api/v1/osrs/mapping` | `name`, `limit`, `members`, `alch`, `id` | 24 h | Official buy limit and membership gate |
| `/api/v1/osrs/1h` | `avgHighPrice`, `avgLowPrice`, `highPriceVolume`, `lowPriceVolume` | 5–15 min | Liquidity and volatility base |
| `/api/v1/osrs/timeseries?timestep=1h&id=…` | full history | on demand | **Shortlisted items only**, cached 1–6 h |

Staleness policy: a quote is unusable if `max(highTime, lowTime)` is older than the configured
freshness window (default 30 min for flips, 2 h for process outputs). Stale data is a rejection
reason, not a fallback.

## 5. Metrics

For an item with recent prints and hourly aggregates:

```
buyPrice   = conservativeBuy         see below
sellPrice  = conservativeSell
tax        = min(5_000_000, floor(sellPrice * 0.02))   if sellPrice >= 50 else 0
netMargin  = sellPrice - buyPrice - tax
roi        = netMargin / buyPrice
```

`conservativeBuy` / `conservativeSell` start from a percentile of recent `low` / `high` prints rather
than the single latest print, then adjust by the configured aggressiveness. This is the main defense
against acting on one stale or manipulated print.

Derived metrics:

- **Volatility** — coefficient of variation of hourly average prices over the window.
- **Trend** — least-squares slope of hourly averages, normalised; plus current z-score against the
  7-day median.
- **Spread stability** — current spread vs the median spread of the same window. A spread far above
  its own median is suspicious (stale print, thin book, manipulation), not attractive.
- **Fill time** — `quantity / (hourlyVolume * participationCap)` computed separately for the buy and
  the sell side; the slower side governs.
- **Capital efficiency** — `roi / (fillHours)`; profit per GP-hour locked.
- **Risk-adjusted score** — see §6.

## 6. Risk model

Hard rejection filters (any one fails the candidate):

- net margin below `minProfitPerUnit`, or ROI below `minRoi`
- volume below `minHourlyVolume`, or fill time above `maxFillHours`
- volatility above `maxCv`, or spread instability above `maxSpreadRatio`
- stale prints
- buy limit remaining below the minimum viable quantity
- item is on the blacklist, or not on a non-empty watchlist
- members item while on an F2P account or world
- capital available below one unit

Scoring:

```
fillHours      = max(qty / (buyVol  * participation), qty / (sellVol * participation))
profitPerHour  = netMargin * qty / fillHours
stability      = clamp(1 - cv / maxCv, 0, 1) * trendFactor * freshnessFactor
score          = profitPerHour * stability - volatilityPenalty
```

Risk presets (starting hypotheses, to be calibrated in Phase 5):

| | Conservative | Balanced | Aggressive |
|---|---|---|---|
| Min hourly volume | 500 | 200 | 50 |
| Min ROI after tax | 3 % | 2 % | 1 % |
| Min profit per unit | 100 gp | 50 gp | 20 gp |
| Max volatility (CV) | 0.08 | 0.15 | 0.30 |
| Max participation of hourly volume | 2 % | 5 % | 10 % |
| Target fill time | < 30 min | < 2 h | < 6 h |
| Reprice steps | 1 | 2 | 3 |
| Hard stop | −2 % | −5 % | −10 % |
| Max capital per item | 20 % | 35 % | 60 % |
| Max concurrent positions | 2 | 4 | 6 |
| Cash reserve | 25 % | 15 % | 5 % |

`CUSTOM` exposes every field directly. Presets only seed the values.

## 7. Holding tolerance and exit policy

Holding tolerance is derived, never asked for in raw minutes:

```
maxHoldMinutes = clamp(3 * predictedFillMinutes, 30, risk.maxHoldCapMinutes)
```

Exit decision order, evaluated every tick while a position is open:

1. Sell offer filled → collect, close position, record realised P&L and prediction error.
2. Current sell price still **≥ break-even after tax** and reprices remain → reprice down one step.
3. Price below break-even, item still passes trend/volatility/freshness → **hold**; do not
   crystallise a loss on a healthy item.
4. Price below `breakEven * (1 - stopLoss)` **and** 1 h trend negative → **exit**, take the loss,
   free capital and slots.
5. `maxHoldMinutes` exceeded → force the same decision as (4) but log it as a timeout, so the
   calibration pass can separate "held too long" from "market moved".

Overnight holds are permitted only if the item passes the volume and stability gates; otherwise the
position is closed before the configured session end.

## 8. Candidate generation

### 8.1 Flip candidates

For every item in the universe (watchlist ∩ mapping ∩ not blacklisted ∩ membership-eligible):

- build a conservative quote from the latest and hourly data
- apply hard filters, attach a `RejectReason` when rejected
- compute fill time, capital efficiency, score
- quantity cap = `min(buyLimitRemaining, capitalPerItem / buyPrice, participationCap * buyHourlyVolume)`

### 8.2 Process candidates

Recipes come from `RecipeCatalog`. Each recipe declares inputs (consumed, tool, or fractional per
output), primary output, optional byproducts, skill requirement, members flag, interaction verb and
estimated units/hour.

Eligibility: skill level, membership (account **and** world), inputs purchasable within buy limits,
affordability, and — critically — **output absorption**:

```
maxUnitsByOutput = outputHourlyVolume * outputParticipationCap
```

A profitable recipe you cannot sell is a loss. Production throughput is capped by output absorption,
not by machine speed.

### 8.3 Route selection

| `RouteMode` | Behaviour |
|---|---|
| `FLIP_ONLY` | Only flip candidates reach the allocator. |
| `PROCESS_ONLY` | Only process candidates; flips disabled. |
| `FLIP_THEN_PROCESS` | Flips run until `flipMinutes`/`flipCycles` is exhausted, then processing takes over. |
| `PROCESS_THEN_FLIP` | Processing runs until `processMinutes`/`maxProcessingUnits` is exhausted or outputs stop absorbing, then flips take over. |
| `BEST_OF_BOTH` | Both candidate types ranked in one allocation pass; the highest score wins each slot. |

Switching is a config/state change only — the routes and ledgers are shared, so no code path is
duplicated.

## 9. Portfolio allocation

Greedy by risk-adjusted score per capital-hour, with these constraints:

- total slots = 8, minus `geSlotsReservedForExits` (default 1)
- capital deployed ≤ `spendableCoins - cashReserve`
- per-item capital ≤ `maxCapitalPerItemPercent` of deployable capital
- per-item quantity ≤ remaining 4 h buy limit
- concurrent positions ≤ `maxConcurrentPositions`
- one live offer per item per direction; no duplicate offers
- process candidates additionally capped by output absorption and by free inventory space

Allocation is recomputed on every scan; a position is never abandoned mid-flight by a re-allocation,
only by the exit policy.

## 10. Execution

### 10.1 `GeExecutor`

Wraps `Rs2GrandExchange` (`processOffer`, `buyItem`, `sellItem`, `collectAllToBank`,
`abortOffer`, `abortAllOffers`, `isSlotAvailable`, `getActiveOfferSlots`, `getCompletedOffers`).

Rules:

- All calls happen off the client thread; widget reads use `runOnClientThreadOptional`.
- Before any mutation: verify the exact intended UI state (overview vs offer screen vs editor, no
  chatbox input, no menu open) — the reference plugin's `isExactGeOverviewReady()` is the right idea.
- One offer per distinct item per direction; offers for distinct items are placed in parallel slots
  rather than serialised.
- **Collect-all is deferred until every offer owned by this plugin is terminal**, so credits are
  accounted atomically and a collect cannot cause a double buy. This is the single most important
  correctness rule from the reference.
- Stalled offers: `Modify offer` path first (widget/menu automation, no abort), then abort and
  recreate only after the reprice budget is exhausted.
- Every placement failure backs off (`10 s`, doubling, capped) and is logged with a reason.

### 10.2 `FlipRoute`

```
SCAN → SELECT → FUND → PLACE_BUY → WAIT_BUY → COLLECT_BUY → (hold) → PLACE_SELL
     → WAIT_SELL → REPRICE/HOLD/STOP → COLLECT_SELL → RECORD → SCAN
```

### 10.3 `ProcessRoute`

```
SCAN → SELECT_RECIPE → FUND → BUY_INPUTS → WAIT_BUY → COLLECT → BANK
     → PREPARE_INVENTORY → PRODUCE → BANK_OUTPUT → SELL_OUTPUT(+BYPRODUCTS)
     → COLLECT_SELL → RECORD → SCAN
```

`PRODUCE` handles three interaction shapes seen in the reference: direct item-on-item use
(knife + chocolate bar), item-on-tool, and production-dialogue recipes (pie shell, pot, etc.). The
progress watchdog restarts a stalled production step a bounded number of times, then abandons the
cycle and banks what it has.

### 10.4 Top-level state machine

```
SCANNING ─→ SELECTING ─→ ACQUIRING ─┬→ (FLIP)    HOLDING → SELLING → COLLECTING ─┐
                                    └→ (PROCESS) BANKING → PREPARING → PRODUCING
                                                 → BANKING_OUTPUT → SELLING → COLLECTING
                                                                                   │
        COOLDOWN / WAITING_FOR_LIMIT / WAITING_FOR_MARKET ←────────────────────────┘
                                     RECOVERING (watchdog) ─→ any state
```

## 11. Ledgers

### `PositionLedger` (persisted in `ConfigManager`, per profile)

Per position: route, item id, quantity, average cost, target sell price, break-even price, opened
at, predicted fill time, reprices used, realised/unrealised P&L, prediction error.

On startup it **reconciles** against bank, inventory and live GE offers:

- position in ledger + matching live sell offer → resume waiting
- position in ledger + items in bank/inventory but no offer → resume at the sell step
- live offer with no ledger entry → adopt as `UNTRACKED`, do not duplicate
- ledger entry with no items and no offer → mark `CLOSED_UNKNOWN`, log for review

### `BuyLimitLedger` (persisted per profile)

`item id → { quantity bought, window start }`. Window is 4 hours from first purchase. Also tracks
"cold start" items where the plugin has no history and must assume a partially used limit, which is
why the first buy of an unknown item is deliberately conservative.

## 12. Break handling

Decision: **BreakHandlerV2 + `Rs2Antiban` + a thin plugin-owned guard.** No custom break handler.

Why:

- V2 already handles login, world hopping, login lockouts, retries and Discord notifications. Those
  are the risky parts and are already tested.
- V2 can **stop a selected plugin before a break and restart it after**, with configurable lead and
  delay. That is the cleanest guarantee that we never log out mid-transaction.
- V1 and V2 publish the same global state (`BreakHandlerScript.isBreakActive()`,
  `isIngameBreakActive()`, `isMicroBreakActive()`, `breakIn`, `setLockState(...)`), so the plugin is
  compatible with either without code changes.
- `Rs2Antiban` covers in-game behaviour (micro-breaks, mouse-away, action cooldown, idle) and is what
  makes GE wait windows look human. It never logs out.

`BreakGuard` contract:

- `setLockState(true)` around every atomic GE action (place, modify, collect), released immediately.
- Before any GE mutation: if a break is active or `breakIn <= 20`, park in `IDLE` instead of starting
  a new action.
- Long GE waits (`WAIT_BUY`, `WAIT_SELL`) are the preferred break windows: trigger breaks there.
- On resume, always re-read GE slots, inventory and bank, then run reconciliation. Never trust the
  pre-break cache.
- No custom logout, no custom world hop, no custom login. One break system only.

## 13. Configuration

```
General
  routeMode                FLIP_ONLY | PROCESS_ONLY | FLIP_THEN_PROCESS |
                           PROCESS_THEN_FLIP | BEST_OF_BOTH
  riskLevel                CONSERVATIVE | BALANCED | AGGRESSIVE | CUSTOM
  capitalSource            AUTO_DETECT | FIXED_GP
  capitalLimit             int        (used when FIXED_GP)
  cashReservePercent       int
  watchlist                String     (comma separated; empty = whole market)
  blacklist                String
  membershipMode           AUTO | F2P_ONLY | MEMBERS_ONLY
  dryRun                   boolean    (default true on first run)
  flipMinutes / flipCycles         hybrid mode timing
  processMinutes / maxProcessingUnits

Flip (CUSTOM only; presets hide these)
  minRoiPercent, minProfitPerUnit, maxFillHours, minHourlyVolume
  participationCapPercent, maxCapitalPerItemPercent, maxConcurrentPositions
  maxHoldMinutes (0 = auto), repriceStepPercent, maxReprices
  stopLossPercent, allowOvernightHold

Process
  recipeMode               AUTO_BEST_PROFIT | FIXED_RECIPE
  fixedRecipe
  targetUnitsPerCycle, maxInputSpend
  outputParticipationCapPercent
  sellByproducts           boolean

Execution
  geSlotsReservedForExits  int
  offerTimeoutSeconds
  collectMode              BANK | INVENTORY
  maxPlacementFailures

Breaks
  respectBreakHandler      boolean (default true)
  breakLeadSeconds         int
  useRs2Antiban            boolean
  softBreakDuringWaits      boolean

Overlay
  showCandidateTable, showPositions, showPnl, showRejections
```

Derived/display values: detected liquid GP, committed GP (locked in live buy offers), spendable GP,
session P&L, fees paid, average fill time vs predicted.

## 14. Safety and failure handling

- **Dry run** simulates placement and records what would have happened; no GE interaction.
- **Single instance guard** — a static flag plus the existing script-instance conventions; a second
  copy refuses to start.
- **Client thread** — no blocking calls; all HTTP and sleeps off-thread.
- **Watchdog** — if state does not progress for N ticks, escalate to the state's recovery action;
  after 3 failures in a cycle, abandon the cycle, bank everything, and rescan.
- **Cash accounting** — cash flows are derived from observed coin deltas plus offer ledger, never
  from assumptions about fill price.
- **Duplicate protection** — one offer per item per direction, keyed by item id, checked against live
  slots before every placement.
- **Kill switch** — a config button that stops the script, aborts all offers (optionally), and leaves
  a summary in the log.

## 15. Phases

| Phase | Contents | Exit criteria |
|---|---|---|
| **0. Offline analysis** | Plugin/config/overlay skeleton, `MarketDataService`, `MarketAnalyzer`, `RiskProfile`, `CandidateEngine`, ranking harness that prints top flips + top recipes | Rankings match manual wiki spot checks; cached replay is deterministic; no GE code exists yet |
| **1. Flip MVP** | `PositionLedger`, `BuyLimitLedger`, `GeExecutor`, `FlipRoute`, dry-run, one item, one slot, Modify/Abort, watchdog | 24 h dry run produces sane decisions; live small-capital test with predicted-vs-actual report |
| **2. Processing** | `RecipeCatalog`, `ProcessRoute`, byproducts, output-absorption cap | One full cycle per supported skill; no stranded inputs/outputs; restart reconciliation works |
| **3. Portfolio & risk** | `PortfolioAllocator`, multi-position, volatility/trend/stop guards, cooldowns, caps | Offline backtest over 7–30 days with conservative fills; drawdown and fill time within bounds |
| **4. Hardening** | Break guard, humanizer, recovery paths, 8 h+ soak, multi-instance guard | No duplicate offers, no stuck editors, stable memory/CPU |
| **5. Calibration** | Predicted vs realised margin, fill time, slippage; tune participation, reprice steps, thresholds | Error within target bounds over 50+ recorded trades; paper mode stays available |

## 16. Validation

- **Offline harness** — replay cached API snapshots through `MarketAnalyzer` + `CandidateEngine`;
  assert deterministic rankings. Doubles as the regression suite when the model changes.
- **Paper mode** — full decision path, no GE mutation; logs intended actions.
- **Calibration log** — every position stores prediction and outcome; Phase 5 compares them.
- **Backtest** — hourly history plus conservative fill assumptions (no fills above the low
  percentile on the sell side, none below the high percentile on the buy side).

## 17. Open items to settle during Phase 0–1

- Exact low-value tax exemption threshold and cap (confirm against current wiki rules before
  hard-coding).
- Whether a flip position should ever be converted into a process input (e.g. bought logs become
  bowstrings). Proposal: no automatic conversion; the two routes are separate but share capital.
- Default hybrid timings (`flipMinutes`, `processMinutes`).
- Whether `BEST_OF_BOTH` should reserve one slot per route or compete for all slots.
- Minimum sample size before the calibration pass is allowed to change presets.

## 18. References

- `KSPOG/ksppluginsrelease` → `f2pprocessingfactory` (behavioral reference only, no license).
  Reusable ideas: recipe catalog shape, 4 h buy-limit ledger, atomic Collect-all accounting,
  state-specific watchdog recovery, output-absorption reasoning.
- `microbot/geflipper` — existing GE slot-action and UI-state helpers worth reading before writing
  a third implementation (`SlotActionExecutor`, `GeUiState`).
- `microbot/gecooker` — example of GE-adjacent processing without buy/sell automation.
- Microbot core: `Rs2GrandExchange`, `Rs2Bank`, `Rs2Inventory`, `Rs2Widget`, `Rs2Antiban`,
  `BreakHandlerScript`, `Rs2Player.isMember()/isInMemberWorld()`, `LoginManager`.
