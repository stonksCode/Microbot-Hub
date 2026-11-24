package net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class EnchantingProcessor implements BankStandingProcessor {

    private static final long PROGRESS_GRACE_MS = 4000;
    private static final long STARTUP_GRACE_MS = 1500;

    private final BoltType selectedBoltType;

    // Make All batch state
    private boolean currentlyMaking;
    private int[] trackedMaterials;
    private int[] trackedRequirements;
    private int[] materialCountsAtStart;
    private Map<Integer, Integer> inventoryAtStart = new HashMap<>();
    private long processingStartTime;
    private long lastProgressTime;

    public EnchantingProcessor(BoltType boltType) {
        this.selectedBoltType = boltType;
    }

    @Override
    public boolean validate() {
        if (selectedBoltType == null) {
            log.info("No bolt type selected");
            return false;
        }

        int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        if (level < selectedBoltType.getLevelRequired()) {
            log.info("Insufficient magic level for {}: need {}, have {}",
                    selectedBoltType.getName(), selectedBoltType.getLevelRequired(), level);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        requirements.add(selectedBoltType.getName());

        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        for (int runeId : selectedBoltType.getRuneIds()) {
            if (!providedRunes.contains(runeId)) {
                requirements.add(getRuneName(runeId));
            }
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        return Rs2Inventory.itemQuantity(selectedBoltType.getUnenchantedId()) >= 1 && hasRequiredRunes(1);
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

        if (Rs2Bank.hasWithdrawAsNote() && !Rs2Bank.setWithdrawAsItem()) {
            log.info("Could not switch bank withdrawal mode to items");
            return false;
        }

        // Equip before the missing-items check so staff-provided runes count as available.
        if (StaffUtils.equipBestAvailableStaff(selectedBoltType.getRuneIds())) {
            Rs2Random.waitEx(140, 70);
            boolean equipped = sleepUntil(() -> StaffUtils.getEquippedStaff() != null, 3000);
            log.info("Staff equip confirmation: {}", equipped);
        }

        String missingItems = getMissingItemsList();
        if (!missingItems.isEmpty()) {
            log.info("Missing required items: {}", missingItems);
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

        // Runes are stacks, so size the bolt batch to what the runes can actually cast.
        int affordableBolts = maxBoltsAffordableFromBank();
        int bankBolts = Rs2Bank.count(selectedBoltType.getUnenchantedId());
        int amount = Math.min(bankBolts, affordableBolts);
        amount = Math.min(amount, Rs2Inventory.emptySlotCount());
        if (amount > 1) {
            amount = Math.max(1, amount - Rs2Random.between(0, 1));
        }
        if (amount < 1) {
            log.info("Not enough bolts/runes/free slots for an enchanting batch");
            return false;
        }

        if (!withdrawRequiredRunes(amount)) {
            return false;
        }
        Rs2Random.waitEx(140, 70);
        return withdrawBolts(amount);
    }

    @Override
    public boolean process() {
        if (currentlyMaking) {
            if (tracksProgress()) {
                return true;
            }
            log.info("Finished enchanting batch - preparing another one");
            resetBatchTracking();
        }

        if (!hasRequiredItems()) {
            return false;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            if (!sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 2000)) {
                return false;
            }
            Rs2Random.waitEx(120, 60);
            return true;
        }

        if (!castCrossbowBoltEnchantSpell()) {
            log.info("Failed to cast Enchant Crossbow Bolt");
            return false;
        }
        if (!sleepUntil(Rs2Widget::isProductionWidgetOpen, 3000)) {
            log.info("Production widget did not open");
            return false;
        }

        Rs2Random.waitEx(140, 70);
        if (!selectBoltType()) {
            log.info("Failed to select {} from the enchanting interface", selectedBoltType.getEnchantedName());
            return false;
        }

        sleepUntil(() -> Rs2Player.isAnimating(1000), 2000);
        sleepUntil(() -> !Rs2Player.isAnimating(1000), 10000);
        startBatchTracking();
        Rs2Random.waitEx(160, 80);
        return true;
    }

    @Override
    public boolean canContinueProcessing() {
        if (isActivelyProcessing()) {
            return true;
        }
        if (Rs2Inventory.itemQuantity(selectedBoltType.getUnenchantedId()) >= 1 && hasRequiredRunes(1)) {
            return true;
        }
        if (Rs2Bank.isNearBank(10) &&
                Rs2Bank.count(selectedBoltType.getUnenchantedId()) > 0 &&
                maxBoltsAffordableFromBank() > 0) {
            return true;
        }
        log.info("No more bolts or runes to enchant with");
        return false;
    }

    @Override
    public String getStatusMessage() {
        if (currentlyMaking) {
            return "Enchanting " + selectedBoltType.getName().toLowerCase() + "...";
        }
        return "Enchanting " + selectedBoltType.getName() + "...";
    }

    // ---------------------------------------------------------------------
    // Banking helpers
    // ---------------------------------------------------------------------

    /** Largest bolt batch the runes in the bank can fully enchant. */
    private int maxBoltsAffordableFromBank() {
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();

        int limit = Integer.MAX_VALUE;
        for (int i = 0; i < runeIds.length; i++) {
            if (providedRunes.contains(runeIds[i])) {
                continue;
            }
            int perCast = Math.max(1, runeQuantities[i]);
            limit = Math.min(limit, Rs2Bank.count(runeIds[i]) / perCast);
        }
        return limit == Integer.MAX_VALUE ? Rs2Bank.count(selectedBoltType.getUnenchantedId()) : limit;
    }

    private boolean withdrawRequiredRunes(int boltCount) {
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();

        for (int i = 0; i < runeIds.length; i++) {
            int runeId = runeIds[i];
            if (providedRunes.contains(runeId)) {
                log.info("Skipping {} withdrawal - provided by staff", getRuneName(runeId));
                continue;
            }

            int needed = runeQuantities[i] * boltCount;
            int bankQty = Rs2Bank.count(runeId);
            if (bankQty < needed) {
                log.info("Not enough {} for a batch of {} bolts (have {}, need {})",
                        getRuneName(runeId), boltCount, bankQty, needed);
                return false;
            }

            if (!withdraw(runeId, bankQty, true)) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(runeId) < needed) {
                log.info("Withdrawal verification failed for {}", getRuneName(runeId));
                return false;
            }
        }
        return true;
    }

    private boolean withdrawBolts(int amount) {
        if (!withdraw(selectedBoltType.getUnenchantedId(), amount, false)) {
            return false;
        }
        log.info("Withdrew {} bolts for enchanting", amount);
        return true;
    }

    private boolean withdraw(int itemId, int amount, boolean stackable) {
        if (amount <= 0) {
            return false;
        }

        int before = Rs2Inventory.itemQuantity(itemId);
        int bankQty = Rs2Bank.count(itemId);
        int expectedGain = stackable ? bankQty : amount;
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
        return sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= before + expectedGain, 3500);
    }

    // ---------------------------------------------------------------------
    // Casting and batch tracking
    // ---------------------------------------------------------------------

    private boolean castCrossbowBoltEnchantSpell() {
        try {
            if (Rs2Magic.cast(BoltType.getCrossbowBoltSpell())) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Rs2Magic cast failed: {}", e.getMessage());
        }

        // Legacy widget fallback for clients where the spell lookup is not available.
        Rs2Random.waitEx(120, 60);
        return Rs2Widget.clickWidget(218, 10);
    }

    private boolean selectBoltType() {
        String name = selectedBoltType.getEnchantedName();

        if (Rs2Dialogue.hasCombinationDialogue() && Rs2Dialogue.getCombinationOption(name) != null) {
            return Rs2Dialogue.clickCombinationOption(name);
        }
        if (Rs2Widget.clickWidget(name)) {
            return true;
        }
        // Last-resort fallback to the old production widget child.
        return Rs2Widget.clickWidget(name, Optional.of(270), 13, false);
    }

    private void startBatchTracking() {
        List<Integer> materials = new ArrayList<>();
        List<Integer> requirements = new ArrayList<>();
        materials.add(selectedBoltType.getUnenchantedId());
        requirements.add(1);

        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (!providedRunes.contains(runeIds[i])) {
                materials.add(runeIds[i]);
                requirements.add(Math.max(1, runeQuantities[i]));
            }
        }

        currentlyMaking = true;
        trackedMaterials = materials.stream().mapToInt(Integer::intValue).toArray();
        trackedRequirements = requirements.stream().mapToInt(Integer::intValue).toArray();
        materialCountsAtStart = Arrays.stream(trackedMaterials).mapToObj(Rs2Inventory::itemQuantity)
                .mapToInt(Integer::intValue).toArray();
        inventoryAtStart = inventorySnapshot();
        processingStartTime = System.currentTimeMillis();
        lastProgressTime = processingStartTime;
        log.info("Started enchanting batch tracking for {}", selectedBoltType.getName());
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

    private boolean tracksProgress() {
        if (trackedMaterials == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < trackedMaterials.length; i++) {
            if (Rs2Inventory.itemQuantity(trackedMaterials[i]) < materialCountsAtStart[i]) {
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

        for (int i = 0; i < trackedMaterials.length; i++) {
            if (Rs2Inventory.itemQuantity(trackedMaterials[i]) < trackedRequirements[i]) {
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

    // ---------------------------------------------------------------------
    // Runes
    // ---------------------------------------------------------------------

    private boolean hasRequiredRunes(int casts) {
        if (selectedBoltType == null || casts < 1) {
            return false;
        }
        if (Rs2Inventory.itemQuantity(selectedBoltType.getUnenchantedId()) < 1) {
            return false;
        }

        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (providedRunes.contains(runeIds[i])) {
                continue;
            }
            int needed = runeQuantities[i] * casts;
            int available = Rs2Inventory.itemQuantity(runeIds[i]);
            if (available < needed) {
                log.info("Missing rune: {} (have {}, need {} for {} casts)",
                        getRuneName(runeIds[i]), available, needed, casts);
                return false;
            }
        }
        return true;
    }

    private String getMissingItemsList() {
        StringBuilder missing = new StringBuilder();

        int bolts = Rs2Inventory.itemQuantity(selectedBoltType.getUnenchantedId()) +
                Rs2Bank.count(selectedBoltType.getUnenchantedId());
        if (bolts < 1) {
            missing.append(selectedBoltType.getName());
        }

        Set<Integer> providedRunes = StaffUtils.getProvidedRunes();
        int[] runeIds = selectedBoltType.getRuneIds();
        int[] runeQuantities = selectedBoltType.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (providedRunes.contains(runeIds[i])) {
                continue;
            }
            int total = Rs2Inventory.itemQuantity(runeIds[i]) + Rs2Bank.count(runeIds[i]);
            if (total < runeQuantities[i]) {
                if (missing.length() > 0) {
                    missing.append(" - ");
                }
                missing.append(getRuneName(runeIds[i]));
            }
        }
        return missing.toString();
    }

    private String getRuneName(int runeId) {
        switch (runeId) {
            case ItemID.AIRRUNE: return "Air rune";
            case ItemID.WATERRUNE: return "Water rune";
            case ItemID.EARTHRUNE: return "Earth rune";
            case ItemID.FIRERUNE: return "Fire rune";
            case ItemID.MINDRUNE: return "Mind rune";
            case ItemID.COSMICRUNE: return "Cosmic rune";
            case ItemID.NATURERUNE: return "Nature rune";
            case ItemID.BLOODRUNE: return "Blood rune";
            case ItemID.LAWRUNE: return "Law rune";
            case ItemID.SOULRUNE: return "Soul rune";
            case ItemID.DEATHRUNE: return "Death rune";
            default: return "Unknown rune (" + runeId + ")";
        }
    }
}
