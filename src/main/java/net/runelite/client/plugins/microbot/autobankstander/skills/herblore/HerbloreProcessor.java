package net.runelite.client.plugins.microbot.autobankstander.skills.herblore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Herb;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.Mode;
import net.runelite.client.plugins.microbot.autobankstander.skills.herblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class HerbloreProcessor implements BankStandingProcessor {

    private static final long PROGRESS_GRACE_MS = 4000;
    private static final long STARTUP_GRACE_MS = 1500;

    private Mode mode;
    private CleanHerbMode cleanHerbMode;
    private UnfinishedPotionMode unfinishedPotionMode;
    private HerblorePotion finishedPotion;
    private boolean useAmuletOfChemistry;

    private final boolean turboModeEnabled;
    private final int turboHerbLimit;
    private final int sleepMin;
    private final int sleepMax;
    private final int sleepTarget;
    private boolean turboActive;
    private int turboHerbsCleanedCount;
    private final Random sleepRandom = new Random();
    private final Map<Integer, Boolean> stackableCache = new HashMap<>();

    private Herb currentHerb;
    private Herb currentHerbForUnfinished;
    private HerblorePotion currentPotion;

    // Make All batch state
    private boolean currentlyMaking;
    private int[] trackedMaterials;
    private int[] trackedRequirements;
    private int[] materialCountsAtStart;
    private Map<Integer, Integer> inventoryAtStart = new HashMap<>();
    private long processingStartTime;
    private long lastProgressTime;

    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode,
                             HerblorePotion finishedPotion, boolean useAmuletOfChemistry) {
        this(mode, cleanHerbMode, unfinishedPotionMode, finishedPotion, useAmuletOfChemistry,
                false, 0, 60, 300, 150);
    }

    public HerbloreProcessor(Mode mode, CleanHerbMode cleanHerbMode, UnfinishedPotionMode unfinishedPotionMode,
                             HerblorePotion finishedPotion, boolean useAmuletOfChemistry,
                             boolean turboMode, int turboHerbLimit,
                             int sleepMin, int sleepMax, int sleepTarget) {
        this.mode = mode;
        this.cleanHerbMode = cleanHerbMode;
        this.unfinishedPotionMode = unfinishedPotionMode;
        this.finishedPotion = finishedPotion;
        this.useAmuletOfChemistry = useAmuletOfChemistry;
        this.turboModeEnabled = turboMode;
        this.turboHerbLimit = turboHerbLimit;
        this.sleepMin = sleepMin;
        this.sleepMax = sleepMax;
        this.sleepTarget = sleepTarget;
        this.turboActive = turboMode;
    }

    @Override
    public boolean validate() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        log.info("Herblore level: {}, mode: {}", level, mode);

        if (mode == Mode.FINISHED_POTIONS && finishedPotion != null && level < finishedPotion.level) {
            log.info("Insufficient herblore level for {}: need {}, have {}",
                    finishedPotion, finishedPotion.level, level);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        switch (mode) {
            case CLEAN_HERBS:
                requirements.add("Grimy herbs");
                break;
            case UNFINISHED_POTIONS:
                requirements.add("Clean herbs");
                requirements.add("Vials of water");
                break;
            case FINISHED_POTIONS:
                if (finishedPotion != null) {
                    if (isSuperCombat(finishedPotion)) {
                        requirements.addAll(Arrays.asList("Torstol", "Super attack", "Super strength", "Super defence"));
                    } else {
                        requirements.add("Unfinished " + finishedPotion + " potion");
                        requirements.add("Secondary ingredient");
                    }
                }
                if (useAmuletOfChemistry) {
                    requirements.add("Amulet of chemistry");
                }
                break;
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        switch (mode) {
            case CLEAN_HERBS:
                return hasGrimyHerbInInventory();
            case UNFINISHED_POTIONS:
                return currentHerbForUnfinished != null &&
                        Rs2Inventory.itemQuantity(currentHerbForUnfinished.clean) >= 1 &&
                        Rs2Inventory.itemQuantity(ItemID.VIAL_WATER) >= 1;
            case FINISHED_POTIONS:
                return hasFinishedMaterials();
            default:
                return false;
        }
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

    @Override
    public boolean performBanking() {
        if (!Rs2Bank.isOpen()) {
            log.info("Bank not open");
            return false;
        }

        // Never withdraw noted herbs/potions - they cannot be used at the bank.
        if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
            log.info("Could not switch bank withdrawal mode to items");
            return false;
        }

        if (!Rs2Inventory.isEmpty() && !Rs2Bank.depositAll()) {
            log.info("Failed to deposit inventory");
            return false;
        }
        if (!sleepUntil(Rs2Inventory::isEmpty, 3000)) {
            log.info("Inventory did not empty after deposit");
            return false;
        }

        // A fresh inventory means any previous Make All batch is finished.
        resetBatchTracking();

        switch (mode) {
            case CLEAN_HERBS:
                return bankForCleanHerbs();
            case UNFINISHED_POTIONS:
                return bankForUnfinishedPotions();
            case FINISHED_POTIONS:
                return bankForFinishedPotions();
            default:
                return false;
        }
    }

    @Override
    public boolean process() {
        switch (mode) {
            case CLEAN_HERBS:
                return processCleanHerbs();
            case UNFINISHED_POTIONS:
                return processUnfinishedPotions();
            case FINISHED_POTIONS:
                return processFinishedPotions();
            default:
                return false;
        }
    }

    @Override
    public boolean canContinueProcessing() {
        if (isActivelyProcessing()) {
            return true;
        }
        switch (mode) {
            case CLEAN_HERBS:
                return hasGrimyHerbInInventory() || findHerb() != null;
            case UNFINISHED_POTIONS:
                return (currentHerbForUnfinished != null &&
                        Rs2Inventory.itemQuantity(currentHerbForUnfinished.clean) >= 1 &&
                        Rs2Inventory.itemQuantity(ItemID.VIAL_WATER) >= 1) ||
                        findHerbForUnfinished() != null;
            case FINISHED_POTIONS:
                return hasFinishedMaterials() || findPotion() != null;
            default:
                return false;
        }
    }

    @Override
    public String getStatusMessage() {
        if (currentlyMaking) {
            return "Making potions...";
        }
        switch (mode) {
            case CLEAN_HERBS:
                return "Cleaning herbs...";
            case UNFINISHED_POTIONS:
                return "Making unfinished potions...";
            case FINISHED_POTIONS:
                return "Making finished potions...";
            default:
                return "Processing herblore...";
        }
    }

    // ---------------------------------------------------------------------
    // Banking
    // ---------------------------------------------------------------------

    private boolean bankForCleanHerbs() {
        currentHerb = findHerb();
        if (currentHerb == null) {
            log.info("No more herbs available");
            return false;
        }

        int bankQty = Rs2Bank.count(currentHerb.grimy);
        int amount = jitterBatch(Math.min(bankQty, Rs2Inventory.emptySlotCount()));
        if (amount < 1) {
            log.info("No free slots or no grimy {} available", currentHerb.name());
            return false;
        }

        log.info("Withdrawing {} grimy {} (free slots: {})", amount, currentHerb.name(), Rs2Inventory.emptySlotCount());
        return withdraw(currentHerb.grimy, amount, false);
    }

    private boolean bankForUnfinishedPotions() {
        currentHerbForUnfinished = findHerbForUnfinished();
        if (currentHerbForUnfinished == null) {
            log.info("No more herbs or vials available");
            return false;
        }

        int herbCount = Rs2Bank.count(currentHerbForUnfinished.clean);
        int vialCount = Rs2Bank.count(ItemID.VIAL_WATER);
        int amount = jitterBatch(Math.min(Math.min(herbCount, vialCount), Rs2Inventory.emptySlotCount() / 2));
        if (amount < 1) {
            log.info("Not enough clean herbs, vials, or free slots for a batch");
            return false;
        }

        log.info("Withdrawing {} clean herbs and {} vials of water", amount, amount);
        if (!withdraw(currentHerbForUnfinished.clean, amount, false)) {
            return false;
        }
        Rs2Random.waitEx(120, 60);
        return withdraw(ItemID.VIAL_WATER, amount, false);
    }

    private boolean bankForFinishedPotions() {
        if (useAmuletOfChemistry) {
            checkAndEquipAmulet();
        }

        currentPotion = findPotion();
        if (currentPotion == null) {
            log.info("No more ingredients for selected potion");
            return false;
        }

        if (isSuperCombat(currentPotion)) {
            return bankForSuperCombat();
        } else if (usesStackableSecondary(currentPotion)) {
            return bankForStackableSecondary();
        }
        return bankForRegularPotion();
    }

    private boolean bankForSuperCombat() {
        int[] ingredients = {
                ItemID.TORSTOL,
                ItemID._4DOSE2ATTACK,
                ItemID._4DOSE2STRENGTH,
                ItemID._4DOSE2DEFENSE
        };

        int lowest = Arrays.stream(ingredients).map(Rs2Bank::count).min().orElse(0);
        int amount = jitterBatch(Math.min(lowest, Rs2Inventory.emptySlotCount() / ingredients.length));
        if (amount < 1) {
            log.info("Not enough super combat ingredients or free slots for a batch");
            return false;
        }

        log.info("Withdrawing {} of each super combat ingredient", amount);
        for (int id : ingredients) {
            if (!withdraw(id, amount, false)) {
                return false;
            }
            Rs2Random.waitEx(120, 60);
        }
        return true;
    }

    private boolean bankForStackableSecondary() {
        int unfinishedId = currentPotion.unfinished;
        int secondaryId = currentPotion.secondary;
        int ratio = getStackableSecondaryRatio(currentPotion);

        int unfinishedCount = Rs2Bank.count(unfinishedId);
        int secondaryCount = Rs2Bank.count(secondaryId);
        // One slot is needed for the whole secondary stack.
        int amount = Math.min(unfinishedCount, Math.min(secondaryCount / ratio, Rs2Inventory.emptySlotCount() - 1));
        amount = jitterBatch(amount);
        if (amount < 1) {
            log.info("Not enough unfinished potions, secondary, or free slots for a batch");
            return false;
        }

        log.info("Withdrawing {} unfinished potions and all {} secondaries", amount, secondaryCount);
        if (!withdraw(unfinishedId, amount, false)) {
            return false;
        }
        Rs2Random.waitEx(120, 60);
        return withdraw(secondaryId, secondaryCount, true);
    }

    private boolean bankForRegularPotion() {
        int unfinishedId = currentPotion.unfinished;
        int secondaryId = currentPotion.secondary;
        boolean secondaryStackable = isStackable(secondaryId);

        int unfinishedCount = Rs2Bank.count(unfinishedId);
        int secondaryCount = Rs2Bank.count(secondaryId);
        int freeSlots = Rs2Inventory.emptySlotCount();
        int amount = secondaryStackable
                ? Math.min(unfinishedCount, Math.min(secondaryCount, freeSlots - 1))
                : Math.min(Math.min(unfinishedCount, secondaryCount), freeSlots / 2);
        amount = jitterBatch(amount);
        if (amount < 1) {
            log.info("Not enough potion ingredients or free slots for a batch");
            return false;
        }

        log.info("Withdrawing {} unfinished potions and {} secondaries", amount, amount);
        if (!withdraw(unfinishedId, amount, false)) {
            return false;
        }
        Rs2Random.waitEx(120, 60);
        return withdraw(secondaryId, secondaryStackable ? secondaryCount : amount, secondaryStackable);
    }

    /**
     * Stackables are taken with Withdraw-All so they keep using a single slot.
     * Non-stackables are budgeted against the real free-slot count and verified.
     */
    private boolean withdraw(int itemId, int amount, boolean stackable) {
        if (amount <= 0) {
            return false;
        }

        int before = Rs2Inventory.itemQuantity(itemId);
        int bankQty = Rs2Bank.count(itemId);
        int expectedGain = stackable ? bankQty : amount;
        if (expectedGain <= 0) {
            log.info("Bank has no item {}", itemId);
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
        return sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= before + expectedGain, 3500);
    }

    private int jitterBatch(int amount) {
        if (amount <= 1) {
            return amount;
        }
        return Math.max(1, amount - Rs2Random.between(0, 2));
    }

    // ---------------------------------------------------------------------
    // Processing
    // ---------------------------------------------------------------------

    private boolean processCleanHerbs() {
        if (turboActive && turboHerbLimit > 0 && turboHerbsCleanedCount >= turboHerbLimit) {
            log.info("Turbo auto-disabled after {} herbs (limit {})", turboHerbsCleanedCount, turboHerbLimit);
            turboActive = false;
        }

        if (!hasGrimyHerbInInventory()) {
            log.info("No grimy herbs in inventory - returning to banking");
            return true;
        }

        if (turboActive) {
            cleanHerbsTurbo();
        } else {
            cleanHerbsNormal();
        }
        return true;
    }

    private void cleanHerbsNormal() {
        Rs2Inventory.cleanHerbs(InteractOrder.ZIGZAG);
        sleepUntil(() -> !hasGrimyHerbInInventory(), 5000);
        sleep(gaussianSleep());
    }

    private void cleanHerbsTurbo() {
        List<Rs2ItemModel> grimy = Rs2Inventory.items()
                .filter(item -> item.getName() != null && item.getName().toLowerCase().contains("grimy"))
                .collect(Collectors.toList());
        if (grimy.isEmpty()) {
            return;
        }

        List<Rs2ItemModel> ordered = Rs2Inventory.calculateInteractOrder(grimy, InteractOrder.ZIGZAG);
        for (Rs2ItemModel herb : ordered) {
            if (herb == null || herb.getName() == null || !herb.getName().toLowerCase().contains("grimy")) {
                continue;
            }
            if (Rs2Inventory.interact(herb, "Clean")) {
                turboHerbsCleanedCount++;
            }
            sleep(Rs2Random.between(5, 15));
        }
        Rs2Inventory.waitForInventoryChanges(3000);
        Rs2Random.waitEx(75, 25);
    }

    private int gaussianSleep() {
        double mean = (sleepMin + sleepMax + sleepTarget) / 3.0;
        double std = Math.abs(sleepTarget - mean) / 3.0;
        if (std <= 0) {
            return sleepTarget;
        }
        int duration;
        do {
            duration = (int) Math.round(mean + sleepRandom.nextGaussian() * std);
        } while (duration < sleepMin || duration > sleepMax);
        return duration;
    }

    private boolean processUnfinishedPotions() {
        if (currentlyMaking) {
            if (tracksProgress(trackedMaterials)) {
                return true;
            }
            log.info("Finished making unfinished potions - preparing another batch");
            resetBatchTracking();
        }

        if (currentHerbForUnfinished == null) {
            currentHerbForUnfinished = findHerbForUnfinished();
        }
        if (currentHerbForUnfinished == null) {
            return false;
        }
        int cleanId = currentHerbForUnfinished.clean;
        if (Rs2Inventory.itemQuantity(cleanId) < 1 || Rs2Inventory.itemQuantity(ItemID.VIAL_WATER) < 1) {
            return false;
        }

        Map<Integer, Integer> before = inventorySnapshot();
        if (!Rs2Inventory.combine(cleanId, ItemID.VIAL_WATER)) {
            return false;
        }

        String productName = getUnfinishedPotionName(currentHerbForUnfinished);
        if (!handleMakeDialogue(productName)) {
            if (!inventoryChanged(before)) {
                return false;
            }
            log.info("Made {} without a quantity dialogue", productName);
            Rs2Random.waitEx(140, 70);
            return true;
        }

        startBatchTracking(new int[]{cleanId, ItemID.VIAL_WATER}, new int[]{1, 1});
        Rs2Random.waitEx(160, 80);
        return true;
    }

    private boolean processFinishedPotions() {
        if (useAmuletOfChemistry && !amuletEquipped()) {
            log.info("Amulet of chemistry is no longer equipped - returning to banking");
            return false;
        }

        if (currentlyMaking) {
            if (tracksProgress(trackedMaterials)) {
                return true;
            }
            log.info("Finished making {} - preparing another batch", currentPotion);
            resetBatchTracking();
        }

        if (currentPotion == null) {
            currentPotion = findPotion();
        }
        if (currentPotion == null || !hasFinishedMaterials()) {
            return false;
        }

        return isSuperCombat(currentPotion) ? processSuperCombat() : processRegularPotion();
    }

    private boolean processSuperCombat() {
        int[] materials = {
                ItemID.TORSTOL,
                ItemID._4DOSE2ATTACK,
                ItemID._4DOSE2STRENGTH,
                ItemID._4DOSE2DEFENSE
        };
        for (int id : materials) {
            if (Rs2Inventory.itemQuantity(id) < 1) {
                return false;
            }
        }

        Map<Integer, Integer> before = inventorySnapshot();
        if (!Rs2Inventory.combine(ItemID.TORSTOL, ItemID._4DOSE2ATTACK)) {
            return false;
        }
        if (!handleMakeDialogue("Super combat potion")) {
            if (!inventoryChanged(before)) {
                return false;
            }
            Rs2Random.waitEx(140, 70);
            return true;
        }

        startBatchTracking(materials, new int[]{1, 1, 1, 1});
        Rs2Random.waitEx(160, 80);
        return true;
    }

    private boolean processRegularPotion() {
        int unfinishedId = currentPotion.unfinished;
        int secondaryId = currentPotion.secondary;
        int secondaryNeeded = getStackableSecondaryRatio(currentPotion);
        if (Rs2Inventory.itemQuantity(unfinishedId) < 1 ||
                Rs2Inventory.itemQuantity(secondaryId) < secondaryNeeded) {
            return false;
        }

        Map<Integer, Integer> before = inventorySnapshot();
        if (!Rs2Inventory.combine(unfinishedId, secondaryId)) {
            return false;
        }
        if (!handleMakeDialogue(currentPotion.toString())) {
            if (!inventoryChanged(before)) {
                return false;
            }
            Rs2Random.waitEx(140, 70);
            return true;
        }

        startBatchTracking(new int[]{unfinishedId, secondaryId}, new int[]{1, secondaryNeeded});
        Rs2Random.waitEx(160, 80);
        return true;
    }

    // ---------------------------------------------------------------------
    // Make dialogue and batch tracking
    // ---------------------------------------------------------------------

    private boolean handleMakeDialogue(String productName) {
        boolean dialogueAppeared = sleepUntil(() -> Rs2Dialogue.hasCombinationDialogue() ||
                Rs2Dialogue.hasQuestion("How many") ||
                findProductOption(productName) != null, 3000);
        if (!dialogueAppeared) {
            return false;
        }

        Rs2Random.waitEx(140, 80);

        if (Rs2Dialogue.hasCombinationDialogue()) {
            String match = findProductOption(productName);
            if (match == null) {
                log.info("Product {} is not offered in the combination dialogue - refusing fallback", productName);
                return false;
            }
            if (!Rs2Dialogue.clickCombinationOption(match)) {
                log.info("Failed to select product {} from combination dialogue", productName);
                return false;
            }
            return confirmQuantityDialogue(productName);
        }

        String directOption = findProductOption(productName);
        if (directOption != null) {
            if (!Rs2Dialogue.clickOption(directOption)) {
                log.info("Failed to select product {} from dialogue", productName);
                return false;
            }
            return confirmQuantityDialogue(productName);
        }

        if (Rs2Dialogue.hasQuestion("How many")) {
            // No product selection was needed, so a quantity confirmation is safe.
            return clickQuantityOption();
        }

        if (!Rs2Dialogue.getDialogueOptions().isEmpty()) {
            log.info("Product {} was not offered - refusing generic make option", productName);
        }
        return false;
    }

    private String findProductOption(String productName) {
        String[] candidates = {
                productName,
                productName + " (4)",
                productName + " (3)",
                productName + " (2)",
                productName + " (1)"
        };
        for (String candidate : candidates) {
            if (Rs2Dialogue.hasCombinationDialogue() && Rs2Dialogue.getCombinationOption(candidate) != null) {
                return candidate;
            }
            if (Rs2Dialogue.hasDialogueOption(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean confirmQuantityDialogue(String productName) {
        if (!Rs2Dialogue.sleepUntilHasQuestion("How many")) {
            log.info("No quantity confirmation appeared for {}", productName);
            return false;
        }
        Rs2Random.waitEx(140, 70);
        return clickQuantityOption();
    }

    private boolean clickQuantityOption() {
        if (Rs2Dialogue.hasDialogueOption("Make All") && Rs2Dialogue.clickOption("Make All")) {
            return true;
        }
        if (Rs2Dialogue.hasDialogueOption("All") && Rs2Dialogue.clickOption("All")) {
            return true;
        }
        log.info("No quantity option found in make dialogue");
        return false;
    }

    private void startBatchTracking(int[] materials, int[] requirements) {
        currentlyMaking = true;
        trackedMaterials = materials.clone();
        trackedRequirements = requirements.clone();
        materialCountsAtStart = Arrays.stream(materials).mapToObj(Rs2Inventory::itemQuantity)
                .mapToInt(Integer::intValue).toArray();
        inventoryAtStart = inventorySnapshot();
        processingStartTime = System.currentTimeMillis();
        lastProgressTime = processingStartTime;
        log.info("Started potion batch with materials {}", Arrays.toString(materials));
    }

    private void resetBatchTracking() {
        currentlyMaking = false;
        trackedMaterials = null;
        trackedRequirements = null;
        materialCountsAtStart = null;
        inventoryAtStart = new HashMap<>();
        processingStartTime = 0;
        lastProgressTime = 0;
    }

    private boolean tracksProgress(int[] materials) {
        if (materials == null || !Arrays.equals(materials, trackedMaterials)) {
            return false;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < materials.length; i++) {
            if (Rs2Inventory.itemQuantity(materials[i]) < materialCountsAtStart[i]) {
                lastProgressTime = now;
                break;
            }
        }

        Map<Integer, Integer> current = inventorySnapshot();
        for (Map.Entry<Integer, Integer> entry : current.entrySet()) {
            Integer before = inventoryAtStart.get(entry.getKey());
            if (before == null || entry.getValue() > before) {
                lastProgressTime = now;
                break;
            }
        }

        for (int i = 0; i < materials.length; i++) {
            if (Rs2Inventory.itemQuantity(materials[i]) < trackedRequirements[i]) {
                return false;
            }
        }
        return now - lastProgressTime < PROGRESS_GRACE_MS;
    }

    private Map<Integer, Integer> inventorySnapshot() {
        Map<Integer, Integer> snapshot = new HashMap<>();
        Rs2Inventory.items().forEach(item -> {
            if (item.getId() > 0) {
                snapshot.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        });
        return snapshot;
    }

    private boolean inventoryChanged(Map<Integer, Integer> before) {
        Map<Integer, Integer> current = inventorySnapshot();
        for (Map.Entry<Integer, Integer> entry : current.entrySet()) {
            Integer previous = before.get(entry.getKey());
            if (previous == null || !previous.equals(entry.getValue())) {
                return true;
            }
        }
        for (Integer id : before.keySet()) {
            if (!current.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    //Helpers
    // ---------------------------------------------------------------------

    private boolean hasGrimyHerbInInventory() {
        return Rs2Inventory.items().anyMatch(item ->
                item.getName() != null && item.getName().toLowerCase().contains("grimy"));
    }

    private boolean hasFinishedMaterials() {
        if (currentPotion == null) {
            return false;
        }
        if (isSuperCombat(currentPotion)) {
            return Rs2Inventory.itemQuantity(ItemID.TORSTOL) >= 1 &&
                    Rs2Inventory.itemQuantity(ItemID._4DOSE2ATTACK) >= 1 &&
                    Rs2Inventory.itemQuantity(ItemID._4DOSE2STRENGTH) >= 1 &&
                    Rs2Inventory.itemQuantity(ItemID._4DOSE2DEFENSE) >= 1;
        }
        return Rs2Inventory.itemQuantity(currentPotion.unfinished) >= 1 &&
                Rs2Inventory.itemQuantity(currentPotion.secondary) >= getStackableSecondaryRatio(currentPotion);
    }

    private boolean amuletEquipped() {
        return Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) ||
                Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED);
    }

    private void checkAndEquipAmulet() {
        if (!useAmuletOfChemistry || amuletEquipped()) {
            return;
        }

        int[] amulets = {ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED, ItemID.AMULET_OF_CHEMISTRY};
        for (int amuletId : amulets) {
            if (!Rs2Bank.hasItem(amuletId)) {
                continue;
            }
            log.info("Withdrawing and equipping amulet of chemistry ({})", amuletId);
            Rs2Random.waitEx(140, 70);
            if (Rs2Bank.withdrawAndEquip(amuletId) &&
                    sleepUntil(() -> amuletEquipped(), 3000)) {
                log.info("Amulet of chemistry equipped");
                return;
            }
        }
        log.info("No amulet of chemistry could be equipped");
    }

    private boolean usesStackableSecondary(HerblorePotion potion) {
        return getStackableSecondaryRatio(potion) > 1;
    }

    private int getStackableSecondaryRatio(HerblorePotion potion) {
        if (potion.secondary == ItemID.PRIF_CRYSTAL_SHARD_CRUSHED) return 4;
        if (potion.secondary == ItemID.SNAKEBOSS_SCALE) return 20;
        if (potion.secondary == ItemID.LAVA_SHARD) return 4;
        if (potion.secondary == ItemID.AMYLASE) return 4;
        return 1;
    }

    private boolean isSuperCombat(HerblorePotion potion) {
        return potion == HerblorePotion.SUPER_COMBAT;
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

    private Herb findHerb() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        if (cleanHerbMode == CleanHerbMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                if (level >= herbs[i].level && Rs2Bank.count(herbs[i].grimy) > 0) {
                    return herbs[i];
                }
            }
        } else {
            Herb herb = getHerbFromMode(cleanHerbMode);
            if (herb != null && level >= herb.level && Rs2Bank.count(herb.grimy) > 0) {
                return herb;
            }
        }
        return null;
    }

    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        if (unfinishedPotionMode == UnfinishedPotionMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                if (level >= herbs[i].level &&
                        Rs2Bank.count(herbs[i].clean) > 0 &&
                        Rs2Bank.count(ItemID.VIAL_WATER) > 0) {
                    return herbs[i];
                }
            }
        } else {
            Herb herb = getHerbFromUnfinishedMode(unfinishedPotionMode);
            if (herb != null && level >= herb.level &&
                    Rs2Bank.count(herb.clean) > 0 &&
                    Rs2Bank.count(ItemID.VIAL_WATER) > 0) {
                return herb;
            }
        }
        return null;
    }

    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        if (finishedPotion == null || level < finishedPotion.level) {
            return null;
        }

        if (isSuperCombat(finishedPotion)) {
            int[] ingredients = {
                    ItemID.TORSTOL,
                    ItemID._4DOSE2ATTACK,
                    ItemID._4DOSE2STRENGTH,
                    ItemID._4DOSE2DEFENSE
            };
            if (Arrays.stream(ingredients).allMatch(id -> Rs2Bank.count(id) > 0)) {
                return finishedPotion;
            }
        } else if (Rs2Bank.count(finishedPotion.unfinished) > 0 &&
                Rs2Bank.count(finishedPotion.secondary) >= getStackableSecondaryRatio(finishedPotion)) {
            return finishedPotion;
        }
        return null;
    }

    private String getUnfinishedPotionName(Herb herb) {
        for (UnfinishedPotionMode mode : UnfinishedPotionMode.values()) {
            if (mode != UnfinishedPotionMode.ANY_AND_ALL && getHerbFromUnfinishedMode(mode) == herb) {
                return mode.getUnfinishedPotionName();
            }
        }
        return herb.name().charAt(0) + herb.name().substring(1).toLowerCase() + " potion (unf)";
    }

    private Herb getHerbFromMode(CleanHerbMode mode) {
        switch (mode) {
            case GUAM: return Herb.GUAM;
            case MARRENTILL: return Herb.MARRENTILL;
            case TARROMIN: return Herb.TARROMIN;
            case HARRALANDER: return Herb.HARRALANDER;
            case RANARR: return Herb.RANARR;
            case TOADFLAX: return Herb.TOADFLAX;
            case IRIT: return Herb.IRIT;
            case AVANTOE: return Herb.AVANTOE;
            case KWUARM: return Herb.KWUARM;
            case SNAPDRAGON: return Herb.SNAPDRAGON;
            case CADANTINE: return Herb.CADANTINE;
            case LANTADYME: return Herb.LANTADYME;
            case DWARF: return Herb.DWARF;
            case TORSTOL: return Herb.TORSTOL;
            default: return null;
        }
    }

    private Herb getHerbFromUnfinishedMode(UnfinishedPotionMode mode) {
        switch (mode) {
            case GUAM_POTION_UNF: return Herb.GUAM;
            case MARRENTILL_POTION_UNF: return Herb.MARRENTILL;
            case TARROMIN_POTION_UNF: return Herb.TARROMIN;
            case HARRALANDER_POTION_UNF: return Herb.HARRALANDER;
            case RANARR_POTION_UNF: return Herb.RANARR;
            case TOADFLAX_POTION_UNF: return Herb.TOADFLAX;
            case IRIT_POTION_UNF: return Herb.IRIT;
            case AVANTOE_POTION_UNF: return Herb.AVANTOE;
            case KWUARM_POTION_UNF: return Herb.KWUARM;
            case SNAPDRAGON_POTION_UNF: return Herb.SNAPDRAGON;
            case CADANTINE_POTION_UNF: return Herb.CADANTINE;
            case LANTADYME_POTION_UNF: return Herb.LANTADYME;
            case DWARF_WEED_POTION_UNF: return Herb.DWARF;
            case TORSTOL_POTION_UNF: return Herb.TORSTOL;
            default: return null;
        }
    }
}
