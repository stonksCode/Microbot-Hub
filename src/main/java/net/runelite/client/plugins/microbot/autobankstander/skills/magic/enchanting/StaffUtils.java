package net.runelite.client.plugins.microbot.autobankstander.skills.magic.enchanting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaffUtils {

    /**
     * Get the currently equipped staff, using the client's staff lookup instead of
     * maintaining a parallel list of staff item IDs.
     */
    public static Rs2Staff getEquippedStaff() {
        return Rs2Equipment.all(EquipmentInventorySlot.WEAPON)
                .map(Rs2ItemModel::getId)
                .map(Rs2Staff::byItemId)
                .filter(staff -> staff != null && staff != Rs2Staff.NONE)
                .findFirst()
                .orElse(null);
    }

    /** Rune IDs that are provided by the equipped staff. */
    public static Set<Integer> getProvidedRunes() {
        Set<Integer> provided = new HashSet<>();
        Rs2Staff staff = getEquippedStaff();
        if (staff == null) {
            return provided;
        }
        for (Runes rune : Runes.values()) {
            if (staff.provides(rune)) {
                provided.add(rune.getItemId());
            }
        }
        return provided;
    }

    /** Find the most useful available staff for the required runes. */
    public static Rs2Staff findBestAvailableStaff(int[] requiredRunes) {
        List<Runes> runes = Arrays.stream(requiredRunes)
                .mapToObj(Runes::byItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Rs2Staff> candidates = Rs2Magic.findStavesByRunes(runes);
        if (candidates == null || candidates.isEmpty()) {
            candidates = Arrays.asList(Rs2Staff.values());
        }

        Rs2Staff best = null;
        int bestScore = -1;
        for (Rs2Staff staff : candidates) {
            if (staff == null || staff == Rs2Staff.NONE) {
                continue;
            }
            boolean available = Rs2Inventory.hasItem(staff.getItemID()) || Rs2Bank.hasItem(staff.getItemID());
            if (!available) {
                continue;
            }
            int score = calculateStaffScore(staff, requiredRunes);
            if (score > bestScore) {
                bestScore = score;
                best = staff;
            }
        }
        return best;
    }

    /** Higher scores mean the staff covers more of the required runes. */
    public static int calculateStaffScore(Rs2Staff staff, int[] requiredRunes) {
        if (staff == null || staff == Rs2Staff.NONE) {
            return 0;
        }

        int matching = 0;
        for (int runeId : requiredRunes) {
            Runes rune = Runes.byItemId(runeId);
            if (rune != null && staff.provides(rune)) {
                matching++;
            }
        }

        if (matching == requiredRunes.length) {
            return 1000 + staff.getRunes().size();
        }
        return matching * 100;
    }

    /** Equip the best staff we already have, withdrawing it from the bank if needed. */
    public static boolean equipBestAvailableStaff(int[] requiredRunes) {
        Rs2Staff current = getEquippedStaff();
        if (current != null && calculateStaffScore(current, requiredRunes) > 0) {
            log.info("Already have suitable staff equipped: {}", current.name());
            return true;
        }

        Rs2Staff best = findBestAvailableStaff(requiredRunes);
        if (best == null) {
            log.info("No suitable staff found in inventory or bank");
            return false;
        }

        if (Rs2Inventory.hasItem(best.getItemID())) {
            log.info("Equipping {} from inventory", best.name());
            return Rs2Inventory.wield(best.getItemID());
        }
        if (Rs2Bank.isOpen() && Rs2Bank.hasItem(best.getItemID())) {
            log.info("Withdrawing and equipping {} from bank", best.name());
            return Rs2Bank.withdrawAndEquip(best.getItemID());
        }
        return false;
    }
}
