package net.runelite.client.plugins.microbot.autobankstander.skills.magic.lunars;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.autobankstander.processors.BankStandingProcessor;
import net.runelite.client.plugins.microbot.autobankstander.processors.MagicItemProcessor.Input;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.StaffUtils;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Bank-standing processor for Lunar spellbook production spells.
 *
 * <p>Most Lunar spells are cast once and then consume every matching item in
 * the inventory (or a fixed group of items per cast). Runes and coins are
 * budgeted per cast rather than per click, and every withdrawal is verified
 * before the spell is cast.</p>
 */
@Slf4j
public class LunarsProcessor implements BankStandingProcessor {

    private final LunarSpell spell;
    private final LunarSpell.Variant variant;

    public LunarsProcessor(LunarSpell spell, String selection) {
        this.spell = spell;
        this.variant = spell == null ? null : spell.getVariant(selection);
    }

    @Override
    public boolean validate() {
        if (spell == null || variant == null) {
            log.info("No Lunar spell or recipe selected");
            return false;
        }
        int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        if (level < spell.getLevel()) {
            log.info("Insufficient magic level for {}: need {}, have {}",
                    spell.getDisplayName(), spell.getLevel(), level);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        for (int i = 0; i < variant.getInputIds().length; i++) {
            requirements.add(getItemName(variant.getInputIds()[i]) + " x"
                    + variant.getInputQuantities()[i]);
        }
        int[] runeIds = spell.getRuneIds();
        int[] runeQuantities = spell.getRuneQuantities();
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        for (int i = 0; i < runeIds.length; i++) {
            if (!provided.contains(runeIds[i])) {
                requirements.add(getRuneName(runeIds[i]) + " x" + runeQuantities[i]);
            }
        }
        if (variant.getCoinsPerSet() > 0) {
            requirements.add("Coins x" + variant.getCoinsPerSet());
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        if (!hasInputsForOneBatch()) {
            return false;
        }
        if (!hasRunesForCasts(1)) {
            return false;
        }
        int coinCost = variant.getCoinsPerSet() * variant.getSetsPerCast();
        return coinCost == 0 || Rs2Inventory.itemQuantity(net.runelite.api.gameval.ItemID.COINS) >= coinCost;
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

        Input[] inputs = inputs();
        int maxSets = Integer.MAX_VALUE;
        for (Input input : inputs) {
            maxSets = Math.min(maxSets, input.bankCount() / input.getQuantity());
        }
        if (variant.getCoinsPerSet() > 0) {
            int bankCoins = Rs2Bank.count(net.runelite.api.gameval.ItemID.COINS);
            maxSets = Math.min(maxSets, bankCoins / variant.getCoinsPerSet());
        }

        // Cap the batch by rune supply; one cast handles setsPerCast input sets.
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = spell.getRuneIds();
        int[] runeQuantities = spell.getRuneQuantities();
        int maxCasts = Integer.MAX_VALUE;
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            maxCasts = Math.min(maxCasts, Rs2Bank.count(runeIds[i]) / Math.max(1, runeQuantities[i]));
        }
        if (maxCasts != Integer.MAX_VALUE) {
            maxSets = Math.min(maxSets, maxCasts * variant.getSetsPerCast());
        }
        if (maxSets == Integer.MAX_VALUE || maxSets < variant.getSetsPerCast()) {
            log.info("Bank does not have enough supplies for one {} batch", variant.getName());
            return false;
        }

        // Cap by free inventory slots, accounting for stackable rune/coin stacks.
        int reservedSlots = 0;
        for (int i = 0; i < runeIds.length; i++) {
            if (!provided.contains(runeIds[i])) {
                reservedSlots++;
            }
        }
        if (variant.getCoinsPerSet() > 0) {
            reservedSlots++;
        }
        int slotsPerSet = 0;
        for (Input input : inputs) {
            if (input.isStackable()) {
                reservedSlots++;
            } else {
                slotsPerSet += input.getQuantity();
            }
        }
        int availableSlots = Math.max(0, Rs2Inventory.emptySlotCount() - reservedSlots);
        if (slotsPerSet > 0) {
            maxSets = Math.min(maxSets, availableSlots / slotsPerSet);
        }
        if (maxSets < variant.getSetsPerCast()) {
            log.info("Not enough free inventory slots for one {} batch", variant.getName());
            return false;
        }

        // Keep batches a multiple of setsPerCast so rune counts stay exact.
        maxSets = (maxSets / variant.getSetsPerCast()) * variant.getSetsPerCast();
        int casts = maxSets / variant.getSetsPerCast();

        if (!withdrawRunesAndCoins(casts)) {
            return false;
        }
        Rs2Random.waitEx(120, 60);

        for (Input input : inputs) {
            int amount = maxSets * input.getQuantity();
            if (!input.withdraw(amount, input.isStackable())) {
                return false;
            }
            if (input.inventoryCount() < amount) {
                log.info("Withdrawal verification failed for {}", input.getLabel());
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean process() {
        if (!hasRequiredItems()) {
            return false;
        }
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchTo(InterfaceTab.MAGIC);
            Rs2Random.waitEx(120, 60);
            return sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 2000);
        }

        if (!Rs2Magic.cast(spell.getSpell())) {
            log.info("Failed to cast {}", spell.getDisplayName());
            return false;
        }

        if (spell == LunarSpell.PLANK_MAKE) {
            // One cast plus one click converts every log in the inventory; the
            // game consumes the runes and coins for each plank automatically.
            int logId = variant.getInputIds()[0];
            Rs2Random.waitEx(120, 60);
            // Match the established Lunar Plank Make flow: cast the spell, then
            // click the log with its default (Cast/Use) action.
            Rs2Inventory.interact(logId);
            sleepUntil(() -> Rs2Inventory.itemQuantity(logId) == 0, 20000);
        } else {
            Rs2Random.waitEx(130, 70);
            if (sleepUntil(() -> Rs2Player.isAnimating(1000), 2000)) {
                sleepUntil(() -> !Rs2Player.isAnimating(1000), 10000);
            }
        }
        return true;
    }

    @Override
    public boolean canContinueProcessing() {
        if (hasRequiredItems()) {
            return true;
        }
        return Rs2Bank.isNearBank(10) && hasBankSupplies();
    }

    @Override
    public String getStatusMessage() {
        return "Casting " + spell.getDisplayName().toLowerCase() + " (" + variant.getName() + ")...";
    }

    @Override
    public boolean isActivelyProcessing() {
        return Rs2Player.isAnimating(1000);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Input[] inputs() {
        int[] ids = variant.getInputIds();
        int[] quantities = variant.getInputQuantities();
        Input[] inputs = new Input[ids.length];
        for (int i = 0; i < ids.length; i++) {
            inputs[i] = Input.id(ids[i], quantities[i]);
        }
        return inputs;
    }

    private boolean hasInputsForOneBatch() {
        int[] ids = variant.getInputIds();
        int[] quantities = variant.getInputQuantities();
        for (int i = 0; i < ids.length; i++) {
            if (Rs2Inventory.itemQuantity(ids[i]) < quantities[i] * variant.getSetsPerCast()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRunesForCasts(int casts) {
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = spell.getRuneIds();
        int[] runeQuantities = spell.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int needed = Math.max(1, runeQuantities[i]) * casts;
            if (Rs2Inventory.itemQuantity(runeIds[i]) < needed) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBankSupplies() {
        for (Input input : inputs()) {
            if (input.bankCount() < input.getQuantity() * variant.getSetsPerCast()) {
                return false;
            }
        }
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = spell.getRuneIds();
        int[] runeQuantities = spell.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int needed = Math.max(1, runeQuantities[i]);
            if (Rs2Inventory.itemQuantity(runeIds[i]) + Rs2Bank.count(runeIds[i]) < needed) {
                return false;
            }
        }
        int coins = variant.getCoinsPerSet() * variant.getSetsPerCast();
        return coins == 0 || Rs2Inventory.itemQuantity(net.runelite.api.gameval.ItemID.COINS)
                + Rs2Bank.count(net.runelite.api.gameval.ItemID.COINS) >= coins;
    }

    private boolean withdrawRunesAndCoins(int casts) {
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = spell.getRuneIds();
        int[] runeQuantities = spell.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int needed = Math.max(1, runeQuantities[i]) * casts;
            int bankQty = Rs2Bank.count(runeIds[i]);
            if (bankQty < needed || !withdrawRune(runeIds[i], bankQty, needed)) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(runeIds[i]) < needed) {
                log.info("Withdrawal verification failed for {}", getRuneName(runeIds[i]));
                return false;
            }
        }

        int coinCost = variant.getCoinsPerSet() * variant.getSetsPerCast() * casts;
        if (coinCost > 0) {
            int bankCoins = Rs2Bank.count(net.runelite.api.gameval.ItemID.COINS);
            if (bankCoins < coinCost
                    || !withdrawRune(net.runelite.api.gameval.ItemID.COINS, bankCoins, coinCost)) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(net.runelite.api.gameval.ItemID.COINS) < coinCost) {
                log.info("Withdrawal verification failed for coins");
                return false;
            }
        }
        return true;
    }

    private boolean withdrawRune(int id, int bankQty, int needed) {
        boolean requested = bankQty > needed ? Rs2Bank.withdrawX(id, needed) : Rs2Bank.withdrawAll(id);
        if (!requested) {
            log.info("Bank rejected withdrawal of rune item {}", id);
            return false;
        }
        return sleepUntil(() -> Rs2Inventory.itemQuantity(id) >= needed, 3500);
    }

    private String getItemName(int id) {
        try {
            Rs2ItemModel model = Rs2ItemModel.createFromCache(id, 1, -1);
            if (model != null && model.getName() != null && !model.getName().isEmpty()) {
                return model.getName();
            }
        } catch (Exception e) {
            log.debug("Could not resolve item name for {}: {}", id, e.getMessage());
        }
        return "Item " + id;
    }

    private String getRuneName(int runeId) {
        switch (runeId) {
            case net.runelite.api.gameval.ItemID.AIRRUNE: return "Air rune";
            case net.runelite.api.gameval.ItemID.WATERRUNE: return "Water rune";
            case net.runelite.api.gameval.ItemID.EARTHRUNE: return "Earth rune";
            case net.runelite.api.gameval.ItemID.FIRERUNE: return "Fire rune";
            case net.runelite.api.gameval.ItemID.ASTRALRUNE: return "Astral rune";
            case net.runelite.api.gameval.ItemID.NATURERUNE: return "Nature rune";
            case net.runelite.api.gameval.ItemID.COSMICRUNE: return "Cosmic rune";
            default: return "Rune " + runeId;
        }
    }
}
