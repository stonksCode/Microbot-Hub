package net.runelite.client.plugins.microbot.DemonicGorillaKiller;

import net.runelite.api.HeadIcon;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;

// Script heavily inspired by Tormented Demon script
public class DemonicGorillaScript extends Script {

    public static final int DEMONIC_GORILLA_PRAYER_SWITCH = 7224;
    public static final int DEMONIC_GORILLA_MAGIC_ATTACK = 7225;
    public static final int DEMONIC_GORILLA_MELEE_ATTACK = 7226;
    public static final int DEMONIC_GORILLA_RANGED_ATTACK = 7227;
    public static final int DEMONIC_GORILLA_AOE_ATTACK = 7228;

    // Behind rope in the entrance of the cave
    private static final WorldPoint SAFE_LOCATION = new WorldPoint(2465, 3494, 0);
    private static final WorldPoint GORILLA_LOCATION = new WorldPoint(2100, 5643, 0);
    private static final long GEAR_SWITCH_GRACE_MS = 350L;
    private static final long OUT_OF_COMBAT_RETARGET_MS = 6000L;
    private static final int FAILED_ATTACK_LIMIT = 7;
    private static final int FAILED_HEAD_ICON_LIMIT = 3;

    // Setup cache slots: banking, range, magic, melee
    private static final int SETUP_BANKING = 0;
    private static final int SETUP_RANGE = 1;
    private static final int SETUP_MAGIC = 2;
    private static final int SETUP_MELEE = 3;

    public static int killCount = 0;
    public static int currentTripKillCount = 0;
    public static long totalLootValue = 0;
    public static Rs2PrayerEnum currentDefensivePrayer = null;
    public static Rs2NpcModel currentTarget = null;
    public static boolean lootAttempted = false;
    public static State BOT_STATUS = State.BANKING;
    public static int gameTickCount = 0;
    public static ArmorEquiped currentGear = ArmorEquiped.MELEE;

    // Written from the client thread by the plugin, read from the script thread.
    public volatile LocalPoint demonicGorillaRockPosition = null;
    public volatile int demonicGorillaRockLifeCycle = -1;

    private Rs2PrayerEnum currentOffensivePrayer = null;
    private HeadIcon currentOverheadIcon = null;
    private String lastChatMessage = "";
    private BankingStep bankingStep = BankingStep.BANK;
    private Instant outOfCombatTime;
    private int failedCount = 0;
    private WorldPoint lastGorillaLocation;
    private int failedAttacks = 0;
    private int lastAttackTick = 0;
    private long gearSwitchGraceUntil = 0;
    private boolean isRunning = false;
    private boolean qolStopped = false;

    private Rs2InventorySetup bankingGear;
    private Rs2InventorySetup rangeGear;
    private Rs2InventorySetup magicGear;
    private Rs2InventorySetup meleeGear;
    private final String[] setupNames = new String[4];

    private void enableAntibanSettings() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(EXTREME);
    }

    public boolean run(DemonicGorillaConfig config) {
        bankingStep = BankingStep.BANK;
        isRunning = true;
        Microbot.enableAutoRunOn = false;
        enableAntibanSettings();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (!preflightChecks(config)) return;
                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case TRAVEL_TO_GORILLAS:
                        handleTravel();
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }
            } catch (Exception ex) {
                logOnceToChat("Error in main loop: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Validates configuration and resolves inventory setups. Setups are cached and only rebuilt
     * when the configured setup name changes; rebuilding them every 50 ms was the hottest part of
     * the old loop.
     */
    private boolean preflightChecks(DemonicGorillaConfig config) {
        if (config.gearSetup() == null) {
            return fail("Your banking gear setup is null. Select it in the config, then select a different setup and re-select your banking setup.");
        }
        int enabledStyles = (config.useMagicStyle() ? 1 : 0)
                + (config.useRangeStyle() ? 1 : 0)
                + (config.useMeleeStyle() ? 1 : 0);
        if (enabledStyles == 0) {
            return fail("No combat style is enabled. Enable at least one style in the Gear Settings section.");
        }
        if (config.useMagicStyle() && config.magicGear() == null) {
            return fail("You've selected magic combat style, but your magic inventory setup is null. Please set it in the config.");
        }
        if (config.useRangeStyle() && config.rangeGear() == null) {
            return fail("You've selected ranged combat style, but your range inventory setup is null. Please set it in the config.");
        }
        if (config.useMeleeStyle() && config.meleeGear() == null) {
            return fail("You've selected melee combat style, but your melee inventory setup is null. Please set it in the config.");
        }

        bankingGear = resolveSetup(config.gearSetup(), bankingGear, SETUP_BANKING);
        rangeGear = resolveSetup(config.rangeGear(), rangeGear, SETUP_RANGE);
        magicGear = resolveSetup(config.magicGear(), magicGear, SETUP_MAGIC);
        meleeGear = resolveSetup(config.meleeGear(), meleeGear, SETUP_MELEE);

        if (!qolStopped) {
            Plugin qolPlugin = Microbot.getActiveMicrobotPlugins().stream()
                    .filter(p -> p.getClass().getSimpleName().contains("QoLPlugin"))
                    .findFirst().orElse(null);
            if (qolPlugin != null) {
                Microbot.stopPlugin(qolPlugin.getClass());
            }
            qolStopped = true;
        }
        return true;
    }

    private Rs2InventorySetup resolveSetup(InventorySetup source, Rs2InventorySetup cached, int slot) {
        if (source == null) return null;
        if (cached != null && Objects.equals(setupNames[slot], source.getName())) return cached;
        setupNames[slot] = source.getName();
        return new Rs2InventorySetup(source, mainScheduledFuture);
    }

    private boolean fail(String message) {
        Microbot.showMessage(message);
        shutdown();
        return false;
    }

    private void handleTravel() {
        if (Rs2Player.distanceTo(GORILLA_LOCATION) < 5 || Rs2Walker.walkTo(GORILLA_LOCATION)) {
            BOT_STATUS = State.FIGHTING;
        }
    }

    private void handleBanking(DemonicGorillaConfig config) {
        if (bankingGear == null) return;
        switch (bankingStep) {
            case BANK:
                if (bankingGear.doesInventoryMatch() && bankingGear.doesEquipmentMatch()) {
                    bankingStep = BankingStep.BANK;
                    BOT_STATUS = State.TRAVEL_TO_GORILLAS;
                    return;
                }
                Rs2Bank.walkToBank();
                Microbot.status = "Opening bank...";
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                bankingStep = BankingStep.LOAD_INVENTORY;
                break;

            case LOAD_INVENTORY:
                Microbot.status = "Loading inventory and equipment setup...";
                boolean equipmentLoaded = bankingGear.loadEquipment();
                boolean inventoryLoaded = bankingGear.loadInventory();
                if (!equipmentLoaded || !inventoryLoaded) {
                    fail("Failed to load the banking gear setup. Check your Inventory Setup.");
                    return;
                }
                if (replenishAtBank(config)) {
                    bankingGear.loadInventory();
                }
                Rs2Bank.closeBank();
                bankingStep = BankingStep.BANK;
                BOT_STATUS = State.TRAVEL_TO_GORILLAS;
                break;
        }
    }

    /** Tops up health and prayer before leaving the bank; returns true if anything was consumed. */
    private boolean replenishAtBank(DemonicGorillaConfig config) {
        boolean consumed = false;
        while (isRunning && this.isRunning()
                && (Rs2Player.getHealthPercentage() < config.minEatPercent()
                || Rs2Player.getPrayerPercentage() < config.minPrayerPercent())) {
            boolean ate = Rs2Player.eatAt(config.minEatPercent());
            boolean drank = Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
            if (!ate && !drank) break;
            consumed = true;
            sleep(1200);
        }
        return consumed;
    }

    private void handleFighting(DemonicGorillaConfig config) {
        if (currentTarget == null || currentTarget.getNpc().isDead()) {
            if (currentTarget != null) {
                killCount++;
                currentTripKillCount++;
            }
            handleNewTarget(config);
        }
        if (BOT_STATUS != State.FIGHTING || currentTarget == null) return;

        if (shouldRetreat(config)) {
            retreatToSafety();
            return;
        }
        handleTargetSelection();
        if (currentTarget == null) return;
        handleDemonicGorillaAttacks(config);
        if (currentTarget == null || BOT_STATUS != State.FIGHTING) return;
        handleGearSwitching(config);
        if (currentTarget == null) return;
        attackGorilla(config);
        if (currentTarget == null) return;
        evaluateAndConsumePotions(config);

        if (config.enableOffensivePrayer()) {
            activateOffensivePrayer(config);
        }
    }

    private void handleTargetSelection() {
        // Ensure the highlighted target is also the one we are attacking
        if (currentTarget != null) {
            Rs2NpcModel tempTarget = getTarget(true);
            if ((tempTarget != null && tempTarget.getIndex() != currentTarget.getIndex()) || currentTarget.getNpc().isDead()) {
                logOnceToChat("Invalid target was selected, switching to correct enemy");
                currentTarget = tempTarget;
            }
        }
        if (Rs2Player.isInCombat()) {
            outOfCombatTime = null;
            return;
        }
        if (outOfCombatTime == null) {
            outOfCombatTime = Instant.now();
        } else if (Instant.now().isAfter(outOfCombatTime.plusMillis(OUT_OF_COMBAT_RETARGET_MS))) {
            logOnceToChat("Out of combat, forcing new target");
            currentTarget = getTarget(true);
            if (currentTarget != null) {
                currentTarget.click("Attack");
            } else {
                logOnceToChat("Unable to force a target, walking back to the gorillas");
                Rs2Walker.walkTo(GORILLA_LOCATION);
            }
            outOfCombatTime = null;
        }
    }

    private void retreatToSafety() {
        currentTarget = null;
        currentOverheadIcon = null;
        currentTripKillCount = 0;
        disableAllPrayers();
        escapeToSafety();
        sleepUntil(() -> SAFE_LOCATION.equals(Rs2Player.getWorldLocation()), 5000);
        BOT_STATUS = State.BANKING;
        Microbot.log("Retreated - restoring banking gear");
        equipGear(bankingGear);
    }

    private void handleNewTarget(DemonicGorillaConfig config) {
        if (!lootAttempted) {
            Rs2Player.eatAt(config.minEatPercent());
            Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
            lootAttempted = true;
            currentTarget = null;
            lastGorillaLocation = null;
        }

        currentTarget = getTarget();
        if (currentTarget == null) {
            logOnceToChat("No target found for attack.");
            return;
        }
        try {
            currentOverheadIcon = currentTarget.getHeadIcon();
            if (currentOverheadIcon == null) {
                logOnceToChat("Failed to retrieve HeadIcon for target - NULL");
                return;
            }
        } catch (Exception e) {
            logOnceToChat("Failed to retrieve HeadIcon for target - Exception");
            return;
        }
        switchGear(config, currentOverheadIcon);
        lootAttempted = false;
    }

    private void attackGorilla(DemonicGorillaConfig config) {
        if (currentTarget == null || currentTarget.getNpc().isDead()) return;
        Rs2Player.eatAt(config.minEatPercent());
        Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
        if (Rs2Player.isAnimating(1600)) return;

        if (config.enableAutoSpecialAttacks()) {
            Rs2Combat.setSpecState(true, 500);
        }
        if (currentTarget.click("Attack")) {
            failedAttacks = 0;
        } else if (++failedAttacks >= FAILED_ATTACK_LIMIT) {
            currentTarget = getTarget(true);
            failedAttacks = 0;
        }
    }

    private void handleDemonicGorillaAttacks(DemonicGorillaConfig config) {
        if (currentTarget == null || currentTarget.getNpc().isDead()) return;

        int currentAnimation = currentTarget.getNpc().getAnimation();
        WorldPoint gorillaLocation = currentTarget.getWorldLocation();
        Rs2PrayerEnum newDefensivePrayer = null;
        boolean dodgedRock = false;

        if (currentAnimation == DEMONIC_GORILLA_MAGIC_ATTACK) {
            newDefensivePrayer = Rs2PrayerEnum.PROTECT_MAGIC;
            lastAttackTick = gameTickCount;
        } else if (currentAnimation == DEMONIC_GORILLA_RANGED_ATTACK) {
            newDefensivePrayer = Rs2PrayerEnum.PROTECT_RANGE;
            lastAttackTick = gameTickCount;
        } else if (currentAnimation == DEMONIC_GORILLA_MELEE_ATTACK) {
            newDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;
            lastAttackTick = gameTickCount;
        } else if (lastGorillaLocation != null && currentDefensivePrayer != Rs2PrayerEnum.PROTECT_MELEE) {
            int distance = lastGorillaLocation.distanceTo(gorillaLocation);
            if (distance > 0) {
                // The gorilla moved towards us - a melee swing is imminent
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;
            } else if (currentAnimation == -1 && gameTickCount - lastAttackTick >= 3) {
                // Fallback if an attack animation was missed: gorillas alternate mage/range
                if (currentDefensivePrayer == Rs2PrayerEnum.PROTECT_MAGIC) {
                    newDefensivePrayer = Rs2PrayerEnum.PROTECT_RANGE;
                } else if (currentDefensivePrayer == Rs2PrayerEnum.PROTECT_RANGE) {
                    newDefensivePrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                }
            }
        }

        if (newDefensivePrayer != null && newDefensivePrayer != currentDefensivePrayer
                && !Rs2Prayer.isPrayerActive(newDefensivePrayer)) {
            switchDefensivePrayer(newDefensivePrayer);
        }

        if (currentAnimation == DEMONIC_GORILLA_AOE_ATTACK && demonicGorillaRockPosition != null) {
            List<WorldPoint> dangerousWorldPoints = new ArrayList<>(Rs2Tile.getDangerousGraphicsObjectTiles().keySet());
            dangerousWorldPoints.add(Rs2Player.getWorldLocation());
            dangerousWorldPoints.add(gorillaLocation);
            dangerousWorldPoints.addAll(DemonicGorillaPlugin.lastLocation.snapshot());
            dangerousWorldPoints.add(new WorldPoint(demonicGorillaRockPosition.getX(), demonicGorillaRockPosition.getY(),
                    demonicGorillaRockPosition.getWorldView()));

            WorldPoint safeTile = findSafeTile(Rs2Player.getWorldLocation(), dangerousWorldPoints);
            if (safeTile != null) {
                dodgedRock = Rs2Walker.walkFastCanvas(safeTile);
                logOnceToChat("Walking to safe tile");
                sleep(1600);
            }
            demonicGorillaRockPosition = null;
            demonicGorillaRockLifeCycle = -1;
        }

        if (!dodgedRock && (currentGear == ArmorEquiped.RANGED || currentGear == ArmorEquiped.MAGIC)
                && currentAnimation != DEMONIC_GORILLA_MELEE_ATTACK
                && currentDefensivePrayer != Rs2PrayerEnum.PROTECT_MELEE
                && currentTarget.getNpc().getWorldArea().isInMeleeDistance(Microbot.getClient().getLocalPlayer().getWorldArea())) {
            moveAwayFromTarget();
        }

        lastGorillaLocation = gorillaLocation;
    }

    /**
     * Only re-equips when the overhead icon changes or the expected gear is no longer worn.
     * The choice is deterministic (preferred style, then the style already equipped), so the bot
     * does not flip between two valid styles on every switch.
     */
    private void handleGearSwitching(DemonicGorillaConfig config) {
        if (!config.autoGearSwitch() || currentTarget == null || System.currentTimeMillis() < gearSwitchGraceUntil) {
            return;
        }
        HeadIcon newOverheadIcon;
        try {
            newOverheadIcon = currentTarget.getHeadIcon();
        } catch (Exception e) {
            if (++failedCount >= FAILED_HEAD_ICON_LIMIT) {
                currentTarget = getTarget(true);
                failedCount = 0;
            }
            return;
        }
        if (newOverheadIcon == null) return;
        failedCount = 0;
        if (newOverheadIcon != currentOverheadIcon || !isExpectedGearEquipped()) {
            currentOverheadIcon = newOverheadIcon;
            switchGear(config, newOverheadIcon);
        }
    }

    public boolean moveAwayFromTarget() {
        if (currentTarget == null) return false;

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = currentTarget.getWorldLocation();

        // Direction from the gorilla to the player. We want to keep moving along it.
        int directionX = playerLocation.getX() - targetLocation.getX();
        int directionY = playerLocation.getY() - targetLocation.getY();
        double length = Math.sqrt(directionX * directionX + directionY * directionY);
        if (length < 0.001) {
            return false;
        }
        int moveX = (int) Math.round((directionX / length) * 2);
        int moveY = (int) Math.round((directionY / length) * 2);

        WorldPoint newPosition = new WorldPoint(playerLocation.getX() + moveX, playerLocation.getY() + moveY,
                playerLocation.getPlane());
        if (!isSafeRetreatTile(newPosition, playerLocation, targetLocation)) {
            for (WorldPoint alternative : List.of(
                    new WorldPoint(playerLocation.getX() + moveX, playerLocation.getY(), playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX() - moveX, playerLocation.getY(), playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX(), playerLocation.getY() + moveY, playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX(), playerLocation.getY() - moveY, playerLocation.getPlane()))) {
                if (isSafeRetreatTile(alternative, playerLocation, targetLocation)) {
                    newPosition = alternative;
                    break;
                }
            }
        }
        if (!isSafeRetreatTile(newPosition, playerLocation, targetLocation)) return false;
        return Rs2Walker.walkFastCanvas(newPosition);
    }

    private boolean isSafeRetreatTile(WorldPoint tile, WorldPoint playerLocation, WorldPoint targetLocation) {
        return Rs2Tile.isWalkable(tile) && !tile.equals(playerLocation) && !tile.equals(targetLocation);
    }

    private void escapeToSafety() {
        Rs2Inventory.interact("Royal seed pod", "Commune");
    }

    public Rs2NpcModel getTarget() {
        return getTarget(false);
    }

    public Rs2NpcModel getTarget(boolean force) {
        if (currentTarget != null && !currentTarget.getNpc().isDead() && !force) {
            return currentTarget;
        }
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        List<Rs2NpcModel> gorillas = Microbot.getRs2NpcCache().query()
                .withName("Demonic gorilla")
                .toListOnClientThread();
        if (gorillas.isEmpty()) {
            logOnceToChat("No demonic gorilla found.");
            return null;
        }

        Comparator<Rs2NpcModel> byDistance = Comparator.comparingInt(
                npc -> npc.getWorldLocation().distanceTo(playerLocation));

        Rs2NpcModel alreadyAttacking = gorillas.stream()
                .filter(npc -> !npc.getNpc().isDead() && npc.isInteractingWithPlayer())
                .min(byDistance).orElse(null);
        if (alreadyAttacking != null) return alreadyAttacking;

        return gorillas.stream()
                .filter(npc -> !npc.getNpc().isDead() && npc.getNpc().getInteracting() == null)
                .min(byDistance).orElse(null);
    }

    private WorldPoint findSafeTile(WorldPoint playerLocation, List<WorldPoint> dangerousWorldPoints) {
        List<WorldPoint> nearbyTiles = List.of(
                new WorldPoint(playerLocation.getX() + 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() + 2, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() - 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() - 2, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() + 1, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() + 2, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() - 1, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() - 2, playerLocation.getPlane())
        );

        for (WorldPoint tile : nearbyTiles) {
            if (!dangerousWorldPoints.contains(tile) && Rs2Tile.isWalkable(tile)) {
                logOnceToChat("Found safe tile: " + tile);
                return tile;
            }
        }
        logOnceToChat("No safe tile found!");
        return null;
    }

    private void switchDefensivePrayer(Rs2PrayerEnum newDefensivePrayer) {
        if (currentDefensivePrayer != null) {
            Rs2Prayer.toggle(currentDefensivePrayer, false);
        }
        Rs2Prayer.toggle(newDefensivePrayer, true);
        currentDefensivePrayer = newDefensivePrayer;
    }

    private void activateOffensivePrayer(DemonicGorillaConfig config) {
        Rs2PrayerEnum newOffensivePrayer = null;

        if (config.useMagicStyle() && currentGear == ArmorEquiped.MAGIC) {
            newOffensivePrayer = Rs2Prayer.getBestMagePrayer();
        } else if (config.useRangeStyle() && currentGear == ArmorEquiped.RANGED) {
            newOffensivePrayer = Rs2Prayer.getBestRangePrayer();
        } else if (config.useMeleeStyle() && currentGear == ArmorEquiped.MELEE) {
            newOffensivePrayer = Rs2Prayer.getBestMeleePrayer();
        }

        if (newOffensivePrayer != null && newOffensivePrayer != currentOffensivePrayer) {
            switchOffensivePrayer(newOffensivePrayer);
        }
    }

    private void switchOffensivePrayer(Rs2PrayerEnum newOffensivePrayer) {
        if (currentOffensivePrayer != null) {
            Rs2Prayer.toggle(currentOffensivePrayer, false);
        }
        Rs2Prayer.toggle(newOffensivePrayer, true);
        currentOffensivePrayer = newOffensivePrayer;
    }

    private Rs2InventorySetup setupFor(ArmorEquiped style) {
        switch (style) {
            case RANGED:
                return rangeGear;
            case MAGIC:
                return magicGear;
            case MELEE:
                return meleeGear;
            default:
                return null;
        }
    }

    private boolean isExpectedGearEquipped() {
        Rs2InventorySetup setup = setupFor(currentGear);
        return setup != null && setup.doesEquipmentMatch();
    }

    private void addCounterStyle(DemonicGorillaConfig config, ArmorEquiped style, List<ArmorEquiped> candidates) {
        boolean enabled;
        switch (style) {
            case RANGED:
                enabled = config.useRangeStyle();
                break;
            case MAGIC:
                enabled = config.useMagicStyle();
                break;
            default:
                enabled = config.useMeleeStyle();
                break;
        }
        if (enabled && setupFor(style) != null) candidates.add(style);
    }

    private ArmorEquiped chooseCounterStyle(DemonicGorillaConfig config, HeadIcon headIcon) {
        List<ArmorEquiped> candidates = new ArrayList<>();
        switch (headIcon) {
            case RANGED:
                addCounterStyle(config, ArmorEquiped.MELEE, candidates);
                addCounterStyle(config, ArmorEquiped.MAGIC, candidates);
                break;
            case MAGIC:
                addCounterStyle(config, ArmorEquiped.RANGED, candidates);
                addCounterStyle(config, ArmorEquiped.MELEE, candidates);
                break;
            case MELEE:
                addCounterStyle(config, ArmorEquiped.RANGED, candidates);
                addCounterStyle(config, ArmorEquiped.MAGIC, candidates);
                break;
            default:
                return null;
        }
        if (candidates.isEmpty()) return null;

        ArmorEquiped preferred = null;
        switch (config.preferredCounterStyle()) {
            case RANGED:
                preferred = ArmorEquiped.RANGED;
                break;
            case MAGIC:
                preferred = ArmorEquiped.MAGIC;
                break;
            case MELEE:
                preferred = ArmorEquiped.MELEE;
                break;
        }
        if (preferred != null && candidates.contains(preferred)) return preferred;

        // If the preferred style is not configured, avoid an unnecessary switch by keeping
        // whichever valid style is already worn.
        for (ArmorEquiped candidate : candidates) {
            Rs2InventorySetup setup = setupFor(candidate);
            if (setup != null && setup.doesEquipmentMatch()) return candidate;
        }
        return candidates.get(0);
    }

    private void switchGear(DemonicGorillaConfig config, HeadIcon combatNpcHeadIcon) {
        ArmorEquiped desired = chooseCounterStyle(config, combatNpcHeadIcon);
        if (desired == null) return;
        if (desired == currentGear && isExpectedGearEquipped()) return;

        ArmorEquiped previous = currentGear;
        if (equipGear(setupFor(desired))) {
            currentGear = desired;
            gearSwitchGraceUntil = System.currentTimeMillis() + GEAR_SWITCH_GRACE_MS;
        } else {
            currentGear = previous;
        }
    }

    private boolean equipGear(Rs2InventorySetup gear) {
        if (gear == null) return false;
        if (gear.doesEquipmentMatch()) return true;

        boolean success = gear.wearEquipment();
        if (!success && Rs2Inventory.isFull()) {
            logOnceToChat("Failed to equip gear - inventory full, eating food");
            Rs2Player.useFood();
            Rs2Inventory.waitForInventoryChanges(1200);
            success = gear.wearEquipment();
        }
        return success;
    }

    private boolean shouldRetreat(DemonicGorillaConfig config) {
        int currentHealth = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPrayerPotions = Rs2Inventory.items()
                .filter(Objects::nonNull)
                .map(item -> item.getName())
                .filter(Objects::nonNull)
                .noneMatch(name -> Rs2Potion.getPrayerPotionsVariants().contains(name));

        return (noFood && currentHealth <= config.healthThreshold())
                || (noPrayerPotions && currentPrayer < 10);
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        currentDefensivePrayer = null;
        currentOffensivePrayer = null;
    }

    private void evaluateAndConsumePotions(DemonicGorillaConfig config) {
        int threshold = config.boostedStatsThreshold();

        if (!isCombatPotionActive(threshold)) {
            consumePotion(Rs2Potion.getCombatPotionsVariants());
        }
        if (!Rs2Player.hasAttackActive(threshold)) {
            consumePotion(Rs2Potion.getAttackPotionsVariants());
        }
        if (!Rs2Player.hasStrengthActive(threshold)) {
            consumePotion(Rs2Potion.getStrengthPotionsVariants());
        }
        if (!isRangingPotionActive(threshold)) {
            consumePotion(Rs2Potion.getRangePotionsVariants());
        }
    }

    private boolean isCombatPotionActive(int threshold) {
        return Rs2Player.hasDivineCombatActive() || (Rs2Player.hasAttackActive(threshold) && Rs2Player.hasStrengthActive(threshold));
    }

    private boolean isRangingPotionActive(int threshold) {
        return Rs2Player.hasRangingPotionActive(threshold) || Rs2Player.hasDivineBastionActive() || Rs2Player.hasDivineRangedActive();
    }

    private void consumePotion(List<String> keyword) {
        var potion = Rs2Inventory.get(keyword.toArray(String[]::new));
        if (potion != null) {
            Rs2Inventory.interact(potion, "Drink");
        }
    }

    /** Called by the looter script so the overlay can show loot without the deprecated aggregate loot API. */
    public static void addLootValue(long value) {
        if (value > 0) {
            totalLootValue += value;
        }
    }

    void logOnceToChat(String message) {
        if (!message.equals(lastChatMessage)) {
            Microbot.log(message);
            lastChatMessage = message;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        BOT_STATUS = State.BANKING;
        isRunning = false;
        qolStopped = false;
        currentTarget = null;
        killCount = 0;
        currentTripKillCount = 0;
        totalLootValue = 0;
        lootAttempted = false;
        currentDefensivePrayer = null;
        currentOffensivePrayer = null;
        currentOverheadIcon = null;
        outOfCombatTime = null;
        lastGorillaLocation = null;
        gearSwitchGraceUntil = 0;
        failedCount = 0;
        failedAttacks = 0;
        bankingGear = null;
        rangeGear = null;
        magicGear = null;
        meleeGear = null;
        disableAllPrayers();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        logOnceToChat("Shutting down Demonic Gorilla script");
    }

    public enum State {BANKING, TRAVEL_TO_GORILLAS, FIGHTING}

    public enum ArmorEquiped {MELEE, RANGED, MAGIC}

    private enum BankingStep {BANK, LOAD_INVENTORY}
}
