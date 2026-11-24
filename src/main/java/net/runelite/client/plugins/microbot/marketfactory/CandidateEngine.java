package net.runelite.client.plugins.microbot.marketfactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.marketfactory.enums.CandidateType;
import net.runelite.client.plugins.microbot.marketfactory.enums.MembershipMode;
import net.runelite.client.plugins.microbot.marketfactory.enums.RejectReason;
import net.runelite.client.plugins.microbot.marketfactory.model.HourlyPoint;
import net.runelite.client.plugins.microbot.marketfactory.model.ItemMeta;
import net.runelite.client.plugins.microbot.marketfactory.model.MarketSnapshot;
import net.runelite.client.plugins.microbot.marketfactory.model.PricePoint;

/**
 * Turns a {@link MarketSnapshot} into ranked, already-sized candidates.
 *
 * <p>Flips use a two-stage scan because hourly history is the expensive part: every tradeable
 * item is first ranked from the cheap latest/1h tables, then only the best {@code deepScanLimit}
 * items have history fetched and are analysed with the full {@link MarketAnalyzer}. Recipes are
 * scored directly from latest/1h prices, using the high print for inputs (what you must pay to
 * buy) and the low print for outputs (what you can expect to receive when selling).
 *
 * <p>The engine is deliberately free of RuneLite client calls: account membership, skills,
 * capital and history all arrive through {@link ScanContext}. That keeps it replayable offline
 * and makes the exact same code run in the scan harness and in the live plugin.
 */
@Slf4j
public final class CandidateEngine
{
	private final MarketAnalyzer analyzer;

	public CandidateEngine()
	{
		this(new MarketAnalyzer());
	}

	public CandidateEngine(MarketAnalyzer analyzer)
	{
		this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
	}

	public ScanReport scan(ScanContext context)
	{
		ScanContext ctx = context == null ? ScanContext.builder().build() : context;
		MarketSnapshot snapshot = ctx.getSnapshot();

		List<Candidate> flips = new ArrayList<>();
		List<Candidate> processes = new ArrayList<>();
		int deepScanned = 0;

		if (snapshot.isEmpty())
		{
			return new ScanReport(ctx.getNowMillis(), ctx.isMembersAccount(), ctx.isMemberWorld(),
				flips, processes, 0, 0, 0, "no market data" + warningSuffix(snapshot));
		}

		if (ctx.getRouteMode().allowsFlips())
		{
			List<RoughFlip> rough = collectRoughFlips(ctx);
			int limit = Math.min(ctx.getDeepScanLimit(), rough.size());
			for (int i = 0; i < limit; i++)
			{
				Candidate candidate = evaluateFlip(rough.get(i).meta, ctx);
				deepScanned++;
				if (candidate.isViable())
				{
					flips.add(candidate);
				}
			}
			flips.sort(bestFirst());
		}

		if (ctx.getRouteMode().allowsProcessing())
		{
			for (FactoryRecipe recipe : RecipeCatalog.all())
			{
				Candidate candidate = evaluateRecipe(recipe, ctx);
				if (candidate.isViable())
				{
					processes.add(candidate);
				}
			}
			processes.sort(bestFirst());
		}

		String note = String.format(Locale.ROOT,
			"%d items, %d deep scans, %d recipes; %d flip(s), %d process(es)%s",
			snapshot.getItems().size(), deepScanned, RecipeCatalog.all().size(),
			flips.size(), processes.size(), warningSuffix(snapshot));

		return new ScanReport(ctx.getNowMillis(), ctx.isMembersAccount(), ctx.isMemberWorld(),
			flips, processes, snapshot.getItems().size(), deepScanned, RecipeCatalog.all().size(), note);
	}

	// ------------------------------------------------------------------
	// Flips
	// ------------------------------------------------------------------

	private List<RoughFlip> collectRoughFlips(ScanContext ctx)
	{
		MarketSnapshot snapshot = ctx.getSnapshot();
		List<RoughFlip> rough = new ArrayList<>();

		for (ItemMeta meta : snapshot.getItems())
		{
			if (!passesFilters(meta, ctx) || !membershipAllows(meta, ctx))
			{
				continue;
			}

			PricePoint price = snapshot.price(meta.getId());
			if (price == null || !price.isComplete())
			{
				continue;
			}

			HourlyPoint hour = snapshot.hour(meta.getId());
			if (hour == null)
			{
				continue;
			}

			long bottleneck = Math.min(hour.getLowPriceVolume(), hour.getHighPriceVolume());
			int netMargin = price.getHigh() - price.getLow() - MarketAnalyzer.tax(price.getHigh());
			if (bottleneck <= 0 || netMargin <= 0 || price.getLow() > ctx.getCapital())
			{
				continue;
			}

			// Cheap proxy only: the deep pass replaces this with percentile pricing. It is net of
			// tax and capped by the four-hour buy limit, so limit-bound items cannot crowd the
			// shortlist with margin they cannot actually repeat.
			double unitsPerHour = bottleneck * ctx.getRisk().getParticipationCap();
			if (meta.getBuyLimit() > 0)
			{
				unitsPerHour = Math.min(unitsPerHour, meta.getBuyLimit() / 4D);
			}
			rough.add(new RoughFlip(meta, netMargin * unitsPerHour));
		}

		rough.sort((a, b) -> Double.compare(b.roughScore, a.roughScore));
		return rough;
	}

	/** Re-prices one flip against the context's snapshot; used right before an offer is placed. */
	public Candidate reevaluateFlip(int itemId, ScanContext context)
	{
		ItemMeta meta = context.getSnapshot().item(itemId);
		if (meta == null)
		{
			return Candidate.builder(CandidateType.FLIP, "item " + itemId, itemId)
				.addReject(RejectReason.NO_PRICE, "unknown item")
				.build();
		}
		return evaluateFlip(meta, context);
	}

	private Candidate evaluateFlip(ItemMeta meta, ScanContext ctx)
	{
		MarketSnapshot snapshot = ctx.getSnapshot();
		RiskProfile risk = ctx.getRisk();

		MarketQuote quote = analyzer.analyze(
			meta,
			snapshot.price(meta.getId()),
			snapshot.hour(meta.getId()),
			ctx.getHistoryProvider().history(meta.getId()),
			risk,
			ctx.getNowMillis(),
			risk.isRequireHistory());

		Candidate.Builder candidate = Candidate.builder(CandidateType.FLIP, meta.getName(), meta.getId());
		if (!quote.isViable())
		{
			for (Map.Entry<RejectReason, String> reject : quote.getRejects().entrySet())
			{
				candidate.addReject(reject.getKey(), reject.getValue());
			}
			return candidate.build();
		}

		double buyPrice = quote.getBuyPrice();
		double sellPrice = quote.getSellPrice();
		double participation = risk.getParticipationCap();
		// Our offers can only capture a share of each side's volume. Each leg is timed on its own.
		double buyRate = quote.getBuyVolumePerHour() * participation;
		double sellRate = quote.getSellVolumePerHour() * participation;
		double bottleneck = Math.min(buyRate, sellRate);

		long budget = Math.round(ctx.getCapital() * (1D - risk.getCashReservePercent() / 100D));
		long perItem = Math.min(budget, Math.round(ctx.getCapital() * risk.getMaxCapitalPerItemPercent() / 100D));
		int capitalCap = buyPrice > 0 ? (int) Math.floor(perItem / buyPrice) : 0;
		if (capitalCap <= 0)
		{
			candidate.addReject(RejectReason.INSUFFICIENT_CAPITAL,
				perItem + " gp available, one unit costs " + Math.round(buyPrice));
		}

		int limitCap = meta.getBuyLimit() > 0 ? meta.getBuyLimit() : Integer.MAX_VALUE;
		int fillCap = bottleneck > 0 ? (int) Math.floor(bottleneck * risk.getMaxFillHours()) : 0;
		if (bottleneck > 0 && fillCap <= 0)
		{
			candidate.addReject(RejectReason.LOW_VOLUME,
				String.format(Locale.ROOT, "%.2f units/h cannot fill one unit in %.1fh",
					bottleneck, risk.getMaxFillHours()));
		}

		int quantity = Math.min(capitalCap, Math.min(limitCap, fillCap));
		if (quantity <= 0)
		{
			return candidate.build();
		}

		// Round trip: the buy has to fill, then the sell. If the four-hour buy limit is the
		// constraint, the trade cannot repeat before the limit has reset for this many units.
		double fillHours = quantity / buyRate + quantity / sellRate;
		double cycleHours = meta.getBuyLimit() > 0
			? Math.max(fillHours, quantity * 4D / meta.getBuyLimit())
			: fillHours;
		double profitPerUnit = quote.getNetMargin();
		long capitalUsed = Math.round(buyPrice * quantity);
		long profit = Math.round(profitPerUnit * quantity);
		long revenue = Math.round(sellPrice * quantity);
		double roi = capitalUsed > 0 ? profit * 100D / capitalUsed : 0D;
		double gpPerHour = profit / Math.max(cycleHours, 0.25D);

		return candidate
			.execution(quote.getBuyPrice(), quote.getSellPrice(), meta.getBuyLimit(), cycleHours)
			.sizing(quantity, capitalUsed, revenue, profit, roi, fillHours, gpPerHour,
				gpPerHour * quote.getStability())
			.quality(quote.getConfidence(), quote.getStability())
			.maxHoldMinutes(holdingTolerance(fillHours, risk))
			.addNote("buy " + quote.getBuyPrice() + " / sell " + quote.getSellPrice()
				+ " / tax " + quote.getTaxPerUnit() + " per unit")
			.build();
	}

	// ------------------------------------------------------------------
	// Processing
	// ------------------------------------------------------------------

	private Candidate evaluateRecipe(FactoryRecipe recipe, ScanContext ctx)
	{
		MarketSnapshot snapshot = ctx.getSnapshot();
		RiskProfile risk = ctx.getRisk();
		Integer outputId = snapshot.idFor(recipe.getOutputName());
		Candidate.Builder candidate = Candidate.builder(CandidateType.PROCESS, recipe.getDisplayName(),
			outputId == null ? 0 : outputId);

		if (!recipePassesFilters(recipe, ctx))
		{
			candidate.addReject(RejectReason.BLACKLISTED, "recipe filtered by watchlist/blacklist");
			return candidate.build();
		}
		if (ctx.getMembershipMode() == MembershipMode.MEMBERS_ONLY && !recipe.isMembersOnly())
		{
			candidate.addReject(RejectReason.MEMBERS_ONLY, "free-to-play recipe in members-only mode");
			return candidate.build();
		}
		if (recipe.isMembersOnly() && !(ctx.isMembersAccount() && ctx.isMemberWorld()))
		{
			candidate.addReject(RejectReason.MEMBERS_ONLY, "requires a members account on a members world");
			return candidate.build();
		}

		int skillLevel = ctx.getSkillSource().level(recipe.getSkill());
		if (skillLevel < recipe.getLevel())
		{
			candidate.addReject(RejectReason.SKILL_TOO_LOW,
				recipe.getSkill() + " " + skillLevel + ", needs " + recipe.getLevel());
			return candidate.build();
		}

		if (outputId == null)
		{
			candidate.addReject(RejectReason.MISSING_OUTPUT, "unknown item " + recipe.getOutputName());
			return candidate.build();
		}
		ItemMeta outputMeta = snapshot.item(outputId);
		if (outputMeta == null)
		{
			candidate.addReject(RejectReason.MISSING_OUTPUT, "no metadata for " + recipe.getOutputName());
			return candidate.build();
		}
		if (!membershipAllows(outputMeta, ctx))
		{
			candidate.addReject(RejectReason.MEMBERS_ONLY, recipe.getOutputName());
			return candidate.build();
		}

		PricePoint outputPrice = snapshot.price(outputId);
		int outputSell = outputPrice == null ? 0
			: outputPrice.hasLow() ? outputPrice.getLow()
			: outputPrice.hasHigh() ? outputPrice.getHigh() : 0;
		if (outputSell <= 0)
		{
			candidate.addReject(RejectReason.MISSING_OUTPUT, "no usable price for " + recipe.getOutputName());
			return candidate.build();
		}

		double inputCost = 0D;
		double inputUnitsPerHour = Double.MAX_VALUE;
		int limitCap = Integer.MAX_VALUE;
		boolean toolRequired = false;
		boolean missingInput = false;

		for (RecipeInput input : recipe.getInputs())
		{
			if (input.isTool())
			{
				toolRequired = true;
				continue;
			}

			Integer inputId = snapshot.idFor(input.getItemName());
			if (inputId == null)
			{
				candidate.addReject(RejectReason.MISSING_INPUT, "unknown item " + input.getItemName());
				missingInput = true;
				continue;
			}
			ItemMeta inputMeta = snapshot.item(inputId);
			if (inputMeta == null)
			{
				candidate.addReject(RejectReason.MISSING_INPUT, "no metadata for " + input.getItemName());
				missingInput = true;
				continue;
			}
			if (!membershipAllows(inputMeta, ctx))
			{
				candidate.addReject(RejectReason.MEMBERS_ONLY, input.getItemName());
				missingInput = true;
				continue;
			}

			PricePoint inputPrice = snapshot.price(inputId);
			int buy = inputPrice == null ? 0
				: inputPrice.hasHigh() ? inputPrice.getHigh()
				: inputPrice.hasLow() ? inputPrice.getLow() : 0;
			if (buy <= 0)
			{
				candidate.addReject(RejectReason.MISSING_INPUT, "no usable price for " + input.getItemName());
				missingInput = true;
				continue;
			}

			double unitsPerOutput = input.unitsPerOutput();
			inputCost += buy * unitsPerOutput;

			HourlyPoint inputHour = snapshot.hour(inputId);
			double inputVolume = inputHour == null ? 0D
				: Math.max(inputHour.getHighPriceVolume(), inputHour.getLowPriceVolume()) / unitsPerOutput;
			inputUnitsPerHour = Math.min(inputUnitsPerHour, inputVolume);

			if (inputMeta.getBuyLimit() > 0)
			{
				limitCap = Math.min(limitCap,
					(int) Math.floor(inputMeta.getBuyLimit() / unitsPerOutput));
			}
		}

		if (missingInput || inputCost <= 0)
		{
			if (inputCost <= 0 && !missingInput)
			{
				candidate.addReject(RejectReason.MISSING_INPUT, "no priced inputs");
			}
			return candidate.build();
		}

		int outputQuantity = recipe.getOutputQuantity();
		double revenue = outputSell * (double) outputQuantity
			- MarketAnalyzer.tax(outputSell) * (double) outputQuantity;
		List<String> pricedByproducts = new ArrayList<>();
		for (String byproduct : recipe.getByproducts())
		{
			Integer byproductId = snapshot.idFor(byproduct);
			PricePoint byproductPrice = byproductId == null ? null : snapshot.price(byproductId);
			int sell = byproductPrice == null ? 0
				: byproductPrice.hasLow() ? byproductPrice.getLow()
				: byproductPrice.hasHigh() ? byproductPrice.getHigh() : 0;
			if (sell > 0)
			{
				revenue += sell - MarketAnalyzer.tax(sell);
				pricedByproducts.add(byproduct + " " + sell);
			}
		}

		double profitPerUnit = revenue - inputCost;
		double roiPercent = inputCost > 0 ? profitPerUnit * 100D / inputCost : 0D;

		if (profitPerUnit < risk.getMinProfitPerUnit())
		{
			candidate.addReject(RejectReason.LOW_MARGIN,
				String.format(Locale.ROOT, "%.0f gp/unit, minimum %.0f",
					profitPerUnit, risk.getMinProfitPerUnit()));
		}
		if (roiPercent < risk.getMinRoiPercent())
		{
			candidate.addReject(RejectReason.LOW_ROI,
				String.format(Locale.ROOT, "%.2f%%, minimum %.2f%%",
					roiPercent, risk.getMinRoiPercent()));
		}

		HourlyPoint outputHour = snapshot.hour(outputId);
		double outputUnitsPerHour = outputHour == null ? 0D
			: Math.max(outputHour.getHighPriceVolume(), outputHour.getLowPriceVolume())
			/ (double) outputQuantity;
		if (outputUnitsPerHour <= 0)
		{
			candidate.addReject(RejectReason.OUTPUT_ILLIQUID, "no output volume in the last hour");
		}

		double bottleneck = Math.min(outputUnitsPerHour, inputUnitsPerHour);
		if (bottleneck == Double.MAX_VALUE)
		{
			bottleneck = 0D;
		}
		if (bottleneck > 0 && bottleneck < risk.getMinHourlyVolume())
		{
			candidate.addReject(RejectReason.LOW_VOLUME,
				String.format(Locale.ROOT, "%.0f units/h, minimum %.0f",
					bottleneck, risk.getMinHourlyVolume()));
		}

		// If there are rejects already accumulated, skip sizing. We can't know easily from builder
		// but recipe evaluation adds rejects before this point; in that case we should build now.
		// To keep code simple, just proceed - builder is fine to build; viable() checked later is on Candidate.

		double unitsPerHour = Math.min(recipe.getEstimatedUnitsPerHour(),
			bottleneck * risk.getParticipationCap());
		if (unitsPerHour <= 0)
		{
			candidate.addReject(RejectReason.LOW_VOLUME, "effective rate is zero");
			return candidate.build();
		}

		long budget = Math.round(ctx.getCapital() * (1D - risk.getCashReservePercent() / 100D));
		long perItem = Math.min(budget, Math.round(ctx.getCapital() * risk.getMaxCapitalPerItemPercent() / 100D));
		int capitalCap = (int) Math.floor(perItem / inputCost);
		if (capitalCap <= 0)
		{
			candidate.addReject(RejectReason.INSUFFICIENT_CAPITAL,
				perItem + " gp available, one unit costs " + Math.round(inputCost));
		}

		int fillCap = (int) Math.floor(unitsPerHour * risk.getMaxFillHours());
		if (fillCap <= 0)
		{
			candidate.addReject(RejectReason.LOW_VOLUME,
				String.format(Locale.ROOT, "%.2f units/h cannot fill one unit in %.1fh",
					unitsPerHour, risk.getMaxFillHours()));
		}

		int quantity = Math.min(capitalCap, Math.min(limitCap, fillCap));
		if (quantity <= 0)
		{
			return candidate.build();
		}

		double processingHours = quantity / (double) recipe.getEstimatedUnitsPerHour();
		double fillHours = quantity / unitsPerHour + processingHours;
		long capitalUsed = Math.round(inputCost * quantity);
		long totalRevenue = Math.round(revenue * quantity);
		long profit = Math.round(profitPerUnit * quantity);
		double roi = capitalUsed > 0 ? profit * 100D / capitalUsed : 0D;
		double gpPerHour = profit / Math.max(fillHours, 0.25D);
		double stability = clamp(1D - fillHours / (risk.getMaxFillHours() * 2D), 0.25D, 1D);
		double confidence = 0.5D + 0.5D * stability;

		Candidate.Builder sized = candidate
			.recipe(recipe)
			.sizing(quantity, capitalUsed, totalRevenue, profit, roi, fillHours, gpPerHour,
				gpPerHour * stability)
			.quality(confidence, stability)
			.maxHoldMinutes(holdingTolerance(fillHours, risk))
			.addNote(String.format(Locale.ROOT, "%.0f gp inputs -> %.0f gp output per unit",
				inputCost, revenue));

		if (toolRequired)
		{
			sized.addNote("tool required in inventory");
		}
		if (!pricedByproducts.isEmpty())
		{
			sized.addNote("byproducts: " + String.join(", ", pricedByproducts));
		}
		return sized.build();
	}

	// ------------------------------------------------------------------
	// Shared helpers
	// ------------------------------------------------------------------

	/** Watchlist/blacklist matching, by item name. */
	private boolean passesFilters(ItemMeta meta, ScanContext ctx)
	{
		String name = meta.getName();
		if (name == null || name.trim().isEmpty())
		{
			return false;
		}
		String key = name.trim().toLowerCase(Locale.ROOT);
		if (ctx.getBlacklist().contains(key))
		{
			return false;
		}
		return !ctx.isWatchlistOnly() || ctx.getWatchlist().isEmpty() || ctx.getWatchlist().contains(key);
	}

	private boolean recipePassesFilters(FactoryRecipe recipe, ScanContext ctx)
	{
		Set<String> blacklist = ctx.getBlacklist();
		if (containsName(blacklist, recipe.getDisplayName())
			|| containsName(blacklist, recipe.getOutputName()))
		{
			return false;
		}
		for (RecipeInput input : recipe.getInputs())
		{
			if (containsName(blacklist, input.getItemName()))
			{
				return false;
			}
		}
		for (String byproduct : recipe.getByproducts())
		{
			if (containsName(blacklist, byproduct))
			{
				return false;
			}
		}
		if (!ctx.isWatchlistOnly() || ctx.getWatchlist().isEmpty())
		{
			return true;
		}
		return containsName(ctx.getWatchlist(), recipe.getDisplayName())
			|| containsName(ctx.getWatchlist(), recipe.getOutputName());
	}

	private static boolean containsName(Set<String> names, String name)
	{
		return name != null && names.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Members items need both a members account and a members world. MEMBERS_ONLY additionally
	 * excludes free-to-play items; F2P_ONLY excludes every members item.
	 */
	private boolean membershipAllows(ItemMeta meta, ScanContext ctx)
	{
		if (!meta.isMembers())
		{
			return ctx.getMembershipMode() != MembershipMode.MEMBERS_ONLY;
		}
		if (ctx.getMembershipMode() == MembershipMode.F2P_ONLY)
		{
			return false;
		}
		return ctx.isMembersAccount() && ctx.isMemberWorld();
	}

	/** Three predicted fill cycles, floored at 30 minutes and capped by the risk profile. */
	private static long holdingTolerance(double fillHours, RiskProfile risk)
	{
		long predictedMinutes = Math.round(fillHours * 60D);
		long target = Math.max(30L, predictedMinutes * 3L);
		return Math.min(target, risk.getMaxHoldCapMinutes());
	}

	private static Comparator<Candidate> bestFirst()
	{
		return (a, b) -> {
			int byScore = Double.compare(b.getScore(), a.getScore());
			return byScore != 0 ? byScore : Long.compare(b.getProfit(), a.getProfit());
		};
	}

	private static String warningSuffix(MarketSnapshot snapshot)
	{
		String warning = snapshot.getWarning();
		return warning == null || warning.isEmpty() ? "" : " | " + warning;
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static final class RoughFlip
	{
		private final ItemMeta meta;
		private final double roughScore;

		private RoughFlip(ItemMeta meta, double roughScore)
		{
			this.meta = meta;
			this.roughScore = roughScore;
		}
	}
}
