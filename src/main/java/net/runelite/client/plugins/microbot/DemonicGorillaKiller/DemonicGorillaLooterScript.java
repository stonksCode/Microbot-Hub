package net.runelite.client.plugins.microbot.DemonicGorillaKiller;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Loot loop for Demonic Gorillas. Uses the current loot engine instead of the deprecated
 * Rs2GroundItem helpers, honours the configured loot list, and tracks the GE value of the drops
 * so the overlay no longer depends on Microbot's deprecated aggregate loot records.
 */
public class DemonicGorillaLooterScript extends Script {

    private static final String MALICIOUS_ASHES = "Malicious ashes";

    private String lastLootItems;
    private Set<String> wantedNames = new HashSet<>();

    public boolean run(DemonicGorillaConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (DemonicGorillaScript.BOT_STATUS == DemonicGorillaScript.State.BANKING
                        || DemonicGorillaScript.BOT_STATUS == DemonicGorillaScript.State.TRAVEL_TO_GORILLAS) {
                    return;
                }
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2Inventory.isFull() || Rs2Inventory.emptySlotCount() <= 0) return;

                lootItems(config);
                if (config.scatterAshes()) {
                    scatterAshes(config);
                }
            } catch (Exception ex) {
                System.out.println("Demonic Gorilla Looter: " + ex.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void lootItems(DemonicGorillaConfig config) {
        String[] names = getWantedNames(config);
        long valueBefore = groundLootValue(config, names);

        LootingParameters params = new LootingParameters(
                0,
                0,
                config.lootRadius(),
                1,
                0,
                false,
                config.lootMyLootOnly(),
                names
        );
        params.setEatFoodForSpace(false);

        boolean looted = Rs2LootEngine.with(params)
                .addByNames()
                .addCoins()
                .addRunes(1)
                .addUntradables()
                .loot();

        if (looted) {
            long collected = valueBefore - groundLootValue(config, names);
            DemonicGorillaScript.addLootValue(collected);
        }
    }

    private String[] getWantedNames(DemonicGorillaConfig config) {
        String configured = config.lootItems();
        if (!configured.equals(lastLootItems)) {
            lastLootItems = configured;
            wantedNames = Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .map(name -> name.toLowerCase())
                    .collect(Collectors.toCollection(HashSet::new));
        }
        return wantedNames.toArray(String[]::new);
    }

    /** GE value of the drops we actually intend to pick up, used to update the overlay total. */
    private long groundLootValue(DemonicGorillaConfig config, String[] names) {
        return Microbot.getRs2TileItemCache().query()
                .within(config.lootRadius())
                .where(item -> item.isLootAble() && !item.isDespawned() && isWantedItem(item, names))
                .toListOnClientThread()
                .stream()
                .mapToLong(Rs2TileItemModel::getTotalGeValue)
                .filter(value -> value > 0)
                .sum();
    }

    private boolean isWantedItem(Rs2TileItemModel item, String[] names) {
        String name = item.getName();
        if (name == null) return false;
        String lowerName = name.toLowerCase();
        for (String wanted : names) {
            if (lowerName.contains(wanted)) return true;
        }
        return lowerName.endsWith(" rune") || lowerName.equals("coins") || !item.isTradeable();
    }

    private void scatterAshes(DemonicGorillaConfig config) {
        if (Rs2Inventory.isFull()) return;
        Rs2TileItemModel ashes = Microbot.getRs2TileItemCache().query()
                .withName(MALICIOUS_ASHES)
                .where(Rs2TileItemModel::isLootAble)
                .within(config.lootRadius())
                .nearest();
        if (ashes == null || !ashes.pickup()) return;
        if (sleepUntil(() -> Rs2Inventory.contains(MALICIOUS_ASHES), 2000)) {
            Rs2Inventory.interact(MALICIOUS_ASHES, "Scatter");
            sleep(600);
        }
    }
}
