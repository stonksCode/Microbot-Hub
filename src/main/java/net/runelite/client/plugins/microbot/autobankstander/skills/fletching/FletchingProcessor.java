package net.runelite.client.plugins.microbot.autobankstander.skills.fletching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.MakeDialogueHelper;
import net.runelite.client.plugins.microbot.autobankstander.skills.fletching.enums.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class FletchingProcessor implements BankStandingProcessor {

    /** A single fletching recipe derived from the selected enum values. */
    private static final class Recipe {
        private final int primaryId;
        private final int secondaryId;
        private final int productId;
        private final String name;
        /** Knives and similar tools occupy a slot but are not consumed by the recipe. */
        private final boolean secondaryIsTool;
        /** Primary materials consumed per finished product (shields need two logs). */
        private final int primaryPerProduct;
        /** Products made from one primary material (shaft cutting makes many). */
        private final int productsPerPrimary;
        /** Wording variants accepted in the make dialogue for this product. */
        private final String[] dialogueNames;

        private Recipe(int primaryId, int secondaryId, int productId, String name,
                       boolean secondaryIsTool, int primaryPerProduct) {
            this(primaryId, secondaryId, productId, name, secondaryIsTool, primaryPerProduct, 1, name);
        }

        private Recipe(int primaryId, int secondaryId, int productId, String name,
                       boolean secondaryIsTool, int primaryPerProduct, int productsPerPrimary,
                       String... dialogueNames) {
            this.primaryId = primaryId;
            this.secondaryId = secondaryId;
            this.productId = productId;
            this.name = name;
            this.secondaryIsTool = secondaryIsTool;
            this.primaryPerProduct = Math.max(1, primaryPerProduct);
            this.productsPerPrimary = Math.max(1, productsPerPrimary);
            this.dialogueNames = dialogueNames != null && dialogueNames.length > 0
                    ? dialogueNames
                    : new String[]{name};
        }
    }

    private FletchingMode mode;
    private DartType selectedDart;
    private BoltType selectedBolt;
    private ArrowType selectedArrow;
    private JavelinType selectedJavelin;
    private ArrowShaftType selectedArrowShaft;
    private JavelinShaftType selectedJavelinShaft;
    private BowType selectedBow;
    private CrossbowType selectedCrossbow;
    private ShieldType selectedShield;

    // Craft batch tracking. Product/material counts are compared instead of assuming
    // that "hasItem" turning false means a Make All batch has finished.
    private boolean currentlyMaking;
    private int trackedPrimaryId = -1;
    private int trackedSecondaryId = -1;
    private int trackedProductId = -1;
    private int primaryCountAtStart;
    private int secondaryCountAtStart;
    private int productCountAtStart;
    private long processingStartTime;
    private long lastProgressTime;

    private final Map<Integer, Boolean> stackableCache = new HashMap<>();

    public FletchingProcessor(FletchingMode mode, DartType dart, BoltType bolt, ArrowType arrow,
                              JavelinType javelin, ArrowShaftType arrowShaft, JavelinShaftType javelinShaft,
                              BowType bow, CrossbowType crossbow, ShieldType shield) {
        log.info("Initializing FletchingProcessor with mode: {}", mode);
        this.mode = mode;
        this.selectedDart = dart;
        this.selectedBolt = bolt;
        this.selectedArrow = arrow;
        this.selectedJavelin = javelin;
        this.selectedArrowShaft = arrowShaft;
        this.selectedJavelinShaft = javelinShaft;
        this.selectedBow = bow;
        this.selectedCrossbow = crossbow;
        this.selectedShield = shield;
        resetBatchTracking();
    }

    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.FLETCHING);
        log.info("Fletching level: {}, selected mode: {}", level, mode);

        int requiredLevel = -1;
        switch (mode) {
            case DARTS:
                if (selectedDart == null) {
                    log.info("No dart type selected - please configure a specific dart type");
                    return false;
                }
                requiredLevel = selectedDart.getLevelRequired();
                break;
            case BOLTS:
                if (selectedBolt == null) {
                    log.info("No bolt type selected - please configure a specific bolt type");
                    return false;
                }
                requiredLevel = selectedBolt.getLevelRequired();
                break;
            case ARROWS:
                if (selectedArrow == null) {
                    log.info("No arrow type selected - please configure a specific arrow type");
                    return false;
                }
                requiredLevel = selectedArrow.getLevelRequired();
                break;
            case JAVELINS:
                if (selectedJavelin == null) {
                    log.info("No javelin type selected - please configure a specific javelin type");
                    return false;
                }
                requiredLevel = selectedJavelin.getLevelRequired();
                break;
            case ARROW_SHAFTS:
                if (selectedArrowShaft == null) {
                    log.info("No arrow shaft type selected - please configure a log type");
                    return false;
                }
                requiredLevel = selectedArrowShaft.getLevelRequired();
                break;
            case JAVELIN_SHAFTS:
                if (selectedJavelinShaft == null) {
                    log.info("No javelin shaft type selected - please configure a log type");
                    return false;
                }
                requiredLevel = selectedJavelinShaft.getLevelRequired();
                break;
            case BOWS:
                if (selectedBow == null) {
                    log.info("No bow type selected - please configure a specific bow type");
                    return false;
                }
                requiredLevel = selectedBow.getLevelRequired();
                break;
            case CROSSBOWS:
                if (selectedCrossbow == null) {
                    log.info("No crossbow type selected - please configure a specific crossbow type");
                    return false;
                }
                requiredLevel = selectedCrossbow.getLevelRequired();
                break;
            case SHIELDS:
                if (selectedShield == null) {
                    log.info("No shield type selected - please configure a specific shield type");
                    return false;
                }
                requiredLevel = selectedShield.getLevelRequired();
                break;
        }

        if (level < requiredLevel) {
            log.info("Insufficient fletching level: need {}, have {}", requiredLevel, level);
            return false;
        }

        log.info("Fletching processor validation successful");
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();

        switch (mode) {
            case DARTS:
                if (selectedDart != null) {
                    requirements.add("Dart tips");
                    requirements.add("Feathers");
                }
                break;
            case BOLTS:
                if (selectedBolt != null) {
                    requirements.add("Unfinished bolts or bolt tips");
                    requirements.add("Feathers or bolts");
                }
                break;
            case ARROWS:
                if (selectedArrow != null) {
                    requirements.add("Arrow materials");
                    requirements.add("Feathers or arrowtips");
                }
                break;
            case JAVELINS:
                if (selectedJavelin != null) {
                    requirements.add("Javelin heads");
                    requirements.add("Javelin shafts");
                }
                break;
            case ARROW_SHAFTS:
                requirements.add("Logs");
                requirements.add("Knife");
                break;
            case JAVELIN_SHAFTS:
                requirements.add("Logs");
                requirements.add("Knife");
                break;
            case BOWS:
                if (selectedBow != null) {
                    requirements.add("Logs or unstrung bows");
                    requirements.add("Knife or bow strings");
                }
                break;
            case CROSSBOWS:
                if (selectedCrossbow != null) {
                    requirements.add("Crossbow materials");
                    requirements.add("Knife, limbs, or strings");
                }
                break;
            case SHIELDS:
                if (selectedShield != null) {
                    requirements.add("Logs");
                    requirements.add("Knife");
                }
                break;
        }

        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        Recipe recipe = getRecipe();
        return recipe != null &&
                Rs2Inventory.itemQuantity(recipe.primaryId) >= recipe.primaryPerProduct &&
                Rs2Inventory.itemQuantity(recipe.secondaryId) >= 1;
    }

    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }

        // Stackable materials must be withdrawn as items; noted stacks cannot be fletched.
        if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
            log.info("Could not switch bank withdrawal mode to items");
            return false;
        }

        Rs2Bank.depositAll();
        sleepUntil(() -> Rs2Inventory.isEmpty(), 3000);

        // Banking means a fresh inventory, so any Make All batch is over.
        resetBatchTracking();

        Recipe recipe = getRecipe();
        if (recipe == null) {
            return false;
        }

        return withdrawMaterials(recipe);
    }

    @Override
    public boolean process() {
        Recipe recipe = getRecipe();
        if (recipe == null) {
            return false;
        }

        if (currentlyMaking) {
            if (tracksProgress(recipe)) {
                return true;
            }

            // Output and materials have stopped changing: the Make All batch has finished.
            log.info("Finished making {} - preparing another batch", recipe.name);
            resetBatchTracking();
        }

        if (!hasRequiredItems()) {
            return false;
        }

        int productCountBefore = Rs2Inventory.itemQuantity(recipe.productId);
        log.info("Combining materials for {} (primary={} x{}, secondary={} x{}, productBefore={})",
                recipe.name,
                recipe.primaryId, Rs2Inventory.itemQuantity(recipe.primaryId),
                recipe.secondaryId, Rs2Inventory.itemQuantity(recipe.secondaryId),
                productCountBefore);
        if (!Rs2Inventory.combine(recipe.primaryId, recipe.secondaryId)) {
            MakeDialogueHelper.logState("Combine failed for " + recipe.name);
            return false;
        }

        // The make interface opens on the next client tick.
        Rs2Random.waitEx(90, 45);
        if (!handleMakeDialogue(recipe, productCountBefore)) {
            MakeDialogueHelper.logState("Make dialogue handling failed for " + recipe.name);
            return false;
        }

        startBatchTracking(recipe);
        // Small, variable pause after clicking through the dialogue.
        Rs2Random.waitEx(180, 90);
        return true;
    }

    @Override
    public boolean canContinueProcessing() {
        Recipe recipe = getRecipe();
        if (recipe == null) {
            return false;
        }
        if (hasRequiredItems()) {
            return true;
        }

        return Rs2Bank.count(recipe.primaryId) > 0 && Rs2Bank.count(recipe.secondaryId) > 0;
    }

    @Override
    public String getStatusMessage() {
        if (currentlyMaking) {
            Recipe recipe = getRecipe();
            return "Making " + (recipe != null ? recipe.name.toLowerCase() : "items") + "...";
        }
        return "Fletching " + mode.getDisplayName().toLowerCase() + "...";
    }

    @Override
    public boolean isActivelyProcessing() {
        if (!currentlyMaking) {
            return false;
        }
        if (Rs2Dialogue.isInDialogue() || Rs2Widget.isProductionWidgetOpen()
                || Rs2Player.isAnimating(1000)) {
            return true;
        }

        long now = System.currentTimeMillis();
        // Give the first crafting animation time to start before deciding it is stuck.
        if (now - processingStartTime < 1500) {
            return true;
        }
        return now - lastProgressTime < 4000;
    }

    private Recipe getRecipe() {
        switch (mode) {
            case DARTS:
                return selectedDart == null ? null : new Recipe(
                        selectedDart.getTipId(), selectedDart.getFeatherId(), selectedDart.getProductId(),
                        selectedDart.getName(), false, 1);
            case BOLTS:
                return selectedBolt == null ? null : new Recipe(
                        selectedBolt.getMaterialOneId(), selectedBolt.getMaterialTwoId(), selectedBolt.getProductId(),
                        selectedBolt.getName(), false, 1);
            case ARROWS:
                return selectedArrow == null ? null : new Recipe(
                        selectedArrow.getMaterialOneId(), selectedArrow.getMaterialTwoId(), selectedArrow.getProductId(),
                        selectedArrow.getName(), false, 1);
            case JAVELINS:
                return selectedJavelin == null ? null : new Recipe(
                        selectedJavelin.getHeadId(), selectedJavelin.getShaftId(), selectedJavelin.getProductId(),
                        selectedJavelin.getName(), false, 1);
            case ARROW_SHAFTS:
                return selectedArrowShaft == null ? null : new Recipe(
                        selectedArrowShaft.getLogId(), selectedArrowShaft.getKnifeId(),
                        selectedArrowShaft.getProductId(), "Arrow shafts", true, 1,
                        selectedArrowShaft.getShaftsPerLog(), selectedArrowShaft.getProductNames());
            case JAVELIN_SHAFTS:
                return selectedJavelinShaft == null ? null : new Recipe(
                        selectedJavelinShaft.getLogId(), selectedJavelinShaft.getKnifeId(),
                        selectedJavelinShaft.getProductId(), "Javelin shafts", true, 1,
                        selectedJavelinShaft.getShaftsPerLog(), selectedJavelinShaft.getProductNames());
            case BOWS:
                return selectedBow == null ? null : new Recipe(
                        selectedBow.getMaterialOneId(), selectedBow.getMaterialTwoId(), selectedBow.getProductId(),
                        selectedBow.getName(), isKnife(selectedBow.getMaterialTwoId()), 1);
            case CROSSBOWS:
                return selectedCrossbow == null ? null : new Recipe(
                        selectedCrossbow.getMaterialOneId(), selectedCrossbow.getMaterialTwoId(),
                        selectedCrossbow.getProductId(), selectedCrossbow.getName(),
                        isKnife(selectedCrossbow.getMaterialTwoId()), 1);
            case SHIELDS:
                return selectedShield == null ? null : new Recipe(
                        selectedShield.getLogId(), selectedShield.getKnifeId(), selectedShield.getProductId(),
                        selectedShield.getName(), true, selectedShield.getLogsRequired());
            default:
                return null;
        }
    }

    private static boolean isKnife(int itemId) {
        return itemId == ItemID.KNIFE || itemId == ItemID.BRONZE_KNIFE;
    }

    private boolean isStackable(int itemId) {
        return stackableCache.computeIfAbsent(itemId, id -> {
            try {
                return Rs2ItemModel.createFromCache(id, 1, -1).isStackable();
            } catch (Exception e) {
                log.debug("Could not determine stackability for item {}: {}", id, e.getMessage());
                return false;
            }
        });
    }

    /**
     * Withdraw materials based on real free-slot capacity and stackability.
     * Stackables are withdrawn with Withdraw-All (they only ever need one slot),
     * while non-stackables are budgeted so the inventory does not overflow.
     */
    private boolean withdrawMaterials(Recipe recipe) {
        int emptySlots = Rs2Inventory.emptySlotCount();
        if (emptySlots <= 0) {
            log.info("No free inventory slots available");
            return false;
        }

        int bankPrimary = Rs2Bank.count(recipe.primaryId);
        int bankSecondary = Rs2Bank.count(recipe.secondaryId);
        if (bankPrimary < recipe.primaryPerProduct || bankSecondary < 1) {
            log.info("Bank does not have enough materials: primary={}, secondary={}", bankPrimary, bankSecondary);
            return false;
        }

        boolean primaryStackable = isStackable(recipe.primaryId);
        boolean secondaryStackable = isStackable(recipe.secondaryId);
        int usableSlots = emptySlots - (recipe.secondaryIsTool ? 1 : 0);
        if (usableSlots <= 0) {
            return false;
        }

        int primaryQty;
        int secondaryQty;

        if (recipe.secondaryIsTool) {
            // Reserve one slot for the knife and fill the rest with the consumed material.
            int products = usableSlots / recipe.primaryPerProduct;
            primaryQty = Math.min(bankPrimary, products * recipe.primaryPerProduct);
            secondaryQty = 1;
        } else if (primaryStackable && secondaryStackable) {
            // Both stacks fit in a single slot each - take the whole stack.
            primaryQty = bankPrimary;
            secondaryQty = bankSecondary;
        } else if (primaryStackable) {
            secondaryQty = Math.min(bankSecondary, usableSlots - 1);
            primaryQty = bankPrimary;
        } else if (secondaryStackable) {
            primaryQty = Math.min(bankPrimary, usableSlots - 1);
            secondaryQty = bankSecondary;
        } else {
            // Neither material stacks: split the free slots, with slightly varied batch sizes.
            int target = Math.min(usableSlots / 2, Rs2Random.between(11, 14));
            primaryQty = Math.min(bankPrimary, target);
            secondaryQty = Math.min(bankSecondary, target);
        }

        if (primaryQty < recipe.primaryPerProduct || secondaryQty < 1) {
            log.info("Not enough usable slots for a full batch (free={}, primary={}, secondary={})",
                    emptySlots, primaryQty, secondaryQty);
            return false;
        }

        log.info("Withdrawing {} x {} and {} x {} (free slots: {})",
                primaryQty, recipe.primaryId, secondaryQty, recipe.secondaryId, emptySlots);

        if (!withdraw(recipe.primaryId, primaryQty, primaryStackable)) {
            return false;
        }
        Rs2Random.waitEx(140, 70);
        // Tools are always withdrawn one at a time, even if several are banked.
        return withdraw(recipe.secondaryId, secondaryQty, secondaryStackable);
    }

    private boolean withdraw(int itemId, int amount, boolean stackable) {
        if (amount <= 0) {
            return false;
        }

        int before = Rs2Inventory.itemQuantity(itemId);
        int bankQty = Rs2Bank.count(itemId);
        int expectedGain = stackable && amount > 1 ? bankQty : amount;
        if (expectedGain <= 0) {
            return false;
        }

        boolean requested;
        if (stackable && amount > 1) {
            requested = Rs2Bank.withdrawAll(itemId);
        } else if (amount == 1) {
            requested = Rs2Bank.withdrawOne(itemId);
        } else {
            requested = Rs2Bank.withdrawX(itemId, amount);
        }

        if (!requested) {
            log.info("Bank rejected withdrawal of item {} (amount {})", itemId, amount);
            return false;
        }

        int expectedTotal = before + expectedGain;
        return sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= expectedTotal, 3500);
    }

    private void startBatchTracking(Recipe recipe) {
        currentlyMaking = true;
        trackedPrimaryId = recipe.primaryId;
        trackedSecondaryId = recipe.secondaryId;
        trackedProductId = recipe.productId;
        primaryCountAtStart = Rs2Inventory.itemQuantity(recipe.primaryId);
        secondaryCountAtStart = Rs2Inventory.itemQuantity(recipe.secondaryId);
        productCountAtStart = Rs2Inventory.itemQuantity(recipe.productId);
        processingStartTime = System.currentTimeMillis();
        lastProgressTime = processingStartTime;
        log.info("Started making {} (product={}, primary={}, secondary={})",
                recipe.name, productCountAtStart, primaryCountAtStart, secondaryCountAtStart);
    }

    private void resetBatchTracking() {
        currentlyMaking = false;
        trackedPrimaryId = -1;
        trackedSecondaryId = -1;
        trackedProductId = -1;
        primaryCountAtStart = 0;
        secondaryCountAtStart = 0;
        productCountAtStart = 0;
        processingStartTime = 0;
        lastProgressTime = 0;
    }

    /** Returns true while the current Make All batch is still producing output. */
    private boolean tracksProgress(Recipe recipe) {
        if (recipe.productId != trackedProductId ||
                recipe.primaryId != trackedPrimaryId ||
                recipe.secondaryId != trackedSecondaryId) {
            return false;
        }

        int productCount = Rs2Inventory.itemQuantity(recipe.productId);
        int primaryCount = Rs2Inventory.itemQuantity(recipe.primaryId);
        int secondaryCount = Rs2Inventory.itemQuantity(recipe.secondaryId);

        if (productCount > productCountAtStart ||
                primaryCount < primaryCountAtStart ||
                secondaryCount < secondaryCountAtStart) {
            lastProgressTime = System.currentTimeMillis();
        }

        // One of the consumable materials may still be present as a single leftover.
        boolean canMakeAnother = primaryCount >= recipe.primaryPerProduct && secondaryCount >= 1;
        return canMakeAnother && System.currentTimeMillis() - lastProgressTime < 4000;
    }

    private boolean handleMakeDialogue(Recipe recipe, int productCountBefore) {
        log.info("Handling make dialogue for product: {}", recipe.name);

        boolean dialogueAppeared = sleepUntil(() -> MakeDialogueHelper.isOpen()
                || findProductOption(recipe) != null, 3000);
        if (!dialogueAppeared) {
            MakeDialogueHelper.logState("No dialogue appeared for " + recipe.name);
            return false;
        }

        // A little reaction-time variation before clicking through the dialogue.
        Rs2Random.waitEx(140, 80);
        MakeDialogueHelper.logState("Make dialogue appeared for " + recipe.name);

        // Fletching uses widget group 270 (SKILLMULTI) rather than the old 219
        // dialogue. Headless arrows open a single-product "Create" interface
        // that must be confirmed with the create/quantity control.
        if (Rs2Widget.isProductionWidgetOpen()) {
            return handleProductionInterface(recipe, productCountBefore);
        }

        boolean productOffered = MakeDialogueHelper.hasProductOption(recipe.dialogueNames)
                || findProductOption(recipe) != null;
        if (productOffered) {
            if (!selectProductOption(recipe)) {
                return false;
            }
            return confirmQuantityDialogue(recipe, productCountBefore);
        }

        if (Rs2Dialogue.hasQuestion("How many") || MakeDialogueHelper.hasQuantityOption()) {
            // No product selection was needed, so a quantity confirmation is safe.
            return clickQuantityOption(recipe, productCountBefore);
        }

        if (!Rs2Dialogue.getDialogueOptions().isEmpty()) {
            log.info("Product {} was not offered - refusing generic make option", recipe.name);
        }
        MakeDialogueHelper.logState("No exact product or quantity option for " + recipe.name);
        return false;
    }

    /** Handles the widget group 270 production interface used by fletching. */
    private boolean handleProductionInterface(Recipe recipe, int productCountBefore) {
        MakeDialogueHelper.logState("Production interface open for " + recipe.name);
        Rs2Random.waitEx(120, 60);

        // Multi-product fletching (knife on logs) must select the exact
        // configured output. Never accept the highlighted/default product.
        if (MakeDialogueHelper.hasProductOption(recipe.dialogueNames)) {
            if (!MakeDialogueHelper.clickProduct(recipe.dialogueNames)) {
                return false;
            }
            log.info("Selected exact product {} in production interface", recipe.name);
            Rs2Random.waitEx(140, 70);
            if (!Rs2Widget.isProductionWidgetOpen()) {
                return true;
            }
            return clickQuantityOption(recipe, productCountBefore);
        }

        if (needsProductSelection(recipe)) {
            MakeDialogueHelper.logState("Exact product " + recipe.name
                    + " not offered - refusing fallback");
            return false;
        }

        // Headless arrows, finished arrows, darts and bolts are single-product
        // recipes. They expose no product option, so confirm the default Create.
        log.info("No product selection offered for {} - confirming default Create", recipe.name);
        return clickQuantityOption(recipe, productCountBefore);
    }

    /** True when knife-on-log fletching can produce several different items. */
    private boolean needsProductSelection(Recipe recipe) {
        return recipe.secondaryIsTool && isKnife(recipe.secondaryId);
    }

    private boolean selectProductOption(Recipe recipe) {
        if (MakeDialogueHelper.hasProductOption(recipe.dialogueNames)) {
            return MakeDialogueHelper.clickProduct(recipe.dialogueNames);
        }

        String productOption = findProductOption(recipe);
        if (productOption == null) {
            return false;
        }
        boolean clicked;
        if (Rs2Dialogue.hasCombinationDialogue()) {
            clicked = Rs2Dialogue.clickCombinationOption(productOption);
        } else {
            clicked = Rs2Dialogue.clickOption(productOption);
        }
        if (!clicked) {
            log.info("Failed to select product {} from dialogue", productOption);
        }
        return clicked;
    }

    /** Returns the first wording of this product that the open dialogue actually offers. */
    private String findProductOption(Recipe recipe) {
        for (String candidate : recipe.dialogueNames) {
            if (Rs2Dialogue.hasCombinationDialogue() &&
                    Rs2Dialogue.getCombinationOption(candidate) != null) {
                return candidate;
            }
            if (Rs2Dialogue.hasDialogueOption(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean confirmQuantityDialogue(Recipe recipe, int productCountBefore) {
        boolean quantityVisible = sleepUntil(() -> Rs2Dialogue.hasQuestion("How many")
                || MakeDialogueHelper.hasQuantityOption(), 1200);
        if (!quantityVisible) {
            Rs2Random.waitEx(140, 70);
            if (!Rs2Widget.isProductionWidgetOpen() && !Rs2Dialogue.hasCombinationDialogue()) {
                log.info("Product selection closed the interface for {}; crafting started", recipe.name);
                return true;
            }
            MakeDialogueHelper.logState("No quantity confirmation appeared for " + recipe.name);
            return false;
        }
        Rs2Random.waitEx(140, 80);
        return clickQuantityOption(recipe, productCountBefore);
    }

    private boolean clickQuantityOption(Recipe recipe, int productCountBefore) {
        return MakeDialogueHelper.clickQuantityOrSpace(
                recipe.name, recipe.productId, productCountBefore);
    }
}
