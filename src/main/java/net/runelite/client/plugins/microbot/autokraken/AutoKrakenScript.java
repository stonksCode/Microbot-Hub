package net.runelite.client.plugins.microbot.autokraken;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
public class AutoKrakenScript extends Script {

    private static final int MAIN_WHIRLPOOL_ID = NpcID.SLAYER_KRAKEN_BOSS_WHIRLPOOL;
    private static final int KRAKEN_ID = NpcID.SLAYER_KRAKEN_BOSS;
    private static final int SMALL_WHIRLPOOL_ID = NpcID.SLAYER_KRAKEN_BOSS_TENTACLE_WHIRLPOOL;

    private static final long LOOP_DELAY_MS = 600L;
    private static final long OPENER_RETRY_MS = 1_500L;
    private static final long BOSS_DESPAWN_GRACE_MS = 2_500L;
    private static final long ATTACK_RETRY_MS = 1_200L;
    private static final long LOOT_SETTLE_MS = 900L;

    @Getter
    private volatile AutoKrakenState state = AutoKrakenState.IDLE;
    @Getter
    private volatile int killCount = 0;
    @Getter
    private volatile String stopReason = "";

    // Scene indexes of small whirlpools already disturbed this attempt.
    private final Set<Integer> disturbedSmallIndexes = new HashSet<>();

    private long lastBossSeenAt = 0L;
    private long lastOpenerAttemptAt = 0L;
    private long lastAttackAt = 0L;
    private long lootStartedAt = 0L;

    // Only switch quick prayers back off on stop if this script turned them on.
    private boolean enabledQuickPrayers = false;

    public boolean run(AutoKrakenConfig config) {
        state = AutoKrakenState.IDLE;
        killCount = 0;
        stopReason = "";
        disturbedSmallIndexes.clear();
        lastBossSeenAt = 0L;
        lastOpenerAttemptAt = 0L;
        lastAttackAt = 0L;
        lootStartedAt = 0L;
        enabledQuickPrayers = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Microbot.pauseAllScripts.get()) return;

                switch (state) {
                    case IDLE:
                        handleIdle(config);
                        break;
                    case OPENING:
                        handleOpening(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case LOOTING:
                        handleLooting(config);
                        break;
                    case STOPPED:
                        return;
                }
            } catch (Exception ex) {
                log.error("[AutoKraken] loop error: {}", ex.getMessage(), ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleIdle(AutoKrakenConfig config) {
        Microbot.status = "Kraken: idle";

        if (config.requireSlayerTask() && !isOnKrakenTask()) {
            requestStop("Not on a Kraken slayer task.");
            return;
        }

        if (config.stopWhenOutOfFood() && Rs2Inventory.getInventoryFood().isEmpty()) {
            requestStop("Out of food.");
            return;
        }

        Rs2NpcModel boss = getBoss();
        if (boss != null) {
            if (isDead(boss)) {
                // The corpse is still in the scene. Wait for it to despawn rather than
                // re-entering looting on every tick after a kill.
                Microbot.status = "Kraken: waiting for corpse";
                return;
            }
            lastBossSeenAt = System.currentTimeMillis();
            state = AutoKrakenState.FIGHTING;
            return;
        }

        if (getMainWhirlpool() != null || anyDisturbableSmallWhirlpool()) {
            disturbedSmallIndexes.clear();
            state = AutoKrakenState.OPENING;
            return;
        }

        Microbot.status = "Kraken: waiting for whirlpool";
    }

    private void handleOpening(AutoKrakenConfig config) {
        Rs2NpcModel boss = getBoss();
        if (boss != null && !isDead(boss)) {
            lastBossSeenAt = System.currentTimeMillis();
            state = AutoKrakenState.FIGHTING;
            return;
        }

        Rs2NpcModel whirlpool = getMainWhirlpool();
        boolean hasExplosive = Rs2Inventory.hasItem(ItemID.FISHING_EXPLOSIVE);

        if (config.useFishingExplosive() && hasExplosive && whirlpool != null) {
            long now = System.currentTimeMillis();
            if (now - lastOpenerAttemptAt < OPENER_RETRY_MS) {
                return;
            }
            lastOpenerAttemptAt = now;

            Microbot.status = "Kraken: using fishing explosive";
            if (!Rs2Inventory.useItemOnNpc(ItemID.FISHING_EXPLOSIVE, whirlpool.getNpc())) {
                Microbot.log("[AutoKraken] Could not use the fishing explosive on the whirlpool.");
                return;
            }

            sleep(600, 900);
            if (waitForBoss(6_000)) {
                lastBossSeenAt = System.currentTimeMillis();
                state = AutoKrakenState.FIGHTING;
            } else {
                Microbot.log("[AutoKraken] Fishing explosive did not wake the Kraken; retrying.");
            }
            return;
        }

        if (whirlpool == null && !anyDisturbableSmallWhirlpool()) {
            state = AutoKrakenState.IDLE;
            return;
        }

        if (config.useFishingExplosive() && !config.fallbackToDisturb() && !hasExplosive) {
            requestStop("Out of fishing explosives.");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastOpenerAttemptAt < OPENER_RETRY_MS) {
            return;
        }
        lastOpenerAttemptAt = now;

        Microbot.status = "Kraken: disturbing whirlpools";

        if (disturbWhirlpools()) {
            if (waitForBoss(8_000)) {
                lastBossSeenAt = System.currentTimeMillis();
                state = AutoKrakenState.FIGHTING;
            } else {
                Microbot.log("[AutoKraken] Whirlpools disturbed but the Kraken did not wake; retrying.");
                disturbedSmallIndexes.clear();
            }
        }
    }

    /**
     * Disturbs all four small whirlpools and then the main whirlpool. Used as a fallback
     * when the player has no fishing explosives.
     */
    private boolean disturbWhirlpools() {
        List<Rs2NpcModel> smalls = Microbot.getRs2NpcCache().query()
                .withId(SMALL_WHIRLPOOL_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .where(npc -> !disturbedSmallIndexes.contains(npc.getIndex()))
                .toList();

        for (Rs2NpcModel npc : smalls) {
            if (disturbedSmallIndexes.size() >= 4) break;
            Microbot.status = "Kraken: disturbing " + (disturbedSmallIndexes.size() + 1) + "/5";
            if (npc.click("Disturb")) {
                disturbedSmallIndexes.add(npc.getIndex());
            }
            sleep(150, 300);
        }

        // The large whirlpool only gains its "Disturb" option once all four small
        // whirlpools have been disturbed, so wait briefly for the option to appear.
        Rs2NpcModel main = getMainWhirlpool();
        long deadline = System.currentTimeMillis() + 2_500L;
        while (main != null && !hasAction(main, "Disturb") && System.currentTimeMillis() < deadline) {
            sleep(100, 200);
            main = getMainWhirlpool();
        }

        if (main != null) {
            Microbot.status = "Kraken: disturbing 5/5";
            main.click("Disturb");
            sleep(150, 300);
        }

        return true;
    }

    private boolean waitForBoss(int timeoutMs) {
        return sleepUntil(() -> {
            Rs2NpcModel boss = getBoss();
            return boss != null && !isDead(boss);
        }, timeoutMs);
    }

    private void handleFighting(AutoKrakenConfig config) {
        long now = System.currentTimeMillis();
        Rs2NpcModel boss = getBoss();

        if (boss == null || isDead(boss)) {
            // The boss NPC despawns when it dies. Allow a short grace period so a cache
            // flicker during the spawn transition is not counted as a kill.
            if (lastBossSeenAt == 0L) {
                lastBossSeenAt = now;
            }
            if (now - lastBossSeenAt >= BOSS_DESPAWN_GRACE_MS) {
                killCount++;
                startLooting();
            } else {
                Microbot.status = "Kraken: waiting for boss";
            }
            return;
        }

        lastBossSeenAt = now;
        Microbot.status = "Kraken: fighting";

        handleFood(config);
        handlePrayer(config);

        if (!isInteractingWith(boss) && now - lastAttackAt >= ATTACK_RETRY_MS) {
            lastAttackAt = now;
            boss.click("Attack");
        }
    }

    private void handleFood(AutoKrakenConfig config) {
        int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        int currentHp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        if (maxHp <= 0 || currentHp <= 0) return;

        int percent = (currentHp * 100) / maxHp;
        if (percent <= config.eatAtPercent()) {
            Rs2Player.eatAt(config.eatAtPercent());
        }
    }

    private void handlePrayer(AutoKrakenConfig config) {
        if (!config.useQuickPrayers()) return;
        if (!Rs2Prayer.isQuickPrayerEnabled()) {
            if (Rs2Prayer.toggleQuickPrayer(true)) {
                enabledQuickPrayers = true;
            }
        }
    }

    private void startLooting() {
        lootStartedAt = System.currentTimeMillis();
        state = AutoKrakenState.LOOTING;
    }

    private void handleLooting(AutoKrakenConfig config) {
        long now = System.currentTimeMillis();
        if (lootStartedAt == 0L) {
            lootStartedAt = now;
        }
        if (now - lootStartedAt < LOOT_SETTLE_MS) {
            Microbot.status = "Kraken: waiting for drops";
            return;
        }

        Microbot.status = "Kraken: looting";

        // Instanced room: no range/ownership filters are needed. The instance also keeps
        // drops on the ground for a long time, so no delayed-looting pass is required.
        LootingParameters params = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                1,
                0,
                false,
                false
        );

        Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                .withLootAction(Rs2GroundItem::coreLoot);

        builder.addCustom("kraken-uniques", AutoKrakenScript::isKrakenUnique, null);
        addCustomNames(builder, config.listOfItemsToLoot());
        if (config.lootCoins()) {
            builder.addCoins();
        }
        if (config.toggleLootByValue()) {
            builder.addByValue();
        }

        builder.loot();
        sleep(200, 400);

        lootStartedAt = 0L;
        lastBossSeenAt = 0L;
        lastAttackAt = 0L;

        if (Rs2Inventory.isFull()) {
            requestStop("Inventory full.");
            return;
        }

        disturbedSmallIndexes.clear();
        state = AutoKrakenState.IDLE;
    }

    public void requestStop(String reason) {
        if (state == AutoKrakenState.STOPPED) return;
        stopReason = reason;
        Microbot.log("[AutoKraken] Stopping: " + reason);
        state = AutoKrakenState.STOPPED;
        disableQuickPrayersIfWeEnabled();
    }

    private static boolean isOnKrakenTask() {
        String task = Rs2Slayer.getSlayerTask();
        return task != null && task.toLowerCase().contains("kraken");
    }

    private static Rs2NpcModel getBoss() {
        return Microbot.getRs2NpcCache().query()
                .withId(KRAKEN_ID)
                .nearest();
    }

    private static Rs2NpcModel getMainWhirlpool() {
        // Do not require the "Disturb" action here: it may not exist until the four
        // small whirlpools are disturbed, and the fishing explosive is used on the
        // whirlpool itself rather than through its menu action.
        return Microbot.getRs2NpcCache().query()
                .withId(MAIN_WHIRLPOOL_ID)
                .nearest();
    }

    private static boolean anyDisturbableSmallWhirlpool() {
        return Microbot.getRs2NpcCache().query()
                .withId(SMALL_WHIRLPOOL_ID)
                .where(npc -> hasAction(npc, "Disturb"))
                .first() != null;
    }

    private static boolean isDead(Rs2NpcModel npc) {
        if (npc == null) return false;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> npc.getNpc().isDead())
                .orElse(false);
    }

    private static boolean isInteractingWith(Rs2NpcModel npc) {
        if (npc == null) return false;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {
                    Player local = Microbot.getClient().getLocalPlayer();
                    return local != null && local.getInteracting() == npc.getNpc();
                })
                .orElse(false);
    }

    private static boolean hasAction(Rs2NpcModel npc, String action) {
        if (npc == null || npc.getNpc() == null) return false;
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> {
                    NPCComposition composition = npc.getNpc().getTransformedComposition();
                    if (composition == null) return false;
                    String[] actions = composition.getActions();
                    if (actions == null) return false;
                    for (String value : actions) {
                        if (action.equalsIgnoreCase(value)) return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private static boolean isKrakenUnique(GroundItem groundItem) {
        String name = groundItem.getName() == null ? "" : groundItem.getName().trim().toLowerCase();
        return name.contains("kraken tentacle")
                || name.contains("trident of the seas")
                || name.contains("jar of dirt")
                || name.contains("pet kraken")
                || name.contains("brimstone key")
                || name.contains("clue scroll")
                || name.contains("scroll box");
    }

    private static void addCustomNames(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null) return;

        Set<String> needles = new HashSet<>();
        Arrays.stream(csvNames.split(","))
                .map(name -> name == null ? "" : name.trim().toLowerCase())
                .filter(name -> !name.isEmpty())
                .forEach(needles::add);
        if (needles.isEmpty()) return;

        Predicate<GroundItem> byNames = groundItem -> {
            String name = groundItem.getName() == null ? "" : groundItem.getName().trim().toLowerCase();
            for (String needle : needles) {
                if (name.contains(needle)) return true;
            }
            return false;
        };
        builder.addCustom("names", byNames, null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        state = AutoKrakenState.STOPPED;
        disableQuickPrayersIfWeEnabled();
    }

    private void disableQuickPrayersIfWeEnabled() {
        if (!enabledQuickPrayers) return;
        enabledQuickPrayers = false;
        if (Rs2Prayer.isQuickPrayerEnabled()) {
            Rs2Prayer.toggleQuickPrayer(false);
        }
    }
}
