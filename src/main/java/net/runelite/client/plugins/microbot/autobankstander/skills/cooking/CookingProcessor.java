package net.runelite.client.plugins.microbot.autobankstander.skills.cooking;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.AnimationID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.GenericCombineProcessor;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Bank-standing Cooking processor.
 *
 * <p>Recipes are modelled as a short pipeline of steps. Assembly recipes delegate to
 * {@link GenericCombineProcessor}; anything that has to be baked on a range uses the
 * direct range interaction implemented in {@link RangeCookStep}. Burnt products are
 * simply deposited with the rest of the inventory on the next banking trip.</p>
 */
@Slf4j
public class CookingProcessor implements BankStandingProcessor {

    private static final long PROGRESS_GRACE_MS = 5000L;
    private static final long STARTUP_GRACE_MS = 1800L;
    private static final long WINE_FERMENT_MS = 12L * 60L * 1000L;

    /** Every cooking range / stove / oven / fire we are willing to use (legacy ObjectID). */
    private static final int[] COOKING_OBJECT_IDS = {
            net.runelite.api.ObjectID.STOVE,
            net.runelite.api.ObjectID.STOVE_9086,
            net.runelite.api.ObjectID.STOVE_9087,
            net.runelite.api.ObjectID.STOVE_12269,
            net.runelite.api.ObjectID.GOBLIN_STOVE,
            net.runelite.api.ObjectID.GOBLIN_STOVE_25441,
            net.runelite.api.ObjectID.SIMPLE_STOVE,
            net.runelite.api.ObjectID.COOKING_STOVE,
            net.runelite.api.ObjectID.STOVE_51540,
            net.runelite.api.ObjectID.COOKING_RANGE,
            net.runelite.api.ObjectID.RANGE,
            net.runelite.api.ObjectID.COOKING_RANGE_4172,
            net.runelite.api.ObjectID.RANGE_7183,
            net.runelite.api.ObjectID.RANGE_7184,
            net.runelite.api.ObjectID.COOKING_RANGE_8750,
            net.runelite.api.ObjectID.RANGE_9682,
            net.runelite.api.ObjectID.RANGE_9736,
            net.runelite.api.ObjectID.RANGE_12102,
            net.runelite.api.ObjectID.RANGE_12611,
            net.runelite.api.ObjectID.STEEL_RANGE,
            net.runelite.api.ObjectID.STEEL_RANGE_13540,
            net.runelite.api.ObjectID.STEEL_RANGE_13541,
            net.runelite.api.ObjectID.FANCY_RANGE,
            net.runelite.api.ObjectID.FANCY_RANGE_13543,
            net.runelite.api.ObjectID.FANCY_RANGE_13544,
            net.runelite.api.ObjectID.COOKING_RANGE_16641,
            net.runelite.api.ObjectID.COOKING_RANGE_16893,
            net.runelite.api.ObjectID.RANGE_21792,
            net.runelite.api.ObjectID.COOKING_RANGE_22154,
            net.runelite.api.ObjectID.RANGE_22713,
            net.runelite.api.ObjectID.RANGE_22714,
            net.runelite.api.ObjectID.RANGE_25730,
            net.runelite.api.ObjectID.RANGE_26181,
            net.runelite.api.ObjectID.RANGE_26182,
            net.runelite.api.ObjectID.RANGE_26183,
            net.runelite.api.ObjectID.RANGE_26184,
            net.runelite.api.ObjectID.RANGE_27516,
            net.runelite.api.ObjectID.RANGE_27517,
            net.runelite.api.ObjectID.RANGE_27724,
            net.runelite.api.ObjectID.RANGE_31631,
            net.runelite.api.ObjectID.COOKING_RANGE_35877,
            net.runelite.api.ObjectID.RANGE_35980,
            net.runelite.api.ObjectID.RANGE_36077,
            net.runelite.api.ObjectID.RANGE_36699,
            net.runelite.api.ObjectID.RANGE_37728,
            net.runelite.api.ObjectID.RANGE_39391,
            net.runelite.api.ObjectID.RANGE_39458,
            net.runelite.api.ObjectID.RANGE_39490,
            net.runelite.api.ObjectID.RANGE_40068,
            net.runelite.api.ObjectID.RANGE_40149,
            net.runelite.api.ObjectID.COOKING_RANGE_41423,
            net.runelite.api.ObjectID.RANGE_47392,
            net.runelite.api.ObjectID.RANGE_47925,
            net.runelite.api.ObjectID.RANGE_48169,
            net.runelite.api.ObjectID.RANGE_50563,
            net.runelite.api.ObjectID.RANGE_54049,
            net.runelite.api.ObjectID.OVEN,
            net.runelite.api.ObjectID.SMALL_OVEN,
            net.runelite.api.ObjectID.SMALL_OVEN_13534,
            net.runelite.api.ObjectID.SMALL_OVEN_13535,
            net.runelite.api.ObjectID.LARGE_OVEN,
            net.runelite.api.ObjectID.LARGE_OVEN_13537,
            net.runelite.api.ObjectID.LARGE_OVEN_13538,
            net.runelite.api.ObjectID.OVEN_52647,
            net.runelite.api.ObjectID.OVEN_52648,
            net.runelite.api.ObjectID.FIRE,
            net.runelite.api.gameval.ObjectID.FIRE_COOK
    };

    private static final String[] KARAMBWAN_THOUGH_OPTIONS = {
            "Cook thoroughly", "Thoroughly", "Cook Thoroughly"
    };

    // -----------------------------------------------------------------
    // Recipe data
    // -----------------------------------------------------------------

    private static final RangeRecipe[] RAW_FOOD_RECIPES = {
            raw("Raw shrimps", 1, ItemID.RAW_SHRIMP, ItemID.SHRIMP, false, ItemID.BURNT_SHRIMP),
            raw("Raw sardine", 1, ItemID.RAW_SARDINE, ItemID.SARDINE, false, ItemID.BURNTFISH5),
            raw("Raw anchovies", 1, ItemID.RAW_ANCHOVIES, ItemID.ANCHOVIES, false, ItemID.BURNTFISH1),
            raw("Raw beef", 1, ItemID.RAW_BEEF, ItemID.COOKED_MEAT, false, ItemID.BURNT_MEAT),
            raw("Raw chicken", 1, ItemID.RAW_CHICKEN, ItemID.COOKED_CHICKEN, false, ItemID.BURNT_CHICKEN),
            raw("Raw rabbit", 1, ItemID.RAW_RABBIT, ItemID.COOKED_RABBIT, false, ItemID.BURNT_MEAT),
            raw("Raw herring", 5, ItemID.RAW_HERRING, ItemID.HERRING, false, ItemID.BURNTFISH3),
            raw("Raw mackerel", 10, ItemID.RAW_MACKEREL, ItemID.MACKEREL, false, ItemID.BURNTFISH3),
            raw("Raw trout", 15, ItemID.RAW_TROUT, ItemID.TROUT, false, ItemID.BURNTFISH2),
            raw("Raw cod", 18, ItemID.RAW_COD, ItemID.COD, false, ItemID.BURNTFISH2),
            raw("Raw pike", 20, ItemID.RAW_PIKE, ItemID.PIKE, false, ItemID.BURNTFISH2),
            raw("Raw salmon", 25, ItemID.RAW_SALMON, ItemID.SALMON, false, ItemID.BURNTFISH2),
            raw("Raw tuna", 30, ItemID.RAW_TUNA, ItemID.TUNA, false, ItemID.BURNTFISH4),
            raw("Raw karambwan", 30, ItemID.TBWT_RAW_KARAMBWAN, ItemID.TBWT_COOKED_KARAMBWAN,
                    true, ItemID.TBWT_BURNT_KARAMBWAN),
            raw("Raw lobster", 40, ItemID.RAW_LOBSTER, ItemID.LOBSTER, false, ItemID.BURNT_LOBSTER),
            raw("Raw bass", 43, ItemID.RAW_BASS, ItemID.BASS, false, ItemID.BURNTFISH4),
            raw("Raw swordfish", 45, ItemID.RAW_SWORDFISH, ItemID.SWORDFISH, false, ItemID.BURNT_SWORDFISH),
            raw("Raw monkfish", 62, ItemID.RAW_MONKFISH, ItemID.MONKFISH, false, ItemID.BURNT_MONKFISH),
            raw("Raw shark", 80, ItemID.RAW_SHARK, ItemID.SHARK, false, ItemID.BURNT_SHARK),
            raw("Raw anglerfish", 84, ItemID.RAW_ANGLERFISH, ItemID.ANGLERFISH, false, ItemID.BURNT_ANGLERFISH)
    };

    private static final RangeRecipe KARAMBWAN_RECIPE =
            raw("Cooked karambwan", 30, ItemID.TBWT_RAW_KARAMBWAN,
                    ItemID.TBWT_COOKED_KARAMBWAN, true, ItemID.TBWT_BURNT_KARAMBWAN);

    private static final RangeRecipe UNCOOKED_MEAT_PIE =
            raw("Uncooked meat pie", 20, ItemID.UNCOOKED_MEAT_PIE, ItemID.MEAT_PIE, false, ItemID.BURNT_PIE);
    private static final RangeRecipe UNCOOKED_APPLE_PIE =
            raw("Uncooked apple pie", 30, ItemID.UNCOOKED_APPLE_PIE, ItemID.APPLE_PIE, false, ItemID.BURNT_PIE);
    private static final RangeRecipe UNCOOKED_BERRY_PIE =
            raw("Uncooked redberry pie", 10, ItemID.UNCOOKED_REDBERRY_PIE,
                    ItemID.REDBERRY_PIE, false, ItemID.BURNT_PIE);
    private static final RangeRecipe UNCOOKED_PIZZA_RECIPE =
            raw("Uncooked pizza", 35, ItemID.UNCOOKED_PIZZA, ItemID.PLAIN_PIZZA, false, ItemID.BURNT_PIZZA);
    private static final RangeRecipe UNCOOKED_CAKE_RECIPE =
            raw("Uncooked cake", 40, ItemID.UNCOOKED_CAKE, ItemID.CAKE, false, ItemID.BURNT_CAKE);
    private static final RangeRecipe BREAD_DOUGH_RECIPE =
            raw("Bread dough", 1, ItemID.BREAD_DOUGH, ItemID.BREAD, false, ItemID.BURNT_BREAD);

    // -----------------------------------------------------------------
    // Public contract
    // -----------------------------------------------------------------

    private final CookingMethod method;
    private final String recipeName;
    private final Plan plan;

    private final List<Step> steps = new ArrayList<>();
    private int stepIndex;
    private boolean awaitingBanking;
    private long wineStartedAt;
    private String lastError = "";

    public CookingProcessor(CookingMethod method, String recipeName) {
        this.method = method == null ? CookingMethod.RAW_FOOD : method;

        List<Plan> available = plansFor(this.method);
        String wanted = normalize(recipeName);
        Plan selected = null;
        for (Plan candidate : available) {
            if (normalize(candidate.name).equals(wanted)) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            String defaultName = normalize(getDefaultRecipe(this.method));
            for (Plan candidate : available) {
                if (normalize(candidate.name).equals(defaultName)) {
                    selected = candidate;
                    break;
                }
            }
        }
        this.plan = selected != null ? selected : available.get(0);
        this.recipeName = this.plan.name;

        rebuildSteps();
    }

    /**
     * Selectable recipe names for a cooking method.
     */
    public static String[] getRecipeNames(CookingMethod method) {
        List<Plan> plans = plansFor(method);
        String[] names = new String[plans.size()];
        for (int i = 0; i < plans.size(); i++) {
            names[i] = plans.get(i).name;
        }
        return names;
    }

    /**
     * Sensible default recipe name for a cooking method.
     */
    public static String getDefaultRecipe(CookingMethod method) {
        if (method == null) {
            return "Raw shrimps";
        }
        switch (method) {
            case PIES:
                return "Uncooked meat pie";
            case PIZZAS:
                return "Plain pizza";
            case CAKES:
                return "Cake";
            case BREAD:
                return "Bread";
            case WINES:
                return "Jug of wine";
            case KARAMBWAN:
                return "Cooked karambwan";
            case RAW_FOOD:
            default:
                return "Raw shrimps";
        }
    }

    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.COOKING);
        int required = plan.requiredLevel;
        if (level < required) {
            log.info("Insufficient Cooking level for {}: need {}, have {}", plan.name, required, level);
            return false;
        }

        if (plan.karambwan) {
            return validateKarambwanSafety(level);
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        return Arrays.asList(plan.withdrawNames);
    }

    @Override
    public boolean hasRequiredItems() {
        Step step = currentStep();
        return step != null && step.worker.hasRequiredItems();
    }

    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Cooking: bank is not open");
            return false;
        }
        if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
            log.info("Cooking: could not switch withdrawal mode to items");
            return false;
        }

        logAndCountBurntProducts();

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            if (!sleepUntil(Rs2Inventory::isEmpty, 3500)) {
                log.info("Cooking: failed to empty inventory at the bank");
                return false;
            }
        }
        Rs2Random.waitEx(140, 70);

        // A fresh inventory means the current batch (and every downstream step) is over.
        rebuildSteps();

        if (plan.wineMode) {
            noteWineFermentation();
        }
        return withdrawForPlan();
    }

    @Override
    public boolean process() {
        ensureStepReady();
        Step step = currentStep();
        if (step == null) {
            return false;
        }

        boolean ok = step.worker.process();
        ensureStepReady();
        return ok;
    }

    @Override
    public boolean canContinueProcessing() {
        Step step = currentStep();
        if (step == null) {
            return false;
        }
        if (step.worker.isActivelyProcessing() || step.worker.hasRequiredItems()) {
            return true;
        }
        // A downstream step may still hold an intermediate product (e.g. uncooked pizza).
        for (int i = stepIndex + 1; i < steps.size(); i++) {
            if (steps.get(i).worker.hasRequiredItems()) {
                return true;
            }
        }
        for (int id : plan.withdrawIds) {
            if (Rs2Bank.count(id) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getStatusMessage() {
        Step step = currentStep();
        if (step == null) {
            return "Cooking: preparing to bank";
        }
        if (plan.wineMode) {
            long remaining = wineStartedAt > 0
                    ? WINE_FERMENT_MS - (System.currentTimeMillis() - wineStartedAt)
                    : WINE_FERMENT_MS;
            if (remaining > 0 && (Rs2Inventory.itemQuantity(ItemID.JUG_UNFERMENTED_WINE) > 0
                    || Rs2Bank.count(ItemID.JUG_UNFERMENTED_WINE) > 0)) {
                return "Making wine - fermenting (~" + Math.max(1, (remaining + 59999) / 60000) + "m left)";
            }
            return "Making wine - banking until fermented";
        }
        if (awaitingBanking) {
            return "Cooking: taking products to bank";
        }
        String detail = step.worker.getStatusMessage();
        return "Cooking " + recipeName.toLowerCase() + (detail.isEmpty() ? "" : " - " + detail);
    }

    @Override
    public boolean isActivelyProcessing() {
        Step step = currentStep();
        return step != null && !awaitingBanking && step.worker.isActivelyProcessing();
    }

    // -----------------------------------------------------------------
    // Pipeline orchestration
    // -----------------------------------------------------------------

    private Step currentStep() {
        if (awaitingBanking || stepIndex < 0 || stepIndex >= steps.size()) {
            return null;
        }
        return steps.get(stepIndex);
    }

    /**
     * Skip steps whose materials have been consumed, and hand control back to the
     * first step once the whole pipeline has run (the next banking trip refills it).
     */
    private void ensureStepReady() {
        if (awaitingBanking) {
            return;
        }
        while (stepIndex < steps.size()) {
            Step step = steps.get(stepIndex);
            if (step.worker.isActivelyProcessing() || step.worker.hasRequiredItems()) {
                return;
            }
            stepIndex++;
        }
        stepIndex = 0;
        awaitingBanking = true;
    }

    private void rebuildSteps() {
        steps.clear();
        stepIndex = 0;
        awaitingBanking = false;

        for (GenericCombineProcessor.Recipe combineRecipe : plan.combines) {
            String label = plan.wineMode
                    ? "Making unfermented wine"
                    : "Making " + combineRecipe.getName().toLowerCase();
            steps.add(new Step(new GenericCombineProcessor(combineRecipe, label)));
        }
        if (plan.cakeAssembly) {
            steps.add(new Step(new CakeAssemblyStep()));
        }
        if (plan.cookCandidates != null && plan.cookCandidates.length > 0) {
            steps.add(new Step(new RangeCookStep(plan.cookCandidates)));
        }
    }

    // -----------------------------------------------------------------
    // Banking helpers
    // -----------------------------------------------------------------

    private void logAndCountBurntProducts() {
        int burnt = 0;
        int cooked = 0;
        for (RangeRecipe recipe : plan.cookCandidates == null ? new RangeRecipe[0] : plan.cookCandidates) {
            for (int burntId : recipe.burntIds) {
                burnt += Rs2Inventory.itemQuantity(burntId);
            }
            cooked += Rs2Inventory.itemQuantity(recipe.cookedId);
        }
        if (burnt > 0) {
            log.info("Depositing {} burnt cooking product(s) before continuing", burnt);
        }
        if (cooked > 0) {
            log.info("Depositing {} cooked product(s)", cooked);
        }
    }

    private boolean withdrawForPlan() {
        // "Any raw food" decides which raw item to cook based on what the bank holds.
        if (plan.dynamicCook) {
            int selected = -1;
            for (RangeRecipe recipe : plan.cookCandidates) {
                if (Rs2Bank.count(recipe.rawId) > 0) {
                    selected = recipe.rawId;
                    break;
                }
            }
            if (selected < 0) {
                log.info("No raw cooking ingredients left in the bank");
                return false;
            }
            plan.withdrawIds = new int[]{selected};
            plan.withdrawPer = new int[]{1};
            plan.withdrawNames = new String[]{"Raw ingredient"};
        }

        int freeSlots = Rs2Inventory.emptySlotCount();
        if (freeSlots <= 0) {
            log.info("No free inventory slots for cooking materials");
            return false;
        }

        int slotsPerBatch = 0;
        int maxBatches = Integer.MAX_VALUE;
        for (int i = 0; i < plan.withdrawIds.length; i++) {
            int id = plan.withdrawIds[i];
            int perBatch = plan.withdrawPer[i];
            int available = Rs2Bank.count(id);
            maxBatches = Math.min(maxBatches, available / Math.max(1, perBatch));
            slotsPerBatch += isStackable(id) ? 1 : perBatch;
        }
        if (slotsPerBatch <= 0) {
            return false;
        }
        // Keep batches human-sized; the same 11-14 range is used by other bank standers.
        maxBatches = Math.min(maxBatches, Math.min(freeSlots / slotsPerBatch, Rs2Random.between(11, 14)));
        if (maxBatches < 1) {
            log.info("Not enough materials or slots for a {} batch", plan.name);
            return false;
        }

        for (int i = 0; i < plan.withdrawIds.length; i++) {
            int id = plan.withdrawIds[i];
            int quantity = maxBatches * plan.withdrawPer[i];
            if (!withdraw(id, quantity, isStackable(id))) {
                return false;
            }
            Rs2Random.waitEx(120, 60);
        }
        return true;
    }

    private boolean withdraw(int itemId, int amount, boolean stackable) {
        if (amount <= 0) {
            return false;
        }
        int before = Rs2Inventory.itemQuantity(itemId);
        int bankQuantity = Rs2Bank.count(itemId);
        int expectedGain = stackable && amount > 1 ? bankQuantity : Math.min(amount, bankQuantity);
        if (expectedGain <= 0) {
            log.info("Bank has no remaining item {}", itemId);
            return false;
        }

        boolean requested;
        if (stackable && amount > 1) {
            requested = Rs2Bank.withdrawAll(itemId);
        } else if (expectedGain == 1) {
            requested = Rs2Bank.withdrawOne(itemId);
        } else {
            requested = Rs2Bank.withdrawX(itemId, expectedGain);
        }
        if (!requested) {
            log.info("Bank rejected withdrawal of item {} (amount {})", itemId, expectedGain);
            return false;
        }
        return sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= before + expectedGain, 3500);
    }

    private boolean isStackable(int itemId) {
        try {
            return Rs2ItemModel.createFromCache(itemId, 1, -1).isStackable();
        } catch (Exception e) {
            log.debug("Could not determine stackability of item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Safety / status helpers
    // -----------------------------------------------------------------

    private boolean validateKarambwanSafety(int level) {
        boolean questDone = Rs2Player.getQuestState(Quest.TAI_BWO_WANNAI_TRIO) == QuestState.FINISHED;
        boolean vessel = Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL)
                || Rs2Equipment.isWearing(ItemID.TBWT_KARAMBWAN_VESSEL);
        boolean burnProof = level >= 99;

        log.info("Karambwan safety check: cooking={}, Tai Bwo Wannai Trio={}, vessel={}, burn-proof level={}",
                level, questDone ? "complete" : "NOT complete", vessel ? "present" : "absent",
                burnProof ? "yes" : "no (burnt karambwan will be deposited)");

        if (!questDone) {
            log.info("Refusing to cook karambwan: complete Tai Bwo Wannai Trio and speak to Tinsay "
                    + "first, otherwise the 'poorly' option produces poison karambwan.");
            return false;
        }
        if (level < KARAMBWAN_RECIPE.level) {
            log.info("Refusing to cook karambwan: need level {} for the thorough option", KARAMBWAN_RECIPE.level);
            return false;
        }
        if (!vessel) {
            log.info("No karambwan vessel found - it is a fishing tool, so it does not prevent "
                    + "poisoning; the thorough cooking option is what keeps the output safe.");
        }
        if (!burnProof) {
            log.info("Cooking level below 99: karambwan may burn. Burnt karambwan will be deposited "
                    + "at the bank; no poison risk when cooking thoroughly.");
        }
        return true;
    }

    private void noteWineFermentation() {
        if (wineStartedAt <= 0) {
            wineStartedAt = System.currentTimeMillis();
            log.info("Started wine batch. Unfermented wine turns into jug of wine after about "
                    + "{} minutes; continuing to bank while it ferments.", WINE_FERMENT_MS / 60000);
        }
    }

    // -----------------------------------------------------------------
    // Step definitions
    // -----------------------------------------------------------------

    private static final class Step {
        final BankStandingProcessor worker;

        Step(BankStandingProcessor worker) {
            this.worker = worker;
        }
    }

    /** Cooks one raw item (or the first available one for "Any raw food") on a nearby range. */
    private static final class RangeCookStep implements BankStandingProcessor {
        private final RangeRecipe[] candidates;
        private RangeRecipe recipe;

        private boolean batchActive;
        private long batchStartTime;
        private long lastProgressTime;
        private int rawAtStart;
        private int cookedAtStart;
        private int burntAtStart;

        RangeCookStep(RangeRecipe[] candidates) {
            this.candidates = candidates;
            if (candidates.length == 1) {
                this.recipe = candidates[0];
            }
        }

        private RangeRecipe resolve() {
            if (recipe != null) {
                return recipe;
            }
            for (RangeRecipe candidate : candidates) {
                if (Rs2Inventory.itemQuantity(candidate.rawId) > 0) {
                    recipe = candidate;
                    return recipe;
                }
            }
            for (RangeRecipe candidate : candidates) {
                if (Rs2Bank.count(candidate.rawId) > 0) {
                    recipe = candidate;
                    return recipe;
                }
            }
            return null;
        }

        @Override
        public boolean validate() {
            return Rs2Player.getRealSkillLevel(Skill.COOKING) >= (recipe == null
                    ? 1 : recipe.level);
        }

        @Override
        public List<String> getBankingRequirements() {
            return Collections.singletonList(recipe == null ? "Raw food" : recipe.rawName);
        }

        @Override
        public boolean hasRequiredItems() {
            RangeRecipe selected = resolve();
            return selected != null && Rs2Inventory.itemQuantity(selected.rawId) > 0;
        }

        @Override
        public boolean performBanking() {
            // The outer CookingProcessor owns banking so it can deposit burnt items
            // and refill every step of the pipeline in one pass.
            return false;
        }

        @Override
        public boolean process() {
            if (batchActive) {
                if (tracksProgress()) {
                    return true;
                }
                batchActive = false;
            }

            RangeRecipe selected = resolve();
            if (selected == null || Rs2Inventory.itemQuantity(selected.rawId) <= 0) {
                return false;
            }

            Rs2TileObjectModel range = Microbot.getRs2TileObjectCache().query()
                    .withIds(COOKING_OBJECT_IDS)
                    .nearest();
            if (range == null) {
                log.info("No cooking range/stove found nearby for {}", selected.rawName);
                return false;
            }
            if (!Rs2Camera.isTileOnScreen(range.getLocalLocation())) {
                Rs2Camera.turnTo(range.getLocalLocation());
                Rs2Random.waitEx(120, 60);
            }

            if (!Rs2Inventory.useItemOnObject(selected.rawId, range.getId())) {
                log.info("Could not use {} on the cooking range", selected.rawName);
                return false;
            }

            boolean widgetOpen = sleepUntil(() -> Rs2Widget.isProductionWidgetOpen() ||
                    Rs2Dialogue.isInDialogue(), 10000);
            if (!widgetOpen) {
                log.info("No cooking dialogue appeared for {}", selected.rawName);
                return false;
            }
            Rs2Random.waitEx(150, 70);

            if (selected.karambwan) {
                if (!selectThoroughCooking()) {
                    return false;
                }
            } else {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }

            rawAtStart = Rs2Inventory.itemQuantity(selected.rawId);
            cookedAtStart = Rs2Inventory.itemQuantity(selected.cookedId);
            burntAtStart = 0;
            for (int burntId : selected.burntIds) {
                burntAtStart += Rs2Inventory.itemQuantity(burntId);
            }
            batchActive = true;
            batchStartTime = System.currentTimeMillis();
            lastProgressTime = batchStartTime;
            Rs2Random.waitEx(180, 90);
            return true;
        }

        @Override
        public boolean canContinueProcessing() {
            if (hasRequiredItems()) {
                return true;
            }
            for (RangeRecipe candidate : candidates) {
                if (Rs2Bank.count(candidate.rawId) > 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getStatusMessage() {
            return batchActive ? "cooking " + (recipe == null ? "food" : recipe.rawName.toLowerCase())
                    : "";
        }

        @Override
        public boolean isActivelyProcessing() {
            if (!batchActive) {
                return false;
            }
            if (Rs2Player.isAnimating(1000) || Rs2Dialogue.isInDialogue()
                    || Rs2Widget.isProductionWidgetOpen()) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - batchStartTime < STARTUP_GRACE_MS) {
                return true;
            }
            return now - lastProgressTime < PROGRESS_GRACE_MS;
        }

        private boolean tracksProgress() {
            RangeRecipe selected = recipe;
            if (selected == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            int raw = Rs2Inventory.itemQuantity(selected.rawId);
            int burntNow = 0;
            for (int burntId : selected.burntIds) {
                burntNow += Rs2Inventory.itemQuantity(burntId);
            }
            if (raw < rawAtStart
                    || Rs2Inventory.itemQuantity(selected.cookedId) > cookedAtStart
                    || burntNow > burntAtStart) {
                lastProgressTime = now;
            }
            // No raw food left means the whole batch has been cooked.
            return raw > 0 && now - lastProgressTime < PROGRESS_GRACE_MS;
        }

        private boolean selectThoroughCooking() {
            for (String option : KARAMBWAN_THOUGH_OPTIONS) {
                if (Rs2Dialogue.hasDialogueOption(option)) {
                    if (Rs2Dialogue.clickOption(option)) {
                        log.info("Karambwan: selected exact '{}' cooking option", option);
                        return true;
                    }
                    log.info("Karambwan: failed to click '{}' option", option);
                    return false;
                }
            }
            if (Rs2Dialogue.getDialogueOptions().isEmpty() && Rs2Widget.isProductionWidgetOpen()) {
                // Since the 2024 update the default cooking option is "thoroughly".
                log.info("Karambwan: no method options offered; using the default (thoroughly) "
                        + "cooking option on the production widget");
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                return true;
            }
            log.info("Karambwan: no exact thorough option was offered - refusing to cook "
                    + "because the 'poorly' option would poison the player");
            return false;
        }
    }

    /** Cake needs three ingredients added to the tin one at a time; there are no intermediate IDs. */
    private static final class CakeAssemblyStep implements BankStandingProcessor {
        private static final int[] INGREDIENTS = {
                ItemID.EGG, ItemID.POT_FLOUR, ItemID.BUCKET_MILK
        };

        @Override
        public boolean validate() {
            return true;
        }

        @Override
        public List<String> getBankingRequirements() {
            return Arrays.asList("Cake tin", "Egg", "Pot of flour", "Bucket of milk");
        }

        @Override
        public boolean hasRequiredItems() {
            if (Rs2Inventory.itemQuantity(ItemID.UNCOOKED_CAKE) > 0) {
                return true;
            }
            if (Rs2Inventory.itemQuantity(ItemID.CAKE_TIN) < 1) {
                return false;
            }
            for (int ingredient : INGREDIENTS) {
                if (Rs2Inventory.itemQuantity(ingredient) < 1) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean performBanking() {
            return false; // handled by the outer processor
        }

        @Override
        public boolean process() {
            if (Rs2Inventory.itemQuantity(ItemID.UNCOOKED_CAKE) > 0) {
                return true;
            }
            if (!hasRequiredItems()) {
                return false;
            }

            for (int ingredient : INGREDIENTS) {
                if (Rs2Inventory.itemQuantity(ItemID.UNCOOKED_CAKE) > 0) {
                    break;
                }
                if (Rs2Inventory.itemQuantity(ingredient) < 1) {
                    break;
                }
                int before = Rs2Inventory.itemQuantity(ingredient);
                if (!Rs2Inventory.combine(ItemID.CAKE_TIN, ingredient)) {
                    log.info("Cake: could not add ingredient {} to the cake tin", ingredient);
                    return false;
                }
                boolean confirmed = sleepUntil(() -> Rs2Widget.isProductionWidgetOpen() ||
                        Rs2Dialogue.isInDialogue(), 4000);
                if (confirmed) {
                    Rs2Random.waitEx(140, 70);
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                }
                boolean consumed = sleepUntil(() -> Rs2Inventory.itemQuantity(ingredient) < before
                        || Rs2Inventory.itemQuantity(ItemID.UNCOOKED_CAKE) > 0, 5000);
                if (!consumed) {
                    log.info("Cake: ingredient {} was not consumed by the cake tin", ingredient);
                    return false;
                }
                Rs2Random.waitEx(150, 75);
            }

            boolean made = sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.UNCOOKED_CAKE) > 0, 4000);
            if (!made) {
                log.info("Cake: no uncooked cake was produced from the cake tin ingredients");
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueProcessing() {
            return hasRequiredItems() || Rs2Bank.count(ItemID.CAKE_TIN) > 0;
        }

        @Override
        public String getStatusMessage() {
            return "assembling uncooked cake";
        }
    }

    // -----------------------------------------------------------------
    // Plan construction
    // -----------------------------------------------------------------

    private static final class Plan {
        final String name;
        final List<GenericCombineProcessor.Recipe> combines = new ArrayList<>();
        RangeRecipe[] cookCandidates;
        int requiredLevel = 1;
        boolean cakeAssembly;
        boolean wineMode;
        boolean karambwan;
        boolean dynamicCook;

        int[] withdrawIds = new int[0];
        int[] withdrawPer = new int[0];
        String[] withdrawNames = new String[0];

        Plan(String name) {
            this.name = name;
        }
    }

    private static List<Plan> plansFor(CookingMethod method) {
        List<Plan> plans = new ArrayList<>();
        if (method == null) {
            return plansFor(CookingMethod.RAW_FOOD);
        }

        switch (method) {
            case RAW_FOOD:
                Plan any = cookOnly("Any raw food", RAW_FOOD_RECIPES[0]);
                any.cookCandidates = RAW_FOOD_RECIPES;
                any.dynamicCook = true;
                any.withdrawNames = new String[]{"Any raw fish or meat"};
                plans.add(any);
                for (RangeRecipe recipe : RAW_FOOD_RECIPES) {
                    plans.add(cookOnly(recipe.rawName, recipe));
                }
                break;

            case KARAMBWAN:
                Plan karambwan = cookOnly("Cooked karambwan", KARAMBWAN_RECIPE);
                karambwan.karambwan = true;
                plans.add(karambwan);
                break;

            case PIES:
                plans.add(cookOnly("Uncooked meat pie", find("Uncooked meat pie", RAW_FOOD_RECIPES)));
                break;

            case PIZZAS:
                plans.add(cookOnly("Uncooked pizza", find("Uncooked pizza", RAW_FOOD_RECIPES)));
                break;

            case CAKES:
                plans.add(cookOnly("Uncooked cake", find("Uncooked cake", RAW_FOOD_RECIPES)));
                break;

            case BREAD:
                plans.add(cookOnly("Bread dough", find("Bread dough", RAW_FOOD_RECIPES)));
                break;

            case WINES:
                break;

            default:
                break;
        }
        return plans;
    }

    private static RangeRecipe find(String name, RangeRecipe[] recipes) {
        for (RangeRecipe recipe : recipes) {
            if (recipe.rawName.equalsIgnoreCase(name)) {
                return recipe;
            }
        }
        throw new IllegalStateException("Missing cooking recipe data: " + name);
    }

    private static Plan cookOnly(String name, RangeRecipe recipe) {
        Plan plan = new Plan(name);
        plan.cookCandidates = new RangeRecipe[]{recipe};
        plan.requiredLevel = recipe.level;
        plan.karambwan = recipe.karambwan;
        plan.withdrawIds = new int[]{recipe.rawId};
        plan.withdrawPer = new int[]{1};
        plan.withdrawNames = new String[]{recipe.rawName};
        return plan;
    }

    private static RangeRecipe raw(String rawName, int level, int rawId, int cookedId,
                                   boolean karambwan, int... burntIds) {
        return new RangeRecipe(rawName, level, rawId, cookedId, karambwan, burntIds);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static final class RangeRecipe {
        final String rawName;
        final int level;
        final int rawId;
        final int cookedId;
        final int[] burntIds;
        final boolean karambwan;

        RangeRecipe(String rawName, int level, int rawId, int cookedId,
                    boolean karambwan, int[] burntIds) {
            this.rawName = rawName;
            this.level = level;
            this.rawId = rawId;
            this.cookedId = cookedId;
            this.karambwan = karambwan;
            this.burntIds = burntIds == null ? new int[0] : burntIds.clone();
        }
    }
}
