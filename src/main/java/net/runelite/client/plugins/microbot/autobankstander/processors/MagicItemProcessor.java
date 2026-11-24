package net.runelite.client.plugins.microbot.autobankstander.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting.StaffUtils;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import lombok.extern.slf4j.Slf4j;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Bank-standing processor for spells that are cast on an inventory item
 * (jewellery enchanting, alchemy, superheating and lunar item spells).
 *
 * <p>Runes may be supplied by an equipped staff; in that case the corresponding
 * rune stacks are not withdrawn. Batches are sized from real bank counts and
 * free inventory slots, and every withdrawal is verified before use.</p>
 */
@Slf4j
public class MagicItemProcessor implements BankStandingProcessor {

    public enum CastMode {
        /** Cast the spell, then use it on the first input item. */
        CAST_USE,
        /** Cast the spell, then click the first input item with the Cast action. */
        CAST_CAST,
        /** Cast the spell only; it consumes every matching inventory item itself. */
        CAST_ONLY,
        /** Use the client's high alchemy helper (handles its own widget clicks). */
        ALCH_HIGH,
        /** Cast low alchemy, then click the first input item with the Cast action. */
        ALCH_LOW,
        /** Use the client's superheat helper (handles its own widget clicks). */
        SUPERHEAT
    }

    /** One inventory input, addressed either by item id or by display name. */
    public static final class Input {
        private final int id;
        private final String name;
        private final int quantity;

        private Input(int id, String name, int quantity) {
            this.id = id;
            this.name = name;
            this.quantity = Math.max(1, quantity);
        }

        public static Input id(int id) {
            return new Input(id, null, 1);
        }

        public static Input id(int id, int quantity) {
            return new Input(id, null, quantity);
        }

        public static Input name(String name) {
            return new Input(-1, name, 1);
        }

        public String getLabel() {
            return name != null ? name : "Item " + id;
        }

        public int getId() {
            return id;
        }

        public boolean isNamed() {
            return name != null;
        }

        public int getQuantity() {
            return quantity;
        }

        public int inventoryCount() {
            return name != null ? Rs2Inventory.itemQuantity(name) : Rs2Inventory.itemQuantity(id);
        }

        public int bankCount() {
            return name != null ? Rs2Bank.count(name) : Rs2Bank.count(id);
        }

        public boolean isStackable() {
            if (name != null) {
                // Name-based targets are intentionally treated as non-stackable:
                // withdrawX still works and the batch is capped by free slots.
                return false;
            }
            try {
                Rs2ItemModel model = Rs2ItemModel.createFromCache(id, 1, -1);
                return model != null && model.isStackable();
            } catch (Exception e) {
                log.debug("Could not determine stackability for item {}: {}", id, e.getMessage());
                return false;
            }
        }

        public boolean interact(String action) {
            return name != null
                    ? Rs2Inventory.interact(name, action)
                    : Rs2Inventory.interact(id, action);
        }

        public boolean withdraw(int amount, boolean stackable) {
            int bankQty = bankCount();
            boolean requested;
            if (stackable && amount > 1) {
                requested = name != null ? Rs2Bank.withdrawAll(name) : Rs2Bank.withdrawAll(id);
            } else if (amount == 1) {
                requested = name != null ? Rs2Bank.withdrawOne(name) : Rs2Bank.withdrawOne(id);
            } else {
                requested = name != null ? Rs2Bank.withdrawX(name, amount) : Rs2Bank.withdrawX(id, amount);
            }
            if (!requested) {
                log.info("Bank rejected withdrawal of {} (amount {})", getLabel(), amount);
                return false;
            }
            int expectedGain = stackable && amount > 1 ? bankQty : amount;
            int before = inventoryCount();
            return sleepUntil(() -> inventoryCount() >= before + expectedGain, 3500);
        }
    }

    /** Immutable description of one spell-on-item recipe. */
    public static final class Recipe {
        private final String name;
        private final int level;
        private final MagicAction spell;
        private final int[] runeIds;
        private final int[] runeQuantities;
        private final Input[] inputs;
        private final int coinsPerCast;
        private final CastMode castMode;

        public Recipe(String name, int level, MagicAction spell, CastMode castMode,
                      int[] runeIds, int[] runeQuantities, Input[] inputs, int coinsPerCast) {
            this.name = name;
            this.level = level;
            this.spell = spell;
            this.castMode = castMode;
            this.runeIds = runeIds == null ? new int[0] : runeIds.clone();
            this.runeQuantities = runeQuantities == null ? new int[0] : runeQuantities.clone();
            this.inputs = inputs == null ? new Input[0] : inputs.clone();
            this.coinsPerCast = Math.max(0, coinsPerCast);
            if (this.runeIds.length != this.runeQuantities.length) {
                throw new IllegalArgumentException("Rune id and quantity arrays must match");
            }
        }

        /** Convenience constructor for id-based inputs. */
        public Recipe(String name, int level, MagicAction spell, CastMode castMode,
                      int[] runeIds, int[] runeQuantities, int[] inputIds, int[] inputQuantities,
                      int coinsPerCast) {
            this(name, level, spell, castMode, runeIds, runeQuantities,
                    toInputs(inputIds, inputQuantities), coinsPerCast);
        }

        private static Input[] toInputs(int[] ids, int[] quantities) {
            if (ids == null) {
                return new Input[0];
            }
            Input[] inputs = new Input[ids.length];
            for (int i = 0; i < ids.length; i++) {
                inputs[i] = Input.id(ids[i], quantities == null || i >= quantities.length ? 1 : quantities[i]);
            }
            return inputs;
        }

        public String getName() { return name; }
        public int getLevel() { return level; }
        public MagicAction getSpell() { return spell; }
        public CastMode getCastMode() { return castMode; }
        public int[] getRuneIds() { return runeIds.clone(); }
        public int[] getRuneQuantities() { return runeQuantities.clone(); }
        public Input[] getInputs() { return inputs.clone(); }
        public int getCoinsPerCast() { return coinsPerCast; }
    }

    private final Recipe recipe;

    public MagicItemProcessor(Recipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public boolean validate() {
        if (recipe == null || recipe.getInputs().length == 0 || recipe.getSpell() == null) {
            return false;
        }
        int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
        if (level < recipe.getLevel()) {
            log.info("Insufficient magic level for {}: need {}, have {}",
                    recipe.getName(), recipe.getLevel(), level);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getBankingRequirements() {
        List<String> requirements = new ArrayList<>();
        for (Input input : recipe.getInputs()) {
            requirements.add(input.getLabel());
        }
        for (int i = 0; i < recipe.getRuneIds().length; i++) {
            requirements.add(getRuneName(recipe.getRuneIds()[i]) + " x" + recipe.getRuneQuantities()[i]);
        }
        if (recipe.getCoinsPerCast() > 0) {
            requirements.add("Coins x" + recipe.getCoinsPerCast());
        }
        return requirements;
    }

    @Override
    public boolean hasRequiredItems() {
        if (!hasInputs()) {
            return false;
        }
        if (recipe.getCoinsPerCast() > 0 &&
                Rs2Inventory.itemQuantity(ItemID.COINS) < recipe.getCoinsPerCast()) {
            return false;
        }
        return hasRunes(1);
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

        // Equip first so staff-provided runes are excluded from the missing check.
        if (StaffUtils.equipBestAvailableStaff(recipe.getRuneIds())) {
            Rs2Random.waitEx(140, 70);
            sleepUntil(() -> StaffUtils.getEquippedStaff() != null, 3000);
        }

        if (!hasBankSupplies()) {
            log.info("Bank does not have all supplies needed for {}", recipe.getName());
            return false;
        }

        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            if (!sleepUntil(Rs2Inventory::isEmpty, 3000)) {
                log.info("Failed to deposit inventory");
                return false;
            }
        }

        int casts = affordableCasts();
        if (casts < 1) {
            log.info("Not enough supplies for a single {} cast", recipe.getName());
            return false;
        }

        if (!withdrawRunesAndCoins(casts)) {
            return false;
        }
        Rs2Random.waitEx(130, 70);
        return withdrawInputs(casts);
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

        if (!castOnItem()) {
            log.info("Failed to cast {} on the selected item", recipe.getName());
            return false;
        }

        Rs2Random.waitEx(140, 70);
        if (sleepUntil(() -> Rs2Player.isAnimating(1000), 2000)) {
            sleepUntil(() -> !Rs2Player.isAnimating(1000), 10000);
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
        return "Casting " + recipe.getName().toLowerCase() + "...";
    }

    @Override
    public boolean isActivelyProcessing() {
        return Rs2Player.isAnimating(1000);
    }

    // ---------------------------------------------------------------------
    // Casting
    // ---------------------------------------------------------------------

    private boolean castOnItem() {
        Input input = recipe.getInputs()[0];
        switch (recipe.getCastMode()) {
            case CAST_ONLY:
                return Rs2Magic.cast(recipe.getSpell());
            case ALCH_HIGH:
                alch(input);
                return true;
            case ALCH_LOW:
                return Rs2Magic.cast(MagicAction.LOW_LEVEL_ALCHEMY) && input.interact("Cast");
            case SUPERHEAT:
                superHeat(input);
                return true;
            case CAST_CAST:
                return Rs2Magic.cast(recipe.getSpell()) && input.interact("Cast");
            case CAST_USE:
            default:
                return Rs2Magic.cast(recipe.getSpell()) && input.interact("Use");
        }
    }

    private void alch(Input input) {
        if (input.isNamed()) {
            Rs2Magic.alch(input.getLabel());
        } else {
            Rs2Magic.alch(Rs2Inventory.get(input.getId()));
        }
    }

    private void superHeat(Input input) {
        if (input.isNamed()) {
            Rs2Magic.superHeat(input.getLabel());
        } else {
            Rs2Magic.superHeat(input.getId());
        }
    }

    // ---------------------------------------------------------------------
    // Requirement checks
    // ---------------------------------------------------------------------

    private boolean hasInputs() {
        for (Input input : recipe.getInputs()) {
            if (input.inventoryCount() < input.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRunes(int casts) {
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = recipe.getRuneIds();
        int[] runeQuantities = recipe.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int needed = Math.max(1, runeQuantities[i]) * casts;
            int available = Rs2Inventory.itemQuantity(runeIds[i]);
            if (available < needed) {
                log.info("Missing rune {} (have {}, need {})", getRuneName(runeIds[i]), available, needed);
                return false;
            }
        }
        return true;
    }

    private boolean hasBankSupplies() {
        for (Input input : recipe.getInputs()) {
            if (input.inventoryCount() + input.bankCount() < input.getQuantity()) {
                return false;
            }
        }

        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = recipe.getRuneIds();
        int[] runeQuantities = recipe.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int need = Math.max(1, runeQuantities[i]);
            if (Rs2Inventory.itemQuantity(runeIds[i]) + Rs2Bank.count(runeIds[i]) < need) {
                return false;
            }
        }

        if (recipe.getCoinsPerCast() > 0 &&
                Rs2Inventory.itemQuantity(ItemID.COINS) + Rs2Bank.count(ItemID.COINS) < recipe.getCoinsPerCast()) {
            return false;
        }
        return true;
    }

    /** Largest batch the bank (runes, coins and materials) can fully support. */
    private int affordableCasts() {
        int limit = Integer.MAX_VALUE;
        for (Input input : recipe.getInputs()) {
            limit = Math.min(limit, input.bankCount() / input.getQuantity());
        }

        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = recipe.getRuneIds();
        int[] runeQuantities = recipe.getRuneQuantities();
        for (int i = 0; i < runeIds.length; i++) {
            if (provided.contains(runeIds[i])) {
                continue;
            }
            int perCast = Math.max(1, runeQuantities[i]);
            limit = Math.min(limit, Rs2Bank.count(runeIds[i]) / perCast);
        }

        if (recipe.getCoinsPerCast() > 0) {
            limit = Math.min(limit, Rs2Bank.count(ItemID.COINS) / recipe.getCoinsPerCast());
        }
        return limit == Integer.MAX_VALUE ? 0 : limit;
    }

    // ---------------------------------------------------------------------
    // Withdrawal
    // ---------------------------------------------------------------------

    private boolean withdrawRunesAndCoins(int casts) {
        Set<Integer> provided = StaffUtils.getProvidedRunes();
        int[] runeIds = recipe.getRuneIds();
        int[] runeQuantities = recipe.getRuneQuantities();
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

        if (recipe.getCoinsPerCast() > 0) {
            int needed = recipe.getCoinsPerCast() * casts;
            int bankCoins = Rs2Bank.count(ItemID.COINS);
            if (bankCoins < needed || !withdrawRune(ItemID.COINS, bankCoins, needed)) {
                return false;
            }
            if (Rs2Inventory.itemQuantity(ItemID.COINS) < needed) {
                log.info("Withdrawal verification failed for coins");
                return false;
            }
        }
        return true;
    }

    private boolean withdrawRune(int id, int bankQty, int needed) {
        boolean requested = bankQty > needed
                ? Rs2Bank.withdrawX(id, needed)
                : Rs2Bank.withdrawAll(id);
        if (!requested) {
            log.info("Bank rejected withdrawal of rune item {}", id);
            return false;
        }
        return sleepUntil(() -> Rs2Inventory.itemQuantity(id) >= needed, 3500);
    }

    private boolean withdrawInputs(int casts) {
        int freeSlots = Rs2Inventory.emptySlotCount();
        for (Input input : recipe.getInputs()) {
            int requested = Math.min(input.bankCount(), input.getQuantity() * casts);
            boolean stackable = input.isStackable();
            if (!stackable) {
                requested = Math.min(requested, Math.max(1, freeSlots - recipe.getInputs().length));
            }
            if (!input.withdraw(requested, stackable)) {
                return false;
            }
            if (input.inventoryCount() < input.getQuantity()) {
                log.info("Withdrawal verification failed for {}", input.getLabel());
                return false;
            }
        }
        return true;
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
            case ItemID.ASTRALRUNE: return "Astral rune";
            case ItemID.CHAOSRUNE: return "Chaos rune";
            default: return "Rune " + runeId;
        }
    }
}
