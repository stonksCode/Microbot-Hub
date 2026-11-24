package net.runelite.client.plugins.microbot.autobankstander.processors;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Shared bank-standing implementation for recipes that are made by using one
 * inventory item on another (combining), with an optional tool in the second
 * slot. Handles capacity-aware withdrawals, exact product dialogue selection,
 * and Make All batch tracking.
 */
@Slf4j
public class GenericCombineProcessor implements BankStandingProcessor {

    /** Immutable description of one combine recipe. */
    public static final class Recipe {
        private final String name;
        private final Skill skill;
        private final int level;
        private final int primaryId;
        private final int secondaryId;
        private final int productId;
        private final boolean secondaryIsTool;
        private final int primaryPerProduct;
        private final boolean pressSpaceAfterCombine;
        private final boolean productionWidget;
        private final String[] productNames;

        public Recipe(String name, Skill skill, int level, int primaryId, int secondaryId,
                      int productId, boolean secondaryIsTool, int primaryPerProduct,
                      String... productNames) {
            this(name, skill, level, primaryId, secondaryId, productId, secondaryIsTool,
                    primaryPerProduct, false, false, productNames);
        }

        public Recipe(String name, Skill skill, int level, int primaryId, int secondaryId,
                      int productId, boolean secondaryIsTool, int primaryPerProduct,
                      boolean pressSpaceAfterCombine, boolean productionWidget,
                      String... productNames) {
            this.name = name;
            this.skill = skill;
            this.level = level;
            this.primaryId = primaryId;
            this.secondaryId = secondaryId;
            this.productId = productId;
            this.secondaryIsTool = secondaryIsTool;
            this.primaryPerProduct = Math.max(1, primaryPerProduct);
            this.pressSpaceAfterCombine = pressSpaceAfterCombine;
            this.productionWidget = productionWidget;
            this.productNames = productNames != null && productNames.length > 0
                    ? productNames
                    : new String[]{name};
        }

        public String getName() {
            return name;
        }

        public Skill getSkill() {
            return skill;
        }

        public int getLevel() {
            return level;
        }

        public int getPrimaryId() {
            return primaryId;
        }

        public int getSecondaryId() {
            return secondaryId;
        }

        public int getProductId() {
            return productId;
        }

        public boolean isSecondaryIsTool() {
            return secondaryIsTool;
        }

        public int getPrimaryPerProduct() {
            return primaryPerProduct;
        }

        public String[] getProductNames() {
            return productNames.clone();
        }

        public boolean isPressSpaceAfterCombine() {
            return pressSpaceAfterCombine;
        }

        public boolean isProductionWidget() {
            return productionWidget;
        }
    }

    private static final long STARTUP_GRACE_MS = 1500;
    private static final long PROGRESS_GRACE_MS = 4000;

    private final Recipe recipe;
    private final String activityLabel;
    private final Map<Integer, Boolean> stackableCache = new HashMap<>();

    private boolean currentlyMaking;
    private int materialCountAtStart;
    private int productCountAtStart;
    private long processingStartTime;
    private long lastProgressTime;

    public GenericCombineProcessor(Recipe recipe, String activityLabel) {
        this.recipe = recipe;
        this.activityLabel = activityLabel;
    }

    @Override
    public boolean validate() {
        if (recipe == null) {
            return false;
        }
        int level = Rs2Player.getRealSkillLevel(recipe.getSkill());
        if (level < recipe.getLevel()) {
            log.info("Insufficient {} level for {}: need {}, have {}",
                    recipe.getSkill().getName(), recipe.getName(), recipe.getLevel(), level);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        requirements.add("Primary material for " + recipe.getName());
        if (recipe.isSecondaryIsTool()) {
            requirements.add("Tool for " + recipe.getName());
        } else {
            requirements.add("Secondary material for " + recipe.getName());
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        return recipe != null &&
                Rs2Inventory.itemQuantity(recipe.getPrimaryId()) >= recipe.getPrimaryPerProduct() &&
                Rs2Inventory.itemQuantity(recipe.getSecondaryId()) >= 1;
    }

    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }
        if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
            log.info("Could not switch bank withdrawal mode to items");
            return false;
        }
        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            if (!sleepUntil(Rs2Inventory::isEmpty, 3000)) {
                log.info("Failed to deposit inventory");
                return false;
            }
        }

        resetBatchTracking();
        return withdrawMaterials();
    }

    @Override
    public boolean process() {
        if (currentlyMaking) {
            if (tracksProgress()) {
                return true;
            }
            log.info("Finished making {} - preparing another batch", recipe.getName());
            resetBatchTracking();
        }
        if (!hasRequiredItems()) {
            return false;
        }

        if (!Rs2Inventory.combine(recipe.getPrimaryId(), recipe.getSecondaryId())) {
            return false;
        }
        if (recipe.isPressSpaceAfterCombine()) {
            // Gem cutting and similar actions apply immediately when Space is
            // pressed; there is no make dialogue to select.
            Rs2Random.waitEx(150, 75);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        } else if (recipe.isProductionWidget()) {
            if (!handleProductionWidget()) {
                return false;
            }
        } else if (!handleMakeDialogue()) {
            return false;
        }

        startBatchTracking();
        Rs2Random.waitEx(160, 80);
        return true;
    }

    @Override
    public boolean canContinueProcessing() {
        if (recipe == null) {
            return false;
        }
        return hasRequiredItems() ||
                (Rs2Bank.count(recipe.getPrimaryId()) >= recipe.getPrimaryPerProduct() &&
                        Rs2Bank.count(recipe.getSecondaryId()) >= 1);
    }

    @Override
    public String getStatusMessage() {
        return currentlyMaking
                ? "Making " + recipe.getName().toLowerCase() + "..."
                : activityLabel;
    }

    @Override
    public boolean isActivelyProcessing() {
        if (!currentlyMaking) {
            return false;
        }
        if (Rs2Dialogue.isInDialogue() || Rs2Player.isAnimating(1000)) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - processingStartTime < STARTUP_GRACE_MS) {
            return true;
        }
        return now - lastProgressTime < PROGRESS_GRACE_MS;
    }

    private boolean withdrawMaterials() {
        int emptySlots = Rs2Inventory.emptySlotCount();
        if (emptySlots <= 0) {
            log.info("No free inventory slots available");
            return false;
        }

        int bankPrimary = Rs2Bank.count(recipe.getPrimaryId());
        int bankSecondary = Rs2Bank.count(recipe.getSecondaryId());
        if (bankPrimary < recipe.getPrimaryPerProduct() || bankSecondary < 1) {
            log.info("Bank does not have enough materials for {}: primary={}, secondary={}",
                    recipe.getName(), bankPrimary, bankSecondary);
            return false;
        }

        boolean primaryStackable = isStackable(recipe.getPrimaryId());
        boolean secondaryStackable = isStackable(recipe.getSecondaryId());
        int usableSlots = emptySlots - (recipe.isSecondaryIsTool() ? 1 : 0);
        if (usableSlots <= 0) {
            return false;
        }

        int primaryQty;
        int secondaryQty;
        if (recipe.isSecondaryIsTool()) {
            int products = usableSlots / recipe.getPrimaryPerProduct();
            primaryQty = Math.min(bankPrimary, products * recipe.getPrimaryPerProduct());
            secondaryQty = 1;
        } else if (primaryStackable && secondaryStackable) {
            primaryQty = bankPrimary;
            secondaryQty = bankSecondary;
        } else if (primaryStackable) {
            secondaryQty = Math.min(bankSecondary, usableSlots - 1);
            primaryQty = bankPrimary;
        } else if (secondaryStackable) {
            primaryQty = Math.min(bankPrimary, usableSlots - 1);
            secondaryQty = bankSecondary;
        } else {
            int target = Math.min(usableSlots / 2, Rs2Random.between(11, 14));
            primaryQty = Math.min(bankPrimary, target);
            secondaryQty = Math.min(bankSecondary, target);
        }

        if (primaryQty < recipe.getPrimaryPerProduct() || secondaryQty < 1) {
            log.info("Not enough usable slots for a full {} batch", recipe.getName());
            return false;
        }

        if (!withdraw(recipe.getPrimaryId(), primaryQty, primaryStackable)) {
            return false;
        }
        Rs2Random.waitEx(120, 60);
        return withdraw(recipe.getSecondaryId(), secondaryQty, secondaryStackable);
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

    private boolean handleProductionWidget() {
        boolean appeared = sleepUntil(() -> Rs2Widget.getWidget(270, 5) != null
                || Rs2Widget.getWidget(17694734) != null, 3000);
        if (!appeared) {
            log.info("No production widget appeared for {}", recipe.getName());
            return false;
        }
        Rs2Random.waitEx(130, 70);

        // Only accept the exact configured product; never fall back to the
        // highlighted default when that product is not offered.
        for (String product : recipe.getProductNames()) {
            if (Rs2Widget.clickWidget(product, true)) {
                return true;
            }
        }
        log.info("No configured product option offered in production widget for {}",
                recipe.getName());
        return false;
    }

    private boolean handleMakeDialogue() {
        boolean appeared = sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue() ||
                Rs2Dialogue.hasQuestion("How many") ||
                hasProductOption(), 3000);
        if (!appeared) {
            log.info("No make dialogue appeared for {}", recipe.getName());
            return false;
        }

        Rs2Random.waitEx(130, 70);

        if (Rs2Dialogue.hasCombinationDialogue()) {
            String match = findProductOption();
            if (match == null) {
                log.info("No configured product option offered for {} - refusing fallback", recipe.getName());
                return false;
            }
            if (!Rs2Dialogue.clickCombinationOption(match)) {
                return false;
            }
            return confirmQuantity();
        }

        String direct = findProductOption();
        if (direct != null) {
            if (!Rs2Dialogue.clickOption(direct)) {
                return false;
            }
            return confirmQuantity();
        }

        if (Rs2Dialogue.hasQuestion("How many")) {
            return clickQuantityOption();
        }
        return false;
    }

    private boolean hasProductOption() {
        return findProductOption() != null;
    }

    private String findProductOption() {
        for (String candidate : recipe.getProductNames()) {
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

    private boolean confirmQuantity() {
        if (!Rs2Dialogue.sleepUntilHasQuestion("How many")) {
            log.info("No quantity confirmation appeared for {}", recipe.getName());
            return false;
        }
        Rs2Random.waitEx(130, 70);
        return clickQuantityOption();
    }

    private boolean clickQuantityOption() {
        if (Rs2Dialogue.hasDialogueOption("Make All") && Rs2Dialogue.clickOption("Make All")) {
            return true;
        }
        if (Rs2Dialogue.hasDialogueOption("All") && Rs2Dialogue.clickOption("All")) {
            return true;
        }
        log.info("No quantity option found in make dialogue for {}", recipe.getName());
        return false;
    }

    private void startBatchTracking() {
        currentlyMaking = true;
        materialCountAtStart = Rs2Inventory.itemQuantity(recipe.getPrimaryId());
        productCountAtStart = Rs2Inventory.itemQuantity(recipe.getProductId());
        processingStartTime = System.currentTimeMillis();
        lastProgressTime = processingStartTime;
    }

    private void resetBatchTracking() {
        currentlyMaking = false;
        materialCountAtStart = 0;
        productCountAtStart = 0;
        processingStartTime = 0;
        lastProgressTime = 0;
    }

    private boolean tracksProgress() {
        int material = Rs2Inventory.itemQuantity(recipe.getPrimaryId());
        int product = Rs2Inventory.itemQuantity(recipe.getProductId());
        if (material < materialCountAtStart || product > productCountAtStart) {
            lastProgressTime = System.currentTimeMillis();
        }
        boolean canMakeAnother = material >= recipe.getPrimaryPerProduct() &&
                Rs2Inventory.itemQuantity(recipe.getSecondaryId()) >= 1;
        return canMakeAnother && System.currentTimeMillis() - lastProgressTime < PROGRESS_GRACE_MS;
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
}
