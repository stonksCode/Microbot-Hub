package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.Actor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TormentedDemonScript extends Script {

    public static int killCount = 0;
    /** Duration of the most recent kill in milliseconds, 0 when none has been recorded yet. */
    public static long lastKillTimeMs = 0;
    private static long totalKillTimeMs = 0;
    private static int timedKills = 0;
    private static int tripKills = 0;
    private static long tripLootValue = 0;
    // A fight that lasts longer than this was almost certainly waiting on a respawn or a walk,
    // not a real kill, so it is kept out of the averages.
    private static final long MAX_TRACKED_KILL_TIME_MS = 15 * 60 * 1000L;
    private Rs2PrayerEnum currentOffensivePrayer = null;
    private HeadIcon currentOverheadIcon = null;
    private CombatStyle currentCombatStyle = null;
    public Rs2NpcModel currentTarget;
    private int currentTargetIndex = -1;
    private long lastMarkCastAt = 0;
    private long lastMarkAttemptAt = 0;
    private long lastThrallCastAttempt = 0;
    private long gearSwitchGraceUntil = 0;
    private long lastWeaponCheckAt = 0;
    private long lastSpecialAttackAttemptAt = 0;
    private boolean specialAttackEnabledByPlugin = false;
    private String lastSpecialAttackDebug = "";
    private boolean lootAttempted = false;
    private boolean warnedArceuus = false;
    private boolean inventoryFullStopIssued = false;
    private long lastDemonicOfferingAt = 0;
    private String lastChatMessage = "";
    public volatile long dodgeUntil = 0;
    /**
     * Set by the plugin while a firebomb blast is still active. Combat may keep attacking from
     * where it stands, but it must not start a walk toward the demon during this window because
     * the client's auto-walk does not know about the bomb tiles.
     */
    public volatile long bombWalkLockUntil = 0;
    private long fightStartTime = 0;
    private volatile long lastFirebombTime = 0;
    private volatile boolean punishGearArmed = false;
    private volatile long lastPlayerHitOnTargetAt = 0;
    private long punishSwappedAt = 0;
    private boolean inPunishPhase = false;
    private boolean inDefenselessPhase = false;
    private String lastGearBeforeSwap = null;
    private List<String> lastGearList = null;
    private boolean swappedForPunish = false;
    private boolean swappedForSpec = false;
    private boolean lastOffensiveGateEngaged = false;
    private long offensivePrayerKeepUntil = 0;
    private long lastOffensivePrayerAttemptAt = 0;
    /** Config of the currently running script, used by helpers that are called without it. */
    private TormentedDemonConfig activeConfig;

    private static final WorldPoint SAFE_LOCATION = new WorldPoint(3150, 3634, 0);
    private static final WorldPoint GUTHIXIAN_TEMPLE_LOCATION = new WorldPoint(4062, 4558, 0);
    private static final int FEROX_POOL_ID = 39651;
    // Time allowed for the emergency teleport to be interrupted by the demon before shutdown.
    private static final int EMERGENCY_TELEPORT_COMBAT_WAIT_MS = 8_000;
    // How many times one loot attempt may free a slot and run the loot engine again.
    private static final int LOOT_SPACE_PASSES = 3;
    // Demonic Offering has a 9 tick (5.4 s) cooldown shared by all offering spells.
    private static final long DEMONIC_OFFERING_COOLDOWN_MS = 5_400L;
    private static final Set<String> DEMONIC_ASHES_NAMES = new HashSet<>(Arrays.asList(
            "fiendish ashes", "vile ashes", "malicious ashes", "abyssal ashes", "infernal ashes"));
    // Order used when the configured emergency teleport is unavailable: bank-adjacent teleports
    // first, then the temple scroll, which at least leaves the cave.
    private static final List<TormentedDemonConfig.EmergencyTeleportMethod> EMERGENCY_TELEPORT_FALLBACKS =
            Arrays.asList(
                    TormentedDemonConfig.EmergencyTeleportMethod.RING_OF_DUELING,
                    TormentedDemonConfig.EmergencyTeleportMethod.AMULET_OF_GLORY,
                    TormentedDemonConfig.EmergencyTeleportMethod.RING_OF_WEALTH,
                    TormentedDemonConfig.EmergencyTeleportMethod.ROYAL_SEED_POD,
                    TormentedDemonConfig.EmergencyTeleportMethod.VARROCK_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.LUMBRIDGE_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.FALADOR_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.SKILLS_NECKLACE,
                    TormentedDemonConfig.EmergencyTeleportMethod.COMBAT_BRACELET,
                    TormentedDemonConfig.EmergencyTeleportMethod.CAMELOT_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.WATCHTOWER_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.ARDOUGNE_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.HOUSE_TAB,
                    TormentedDemonConfig.EmergencyTeleportMethod.GUTHIXIAN_TEMPLE);
    private static final long MARK_REFRESH_MARGIN_MS = 10_000L;
    private static final long MARK_RETRY_MS = 5_000L;
    private static final long THRALL_RETRY_MS = 3_000L;
    private static final long WEAPON_CHECK_RETRY_MS = 2_000L;
    private static final long SPECIAL_ATTACK_RETRY_MS = 600L;
    // Keep the offensive prayer up across short range/LOS dips (dodging, walking back) so it does
    // not flicker off and on every tick, but still drop it during looting and travel.
    private static final long OFFENSIVE_PRAYER_KEEP_MS = 4_000L;
    // A failed activation must not be retried every tick; each attempt can take over a second.
    private static final long OFFENSIVE_PRAYER_RETRY_MS = 1_500L;
    // equipGear already blocks until the gear is verified worn, so the grace only has to cover
    // the client/server round trip for the weapon change before the style/spec checks run again.
    private static final long GEAR_SWITCH_GRACE_MS = 350L;
    private static final long GEAR_RESTORE_GRACE_MS = 250L;
    private static final long DEFENSELESS_DELAY_MS = 18_000L;
    private static final long PUNISH_MAX_HOLD_MS = 8_000L;
    // The punish window only opens once the bombs have landed and the dodge is over. The fire
    // shield stays down until the next attack, so there is no need to swap while still dodging.
    private static final long PUNISH_PHASE_DELAY_MS = 1_800L;
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS = 9;
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_RUNES = 1;

    /**
     * Every potion name the client's drink helper accepts. Names are normalized (lower case, dose
     * suffix removed) so "Prayer potion(1)" and "Prayer potion(4)" both match.
     */
    private static final Set<String> PRAYER_SUPPLY_NAMES;

    static {
        Set<String> names = new HashSet<>();
        for (String name : Rs2Potion.getPrayerPotionsVariants()) {
            names.add(normalizePotionName(name));
        }
        names.add(normalizePotionName(Rs2Potion.getPrayerRegenerationPotion()));
        names.add(normalizePotionName("Blighted super restore"));
        PRAYER_SUPPLY_NAMES = Collections.unmodifiableSet(names);
    }

    private enum State {BANKING, RETREAT, TRAVEL_TO_TORMENTED, FIGHTING}

    public static State BOT_STATUS = State.BANKING;

    private enum TravelStep {LOCATION_ONE, CLIMB_FIRST_STAIRS, CLIMB_SECOND_STAIRS, CLIMB_THROUGH, LOCATION_THREE}

    private TravelStep travelStep = TravelStep.LOCATION_ONE;

    private enum BankingStep {DRINK, BANK, LOAD_INVENTORY}

    private BankingStep bankingStep = BankingStep.DRINK;

    private enum CombatStyle {MELEE, RANGE, MAGIC}

    public boolean run(TormentedDemonConfig config) {
        activeConfig = config;
        if (config.mode() == TormentedDemonConfig.MODE.FULL_AUTO) {
            BOT_STATUS = State.BANKING;
        } else if (config.mode() == TormentedDemonConfig.MODE.COMBAT_ONLY) {
            BOT_STATUS = State.FIGHTING;
        }

        bankingStep = BankingStep.DRINK;
        travelStep = TravelStep.LOCATION_ONE;
        dodgeUntil = 0;
        bombWalkLockUntil = 0;
        lastFirebombTime = 0;
        punishGearArmed = false;
        lastPlayerHitOnTargetAt = 0;
        punishSwappedAt = 0;
        inPunishPhase = false;
        inDefenselessPhase = false;
        swappedForPunish = false;
        swappedForSpec = false;
        lastWeaponCheckAt = 0;
        lastSpecialAttackAttemptAt = 0;
        specialAttackEnabledByPlugin = false;
        lastSpecialAttackDebug = "";
        Microbot.enableAutoRunOn = false;
        resetTripStats();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (System.currentTimeMillis() < dodgeUntil) {
                    // The dodge loop owns movement while a firebomb is active. Keep phase handling
                    // (prayer-appropriate punish gear / spec gear) running so the swap lands inside
                    // the shield-down window instead of being skipped and reverted.
                    if (BOT_STATUS == State.FIGHTING) {
                        handlePhases(config);
                    }
                    return;
                }

                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case RETREAT:
                        handleRetreat();
                        break;
                    case TRAVEL_TO_TORMENTED:
                        handleTravel(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }
            } catch (Exception ex) {
                logOnceToChat("Error in main loop: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleTravel(TormentedDemonConfig config) {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        WorldPoint targetLocationOne = GUTHIXIAN_TEMPLE_LOCATION;
        WorldPoint targetFinalLocation = new WorldPoint(4073, 4432, 0);

        switch (travelStep) {
            case LOCATION_ONE:
                if (isWithin(targetLocationOne, 20)) {
                    travelStep = TravelStep.CLIMB_FIRST_STAIRS;
                    break;
                }
                Microbot.status = "Teleporting to Guthixian temple...";
                if (Rs2Inventory.hasItem("Guthixian temple teleport") && !teleportToGuthixianTemple()) {
                    logOnceToChat("Guthixian temple teleport did not complete; retrying.");
                }
                if (isWithin(targetLocationOne, 20)) {
                    travelStep = TravelStep.CLIMB_FIRST_STAIRS;
                }
                break;

            case CLIMB_FIRST_STAIRS:
                Microbot.status = "Climbing first stairs...";
                if (Microbot.getRs2TileObjectCache().query().interact(53623, "Climb-up")) {
                    Rs2Player.waitForAnimation();
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    travelStep = TravelStep.CLIMB_SECOND_STAIRS;
                }
                break;

            case CLIMB_SECOND_STAIRS:
                Microbot.status = "Climbing second stairs...";
                if (Microbot.getRs2TileObjectCache().query().interact(53624, "Climb-up")) {
                    Rs2Player.waitForAnimation();
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    travelStep = TravelStep.CLIMB_THROUGH;
                }
                break;

            case CLIMB_THROUGH:
                Microbot.status = "Climbing through the path...";
                if (Microbot.getRs2TileObjectCache().query().interact(54082, "Climb-through")) {
                    Rs2Player.waitForAnimation();
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    travelStep = TravelStep.LOCATION_THREE;
                }
                break;

            case LOCATION_THREE:
                Microbot.status = "Approaching Tormented Demon location...";
                if (Rs2Walker.walkTo(targetFinalLocation, 2)) {
                    travelStep = TravelStep.LOCATION_ONE;
                    BOT_STATUS = State.FIGHTING;
                }
                break;
        }
    }

    private void handleBanking(TormentedDemonConfig config) {
        switch (bankingStep) {
            case DRINK: {
                Microbot.status = "Drinking at Ferox Enclave pool...";
                int currentHealth = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
                int maxHealth = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
                int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
                int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);

                if (currentHealth >= maxHealth && currentPrayer >= maxPrayer) {
                    bankingStep = BankingStep.BANK;
                    break;
                }

                if (!moveToFeroxPool()) {
                    Microbot.status = "Teleporting to Ferox Enclave...";
                    if (!teleportToFeroxEnclave()) {
                        logOnceToChat("No usable Ring of Dueling found; stopping.");
                        Microbot.stopPlugin(TormentedDemonPlugin.class);
                        return;
                    }
                    sleepUntil(() -> isWithin(SAFE_LOCATION, 15), 12000);
                    return;
                }
                if (Microbot.getRs2TileObjectCache().query().interact(FEROX_POOL_ID, "Drink")) {
                    Rs2Player.waitForAnimation();
                    if (!sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) >= maxHealth
                            && Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= maxPrayer, 6000)) {
                        logOnceToChat("Ferox Enclave pool did not fully restore stats; continuing.");
                    }
                    bankingStep = BankingStep.BANK;
                } else {
                    logOnceToChat("Ferox Enclave pool not reachable; retrying.");
                }
                break;
            }

            case BANK: {
                Microbot.status = "Opening bank...";
                if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, 5000)) {
                    logOnceToChat("Bank did not open; retrying.");
                    break;
                }
                Rs2Bank.depositAll();
                if (config.stopWhenOutOfSupplies()) {
                    if (!bankHasHealingSupply()) {
                        stopForSupplies(config, "No food left in the bank", false);
                        return;
                    }
                    if (usesPrayer(config) && !bankHasPrayerSupply()) {
                        stopForSupplies(config, "No prayer potions left in the bank", false);
                        return;
                    }
                }
                bankingStep = BankingStep.LOAD_INVENTORY;
                break;
            }

            case LOAD_INVENTORY: {
                Microbot.status = "Loading inventory and equipment setup...";
                Rs2InventorySetup inventorySetup = new Rs2InventorySetup("tormented", mainScheduledFuture);
                boolean equipmentLoaded = inventorySetup.loadEquipment();
                boolean inventoryLoaded = inventorySetup.loadInventory();

                if (equipmentLoaded && inventoryLoaded) {
                    // The setup loader tolerates a partial restock when the bank is short, so verify
                    // the loaded inventory actually contains something the bot can heal/restore with.
                    if (config.stopWhenOutOfSupplies()) {
                        if (!hasHealingSupply()) {
                            stopForSupplies(config, "No food left after restocking", false);
                            return;
                        }
                        if (usesPrayer(config) && !hasPrayerSupply()) {
                            stopForSupplies(config, "No prayer potions left after restocking", false);
                            return;
                        }
                    }
                    Rs2Bank.closeBank();
                    bankingStep = BankingStep.DRINK;
                    resetTripStats();
                    BOT_STATUS = State.TRAVEL_TO_TORMENTED;
                } else {
                    String reason;
                    if (!hasHealingSupply()) {
                        reason = "No food left to restock with";
                    } else if (usesPrayer(config) && !hasPrayerSupply()) {
                        reason = "No prayer potions left to restock with";
                    } else {
                        reason = "Failed to load inventory/equipment setup 'tormented'";
                    }
                    stopForSupplies(config, reason, false);
                }
                break;
            }
        }
    }

    private boolean moveToFeroxPool() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) return false;
        var pool = Microbot.getRs2TileObjectCache().query().withId(FEROX_POOL_ID).nearest();
        if (pool == null) return false;
        WorldPoint poolLocation = pool.getWorldLocation();
        if (poolLocation != null && playerLocation.distanceTo(poolLocation) > 2) {
            Rs2Walker.walkTo(poolLocation, 2);
        }
        return true;
    }

    private void handleRetreat() {
        disableAllPrayers();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && playerLocation.distanceTo(SAFE_LOCATION) <= 15) {
            BOT_STATUS = State.BANKING;
            bankingStep = BankingStep.DRINK;
            return;
        }

        Microbot.status = "Retreating to Ferox Enclave...";
        if (!teleportToFeroxEnclave()) {
            logOnceToChat("No usable Ring of Dueling found; stopping.");
            Microbot.stopPlugin(TormentedDemonPlugin.class);
            return;
        }

        if (!sleepUntil(() -> {
            WorldPoint location = Rs2Player.getWorldLocation();
            return location != null && location.distanceTo(SAFE_LOCATION) <= 15;
        }, 12000)) {
            logOnceToChat("Ferox Enclave teleport did not complete; retrying.");
        }
    }

    private boolean teleportToFeroxEnclave() {
        int[] duelingRingIds = {
                ItemID.RING_OF_DUELING_1,
                ItemID.RING_OF_DUELING_2,
                ItemID.RING_OF_DUELING_3,
                ItemID.RING_OF_DUELING_4,
                ItemID.RING_OF_DUELING_5,
                ItemID.RING_OF_DUELING_6,
                ItemID.RING_OF_DUELING_7,
                ItemID.RING_OF_DUELING_8
        };

        for (int ringId : duelingRingIds) {
            if (Rs2Equipment.isWearing(ringId)) {
                if (Rs2Equipment.interact(JewelleryLocationEnum.FEROX_ENCLAVE.getTooltip(), JewelleryLocationEnum.FEROX_ENCLAVE.getDestination())) {
                    logOnceToChat("Teleporting to Ferox Enclave using Ring of Dueling.");
                    return true;
                }
                return false;
            }
        }

        for (int ringId : duelingRingIds) {
            if (Rs2Inventory.hasItem(ringId)) {
                if (!Rs2Inventory.interact(ringId, "Wear")) continue;
                if (!sleepUntil(() -> Rs2Equipment.isWearing(ringId), 3000)) continue;
                if (Rs2Equipment.interact(JewelleryLocationEnum.FEROX_ENCLAVE.getTooltip(), JewelleryLocationEnum.FEROX_ENCLAVE.getDestination())) {
                    logOnceToChat("Teleporting to Ferox Enclave using Ring of Dueling.");
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    /**
     * Clicks the Guthixian temple teleport and waits for the destination. Used both for travelling
     * to the demons and as an emergency exit from the cave.
     */
    private boolean teleportToGuthixianTemple() {
        if (!Rs2Inventory.interact("Guthixian temple teleport", "Teleport")) {
            return false;
        }
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        return sleepUntil(() -> isWithin(GUTHIXIAN_TEMPLE_LOCATION, 20), 12000);
    }

    public void onFirebombTracked() {
        long now = System.currentTimeMillis();
        // One special launches two bombs; only arm the punish phase once for that special.
        if (now - lastFirebombTime > 5_000L) {
            lastFirebombTime = now;
            punishGearArmed = true;
            lastPlayerHitOnTargetAt = 0;
        }
    }

    /**
     * Called by the plugin for every hitsplat in the game. Only the local player's hits on the
     * current target are recorded: that is the "first hit" that consumes the shield-down punish
     * window. Player animation time is not used because walking/running also animates.
     */
    public void onTargetHitsplat(Actor actor, Hitsplat hitsplat) {
        if (actor == null || hitsplat == null || !hitsplat.isMine()) {
            return;
        }
        Rs2NpcModel target = currentTarget;
        if (target == null || actor != target.getNpc()) {
            return;
        }
        lastPlayerHitOnTargetAt = System.currentTimeMillis();
    }

    public void clearFirebombTracking() {
        lastFirebombTime = 0;
        punishGearArmed = false;
        lastPlayerHitOnTargetAt = 0;
        inPunishPhase = false;
        inDefenselessPhase = false;
    }

    private void handleFighting(TormentedDemonConfig config) {
        if (currentTarget == null || isTargetDead(currentTarget)) {
            releasePluginSpecialAttack(config);
            // Keep protection reactive while a demon is still attacking during the loot, but stop
            // the offensive prayer immediately; the plugin clears stale protection prayers when
            // nothing is targeting the player.
            disableOffensivePrayer();
            restoreGearAfterPhases(config);

            if (!lootAttempted) {
                if (currentTarget != null && isTargetDead(currentTarget)) {
                    killCount++;
                    recordKillTime(fightStartTime);
                    if (config.lootDelay() > 0) {
                        sleep(config.lootDelay());
                    }
                }
                attemptLooting(config);
                lootAttempted = true;
            }

            // Checked after looting and before targeting the next demon: a finished fight is the
            // natural place to leave with the last hit's loot instead of starting another kill.
            if (handleSupplySafety(config)) {
                return;
            }

            currentTarget = findNewTarget();
            if (currentTarget == null) {
                currentTargetIndex = -1;
                logOnceToChat("No Tormented Demon found; waiting...");
                fightStartTime = 0;
                lastFirebombTime = 0;
                punishGearArmed = false;
                lastPlayerHitOnTargetAt = 0;
                punishSwappedAt = 0;
                inPunishPhase = false;
                inDefenselessPhase = false;
                swappedForPunish = false;
                swappedForSpec = false;
                restoreGearAfterPhases(config);
                return;
            }

            currentTargetIndex = getTargetIndex(currentTarget);
            currentOverheadIcon = null;
            currentCombatStyle = null;
            lastWeaponCheckAt = 0;
            lootAttempted = false;
            fightStartTime = System.currentTimeMillis();
            lastFirebombTime = 0;
            punishGearArmed = false;
            lastPlayerHitOnTargetAt = 0;
            punishSwappedAt = 0;
            inPunishPhase = false;
            inDefenselessPhase = false;
        }

        if (handleSupplySafety(config)) {
            return;
        }

        HeadIcon overheadIcon = getHeadIcon(currentTarget);
        if (overheadIcon != null && overheadIcon != currentOverheadIcon) {
            currentOverheadIcon = overheadIcon;
            if (!swappedForPunish && !swappedForSpec) {
                CombatStyle newStyle = config.autoGearSwitch() ? switchGear(config, overheadIcon) : detectStyle(config);
                updateCombatStyle(newStyle);
            }
        } else if (System.currentTimeMillis() >= gearSwitchGraceUntil && !swappedForPunish && !swappedForSpec) {
            updateCombatStyle(detectStyle(config));
            HeadIcon activeIcon = overheadIcon != null ? overheadIcon : currentOverheadIcon;
            updateCombatStyle(ensureCorrectWeapon(config, activeIcon));
        }

        evaluateAndConsumePotions(config);

        handlePhases(config);

        Rs2Player.eatAt(config.minEatPercent());
        if (config.enableDefensivePrayer() || config.enableOffensivePrayer()) {
            Rs2Player.drinkPrayerPotionAt(prayerThreshold(config));
        }

        // Evaluated before the magic utility and the attack click: casting Mark of Darkness or
        // waiting on an attack click used to return early and leave the offensive prayer off for
        // that whole tick, which showed up as "never turns on".
        if (config.enableOffensivePrayer()) {
            // Only boost while the target can actually be hit. Walking to the next demon used to
            // keep the prayer burning for the whole trip, which adds up over a long session.
            // The keep-alive window stops it flickering off during the firebomb dodge or a single
            // out-of-range tick and then being switched straight back on.
            long now = System.currentTimeMillis();
            boolean engaged = currentCombatStyle != null && isEngagedWithTarget();
            if (engaged) {
                offensivePrayerKeepUntil = now + OFFENSIVE_PRAYER_KEEP_MS;
            }
            boolean keep = engaged || now < offensivePrayerKeepUntil || now < dodgeUntil;
            if (config.debugPrayer() && keep != lastOffensiveGateEngaged) {
                lastOffensiveGateEngaged = keep;
                Microbot.log("[TD prayer] offensive gate " + (keep ? "open" : "closed")
                        + ": distance=" + distanceToTarget()
                        + " range=" + attackRange()
                        + " style=" + currentCombatStyle
                        + " engaged=" + engaged);
            }
            if (keep) {
                activateOffensivePrayer(config, currentCombatStyle);
            } else {
                disableOffensivePrayer();
            }
        } else {
            disableOffensivePrayer();
        }

        if (currentCombatStyle == CombatStyle.MAGIC && handleMagicUtility(config)) {
            return;
        }

        if (currentTarget == null || isTargetDead(currentTarget)) {
            return;
        }

        if (config.useSpecialAttack() || config.useSpecialAttackGear()) {
            // "Use Special Attack Gear" implies using the special attack itself; otherwise the
            // gear would be swapped in and the orb would never be enabled.
            handleSpecialAttack(config);
        } else {
            releasePluginSpecialAttack(config);
        }

        boolean walkingToTarget = getInteractingTargetIndex() != currentTargetIndex;
        boolean waitingForFire = walkingToTarget && mustNotWalkToTarget();
        if (walkingToTarget && !waitingForFire) {
            if (!currentTarget.click("attack")) {
                logOnceToChat("Attack failed for target.");
                currentTarget = null;
                currentTargetIndex = -1;
                currentOverheadIcon = null;
                currentCombatStyle = null;
                lastWeaponCheckAt = 0;
                disableOffensivePrayer();
                restoreGearAfterPhases(config);
                return;
            }

            Rs2Player.waitForAnimation();
            sleepUntil(() -> getInteractingTargetIndex() == currentTargetIndex, 3000);
        } else if (waitingForFire && config.debugDodge()) {
            // Not in range yet: clicking attack would make the client auto-walk, and that walk
            // does not avoid the bomb blasts. Hold the click until the fire burns out.
            logOnceToChat("Holding attack until the firebombs burn out before walking back.");
        }
    }

    /**
     * Attack range for the equipped weapon. {@link Rs2Combat#getAttackRange(boolean, boolean)}
     * resolves the weapon-style enum, and that lookup intermittently fails right after an
     * equipment change ("weaponStyleStructs is null"). The exception used to abort the whole
     * combat tick, which meant the offensive prayer gate was never evaluated while a magic
     * weapon was equipped. Fall back to the range typical for the detected style instead.
     */
    private int attackRange() {
        try {
            return Math.max(1, Rs2Combat.getAttackRange(true, false));
        } catch (RuntimeException e) {
            return currentCombatStyle == CombatStyle.MAGIC || currentCombatStyle == CombatStyle.RANGE ? 10 : 1;
        }
    }

    /**
     * True when the current target is close enough to be attacked right now, so an offensive
     * prayer would actually apply to a hit. Merely interacting with a demon does not count: the
     * client keeps that interaction while auto-walking towards it.
     *
     * <p>Distance and line of sight are measured against the NPC's whole area, not its base
     * tile. Tormented Demons occupy several tiles, so a base-tile check reports "out of range"
     * while the player is already standing next to the body and attacking.</p>
     */
    private boolean isEngagedWithTarget() {
        if (currentTarget == null || isTargetDead(currentTarget)) {
            return false;
        }
        int attackRange = attackRange();
        if (distanceToTarget() > attackRange) {
            return false;
        }
        return attackRange <= 1 || targetHasLineOfSight();
    }

    /**
     * Chebyshev distance from the player to the closest tile of the target's body, or
     * {@link Integer#MAX_VALUE} when either location is unavailable.
     */
    private int distanceToTarget() {
        if (currentTarget == null) {
            return Integer.MAX_VALUE;
        }
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        NPC npc = currentTarget.getNpc();
        if (playerLocation == null || npc == null) {
            return Integer.MAX_VALUE;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldArea targetArea = npc.getWorldArea();
            if (targetArea == null || targetArea.getPlane() != playerLocation.getPlane()) {
                return Integer.MAX_VALUE;
            }
            return targetArea.distanceTo(playerLocation);
        }).orElse(Integer.MAX_VALUE);
    }

    private boolean targetHasLineOfSight() {
        if (currentTarget == null) {
            return false;
        }
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        NPC npc = currentTarget.getNpc();
        if (playerLocation == null || npc == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldArea targetArea = npc.getWorldArea();
            WorldView worldView = Microbot.getClient().getTopLevelWorldView();
            if (targetArea == null || worldView == null
                    || targetArea.getPlane() != playerLocation.getPlane()) {
                return false;
            }
            return targetArea.hasLineOfSightTo(worldView, playerLocation);
        }).orElse(false);
    }

    /**
     * True when a tracked firebomb is still burning and the player is not already in attack
     * range of the target. In that situation clicking the demon would start the client's
     * auto-walk, which knows nothing about bomb tiles and can path straight through the blast.
     * Waiting out the fire costs at most a couple of seconds and the punish window is measured
     * from the bomb impact, so the shield-down hit is still available afterwards.
     */
    private boolean mustNotWalkToTarget() {
        if (System.currentTimeMillis() >= bombWalkLockUntil || currentTarget == null) {
            return false;
        }
        int attackRange = attackRange();
        if (distanceToTarget() > attackRange) {
            return true;
        }
        // In range but without line of sight the client would still walk to get a view.
        return attackRange > 1 && !targetHasLineOfSight();
    }

    private void updateCombatStyle(CombatStyle newStyle) {
        if (newStyle == null || newStyle == currentCombatStyle) {
            return;
        }
        currentCombatStyle = newStyle;
        logOnceToChat("Combat style detected: " + newStyle + ".");
        if (currentOffensivePrayer != null) {
            disableOffensivePrayer();
            // Let the new style's prayer come up on this tick instead of waiting out the retry
            // cooldown from the previous activation.
            lastOffensivePrayerAttemptAt = 0;
        }
    }

    private boolean handleMagicUtility(TormentedDemonConfig config) {
        if (!Rs2Magic.isSpellbook(Rs2Spellbook.ARCEUUS)) {
            if ((config.useMarkOfDarkness() || config.useThralls()) && !warnedArceuus) {
                logOnceToChat("Arceuus spellbook is required for Mark of Darkness and thralls.");
                warnedArceuus = true;
            }
            return false;
        }

        warnedArceuus = false;

        if (config.useThralls()) {
            trySummonThrall();
        }

        if (config.useMarkOfDarkness() && shouldCastMark()) {
            return castMarkOfDarkness();
        }

        return false;
    }

    private boolean shouldCastMark() {
        long now = System.currentTimeMillis();
        if (now - lastMarkAttemptAt < MARK_RETRY_MS) {
            return false;
        }
        long refreshMs = Math.max(30_000L, markOfDarknessDurationMs() - MARK_REFRESH_MARGIN_MS);
        return now - lastMarkCastAt >= refreshMs;
    }

    private long markOfDarknessDurationMs() {
        int magicLevel = Math.max(1, Rs2Player.getBoostedSkillLevel(Skill.MAGIC));
        long ticks = magicLevel * 3L;
        if (Rs2Equipment.isWearing("Purging staff")) {
            ticks *= 5L;
        }
        return ticks * 600L;
    }

    private boolean castMarkOfDarkness() {
        lastMarkAttemptAt = System.currentTimeMillis();
        if (!Rs2Magic.canCast(Rs2Spells.MARK_OF_DARKNESS)) {
            return false;
        }

        boolean cast = Rs2Magic.cast(Rs2Spells.MARK_OF_DARKNESS);
        if (cast) {
            lastMarkCastAt = System.currentTimeMillis();
            logOnceToChat("Cast Mark of Darkness.");
        }
        return cast;
    }

    private void trySummonThrall() {
        long now = System.currentTimeMillis();
        if (now - lastThrallCastAttempt < THRALL_RETRY_MS) return;
        if (Rs2Thrall.isActive()) return;

        lastThrallCastAttempt = now;
        Rs2Thrall best = null;
        for (Rs2Thrall thrall : Rs2Thrall.values()) {
            if (!Rs2Thrall.canCast(thrall)) continue;
            if (best == null || thrall.getRequiredLevel() > best.getRequiredLevel()) {
                best = thrall;
            }
        }

        if (best != null && Rs2Thrall.cast(best)) {
            logOnceToChat("Summoned " + best.getName() + ".");
        }
    }

    private HeadIcon getHeadIcon(Rs2NpcModel target) {
        if (target == null) return null;
        NPC npc = target.getNpc();
        if (npc == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            short[] spriteIds = npc.getOverheadSpriteIds();
            if (spriteIds == null) return null;
            for (int spriteId : spriteIds) {
                if (spriteId >= 0 && spriteId < HeadIcon.values().length) {
                    return HeadIcon.values()[spriteId];
                }
            }
            return null;
        }).orElse(null);
    }

    private boolean isTargetDead(Rs2NpcModel target) {
        if (target == null) return true;
        return Microbot.getClientThread().runOnClientThreadOptional(target::isDead).orElse(true);
    }

    private int getTargetIndex(Rs2NpcModel target) {
        if (target == null) return -1;
        return Microbot.getClientThread().runOnClientThreadOptional(target::getIndex).orElse(-1);
    }

    private int getInteractingTargetIndex() {
        var actor = Rs2Player.getInteracting();
        if (actor instanceof NPC) {
            return ((NPC) actor).getIndex();
        }
        return -1;
    }

    private boolean isWithin(WorldPoint target, int distance) {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && location.distanceTo(target) <= distance;
    }

    private Rs2NpcModel findNewTarget() {
        return Microbot.getRs2NpcCache().query()
                .withName("Tormented Demon")
                .where(npc -> !npc.isDead())
                .where(npc -> npc.getInteracting() == null || npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .nearestOnClientThread();
    }

    private CombatStyle switchGear(TormentedDemonConfig config, HeadIcon combatNpcHeadIcon) {
        CombatStyle preferred = detectStyle(config);
        List<CombatStyle> candidates = counterStyles(config, combatNpcHeadIcon);

        if (candidates.isEmpty()) {
            logOnceToChat("No configured gear counters the demon's " + combatNpcHeadIcon + " protection; keeping current gear.");
            return preferred;
        }

        CombatStyle chosen = chooseCounterStyle(config, candidates, preferred);
        List<String> gear = gearForStyle(config, chosen);
        boolean gearChanged = false;
        if (!isGearEquipped(gear)) {
            logOnceToChat("Switching gear to " + chosen + ".");
            equipGear(gear);
            gearChanged = true;
            gearSwitchGraceUntil = System.currentTimeMillis() + GEAR_SWITCH_GRACE_MS;
        }

        if (!gearChanged) {
            CombatStyle actual = detectAttackStyle(config);
            if (actual != null && actual != chosen) {
                if (gear.equals(gearForStyle(config, actual)) && switchWeaponAttackStyle(config, chosen)) {
                    logOnceToChat("Switched Blue Moon spear attack style to " + chosen + ".");
                    return chosen;
                }
                if (gear.equals(gearForStyle(config, actual))) {
                    logOnceToChat("Gear lists for " + chosen + " and " + actual
                            + " are identical; switch the weapon attack style manually.");
                }
                return actual;
            }
        }

        return chosen;
    }

    private CombatStyle ensureCorrectWeapon(TormentedDemonConfig config, HeadIcon combatNpcHeadIcon) {
        if (!config.verifyWeapon() || !config.autoGearSwitch() || combatNpcHeadIcon == null) {
            return null;
        }
        // Punish/spec gear is deliberately outside the normal prayer counter sets. Do not let the
        // weapon verifier revert it while a phase swap is active.
        if (swappedForPunish || swappedForSpec) {
            return null;
        }
        if (System.currentTimeMillis() < gearSwitchGraceUntil) {
            return null;
        }

        List<CombatStyle> candidates = counterStyles(config, combatNpcHeadIcon);
        if (candidates.isEmpty()) {
            return null;
        }

        CombatStyle actual = detectAttackStyle(config);
        if (actual == null) {
            // The weapon style cannot be read reliably right now (equipment change still in
            // flight, or an unrecognised weapon). Re-equipping here produced the observed loop of
            // "Weapon check failed ... (unknown) does not counter MAGIC protection" followed by
            // repeated multi-slot equips, so wait for the next check instead.
            return null;
        }

        String weaponName = getEquippedWeaponName();
        boolean styleCorrect = candidates.contains(actual);
        boolean gearCorrect = styleCorrect && isGearEquipped(gearForStyle(config, actual));

        if (styleCorrect && gearCorrect) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastWeaponCheckAt < WEAPON_CHECK_RETRY_MS) {
            return null;
        }
        lastWeaponCheckAt = now;

        String equipped = weaponName != null ? weaponName : "unknown weapon";
        logOnceToChat("Weapon check failed: " + equipped + " (" + actual + ") does not counter "
                + combatNpcHeadIcon + " protection; correcting gear.");
        return switchGear(config, combatNpcHeadIcon);
    }

    private List<CombatStyle> counterStyles(TormentedDemonConfig config, HeadIcon combatNpcHeadIcon) {
        List<CombatStyle> candidates = new ArrayList<>();

        switch (combatNpcHeadIcon) {
            case RANGED:
                addIfUsable(config, candidates, CombatStyle.MELEE);
                addIfUsable(config, candidates, CombatStyle.MAGIC);
                break;
            case MAGIC:
                addIfUsable(config, candidates, CombatStyle.RANGE);
                addIfUsable(config, candidates, CombatStyle.MELEE);
                break;
            case MELEE:
                addIfUsable(config, candidates, CombatStyle.RANGE);
                addIfUsable(config, candidates, CombatStyle.MAGIC);
                break;
            default:
                break;
        }

        return candidates;
    }

    private CombatStyle chooseCounterStyle(TormentedDemonConfig config, List<CombatStyle> candidates, CombatStyle preferred) {
        if (preferred != null && candidates.contains(preferred)) {
            return preferred;
        }
        for (CombatStyle candidate : candidates) {
            if (isGearEquipped(gearForStyle(config, candidate))) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private boolean switchWeaponAttackStyle(TormentedDemonConfig config, CombatStyle style) {
        if (!Rs2Equipment.isWearing("Blue Moon spear")) {
            return false;
        }

        WidgetInfo styleWidget;
        switch (style) {
            case MAGIC:
                styleWidget = WidgetInfo.COMBAT_STYLE_FOUR;
                break;
            case MELEE:
                styleWidget = WidgetInfo.COMBAT_STYLE_ONE;
                break;
            default:
                return false;
        }

        if (detectAttackStyle(config) == style) {
            return true;
        }

        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.isCurrentTab(InterfaceTab.COMBAT), 1000);
        try {
            if (!Rs2Combat.setAttackStyle(styleWidget)) {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
        sleepUntil(() -> detectAttackStyle(config) == style, 1500);
        return detectAttackStyle(config) == style;
    }

    private void handleSpecialAttack(TormentedDemonConfig config) {
        long now = System.currentTimeMillis();
        if (now < gearSwitchGraceUntil) {
            releasePluginSpecialAttack(config);
            debugSpecialAttack(config, "holding off during gear switch grace");
            return;
        }

        String weaponName = getEquippedWeaponName();
        int energy = Rs2Combat.getSpecEnergy();
        int thresholdEnergy = specThresholdEnergy(config);
        int requiredEnergy = getSpecialAttackEnergyRequired(weaponName);

        if (config.useSpecialAttackGear()) {
            List<String> specGear = getSpecGearForOverhead(config, getHeadIcon(currentTarget));
            boolean specAllowed = inDefenselessPhase
                    && !specGear.isEmpty()
                    && swappedForSpec
                    && energy >= thresholdEnergy;
            if (!specAllowed) {
                // Special attack gear is only used during the defenseless phase, and only against
                // overhead prayers that have a spec set configured. This stops a spec weapon (for
                // example the Blue Moon spear without its full set) from being used incidentally.
                if (swappedForSpec) {
                    restoreGearAfterPhases(config);
                }
                releasePluginSpecialAttack(config);
                debugSpecialAttack(config, "idle defenseless=" + inDefenselessPhase
                        + " gear=" + (specGear.isEmpty() ? "none" : specGear.size() + " items")
                        + " swapped=" + swappedForSpec
                        + " energy=" + energy + " threshold=" + thresholdEnergy);
                return;
            }
        }

        if (requiredEnergy < 0) {
            // Newer weapons are missing from SpecialAttackWeaponEnum. The user explicitly put them
            // in a special attack gear box, so do not give up: use the configured threshold only.
            requiredEnergy = 0;
            debugSpecialAttack(config, "unlisted weapon=" + weaponName
                    + "; relying on threshold only energy=" + energy);
        }

        if (energy < requiredEnergy) {
            releasePluginSpecialAttack(config);
            debugSpecialAttack(config, "waiting energy=" + energy + " required=" + requiredEnergy
                    + " weapon=" + weaponName);
            return;
        }

        if (isSpecialAttackEnabled()) {
            debugSpecialAttack(config, "active energy=" + energy + " required=" + requiredEnergy
                    + " weapon=" + weaponName);
            return;
        }

        if (now - lastSpecialAttackAttemptAt < SPECIAL_ATTACK_RETRY_MS) {
            return;
        }
        lastSpecialAttackAttemptAt = now;

        if (enableSpecialAttack()) {
            specialAttackEnabledByPlugin = true;
            logOnceToChat("Enabled special attack for " + weaponName + ".");
            debugSpecialAttack(config, "enabled energy=" + energy + " required=" + requiredEnergy
                    + " weapon=" + weaponName);
        } else {
            debugSpecialAttack(config, "enable failed state=" + isSpecialAttackEnabled()
                    + " energy=" + Rs2Combat.getSpecEnergy() + " required=" + requiredEnergy
                    + " weapon=" + weaponName + " orbHidden="
                    + Rs2Widget.isHidden(WidgetInfo.MINIMAP_SPEC_ORB.getId() + 2));
        }
    }

    /**
     * Special attack energy is stored on a 0-1000 scale (1000 = the full bar), while the config
     * option is a percentage. Convert before comparing so the configured threshold is honoured.
     */
    private int specThresholdEnergy(TormentedDemonConfig config) {
        int percent = Math.max(0, Math.min(100, config.specialAttackThreshold()));
        return percent * 10;
    }

    private boolean enableSpecialAttack() {
        return setSpecialAttackState(true);
    }

    private void releasePluginSpecialAttack(TormentedDemonConfig config) {
        if (!specialAttackEnabledByPlugin) {
            return;
        }
        boolean disabled = disableSpecialAttack();
        if (disabled) {
            specialAttackEnabledByPlugin = false;
        }
        debugSpecialAttack(config, disabled
                ? "released plugin-owned special attack"
                : "release failed state=" + isSpecialAttackEnabled());
    }

    private boolean disableSpecialAttack() {
        return setSpecialAttackState(false);
    }

    private boolean setSpecialAttackState(boolean desired) {
        if (isSpecialAttackEnabled() == desired) {
            return true;
        }

        int orbButton = WidgetInfo.MINIMAP_SPEC_ORB.getId() + 2;
        Rs2Widget.clickWidget(orbButton);
        sleepUntil(() -> isSpecialAttackEnabled() == desired, 800);
        if (isSpecialAttackEnabled() == desired) {
            return true;
        }

        Rs2Combat.setSpecState(desired, 0);
        sleepUntil(() -> isSpecialAttackEnabled() == desired, 800);
        return isSpecialAttackEnabled() == desired;
    }

    private boolean isSpecialAttackEnabled() {
        int enabled = Microbot.getVarbitPlayerValue(VarPlayerID.SA_ATTACK);
        if (enabled == 0 || enabled == 1) {
            return enabled == 1;
        }
        return Rs2Combat.getSpecState();
    }

    private void debugSpecialAttack(TormentedDemonConfig config, String message) {
        // Always logged (not gated by the debug toggles): the special attack phase is hard to
        // diagnose from chat messages alone, and state transitions are deduplicated below so this
        // produces one line per change rather than one per main-loop tick.
        String line = "[TD spec] " + message;
        if (line.equals(lastSpecialAttackDebug)) {
            return;
        }
        lastSpecialAttackDebug = line;
        Microbot.log(line);
    }

    private String getEquippedWeaponName() {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        return weapon == null ? null : weapon.getName();
    }

    private int getSpecialAttackEnergyRequired(String weaponName) {
        if (weaponName == null) {
            return -1;
        }

        String lower = weaponName.toLowerCase(Locale.ROOT);
        int required = -1;
        int matchedLength = 0;
        for (SpecialAttackWeaponEnum specialAttack : SpecialAttackWeaponEnum.values()) {
            String name = specialAttack.getName().toLowerCase(Locale.ROOT);
            if (lower.contains(name) && name.length() > matchedLength) {
                required = specialAttack.getEnergyRequired();
                matchedLength = name.length();
            }
        }

        if (required >= 0) {
            return required;
        }
        if (lower.contains("blue moon spear")) {
            return 500;
        }
        return -1;
    }

    private void addIfUsable(TormentedDemonConfig config, List<CombatStyle> candidates, CombatStyle style) {
        if (isStyleUsable(config, style)) {
            candidates.add(style);
        }
    }

    private boolean isStyleUsable(TormentedDemonConfig config, CombatStyle style) {
        switch (style) {
            case MELEE:
                return config.useMeleeStyle() && hasGear(config.meleeGear());
            case MAGIC:
                return config.useMagicStyle() && hasGear(config.magicGear());
            case RANGE:
                return config.useRangeStyle() && hasGear(config.rangeGear());
            default:
                return false;
        }
    }

    private CombatStyle detectStyle(TormentedDemonConfig config) {
        CombatStyle attackStyle = detectAttackStyle(config);
        if (attackStyle != null) {
            return attackStyle;
        }

        for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.MAGIC, CombatStyle.RANGE}) {
            if (isStyleUsable(config, style) && isGearEquipped(gearForStyle(config, style))) {
                return style;
            }
        }
        return configuredStyle(config);
    }

    private CombatStyle detectAttackStyle(TormentedDemonConfig config) {
        if (Rs2Equipment.isWearing("Blue Moon spear")) {
            int combatMode = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE);
            if (combatMode == 4) {
                return config.useMagicStyle() ? CombatStyle.MAGIC : null;
            }
            return config.useMeleeStyle() ? CombatStyle.MELEE : null;
        }

        String attackStyle = null;
        try {
            attackStyle = Rs2Combat.getWeaponAttackStyle();
        } catch (RuntimeException ignored) {
            // COMBAT_WEAPON_CATEGORY is briefly 0 while an equipment switch is being applied, so
            // the weapon-style enum lookup returns null. Fall through to the weapon-name check
            // instead of reporting "unknown" and re-equipping the same gear in a loop.
        }

        CombatStyle detected = attackStyleFromName(attackStyle);
        if (detected == null) {
            detected = attackStyleFromWeapon(getEquippedWeaponName());
        }
        if (detected != null) {
            return isStyleEnabled(config, detected) ? detected : null;
        }

        try {
            if (config.useMagicStyle() && Rs2Magic.getCurrentAutoCastSpell() != null) {
                return CombatStyle.MAGIC;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private CombatStyle attackStyleFromName(String attackStyle) {
        if (attackStyle == null) {
            return null;
        }
        String style = attackStyle.toLowerCase(Locale.ROOT);
        if (style.equals("magic") || style.equals("cast") || style.equals("casting")
                || style.equals("spell") || style.equals("autocast")) {
            return CombatStyle.MAGIC;
        }
        if (style.equals("ranged") || style.equals("ranging") || style.equals("rapid")
                || style.contains("longrange") || style.contains("long range")) {
            return CombatStyle.RANGE;
        }
        if (isMeleeAttackStyle(style)) {
            return CombatStyle.MELEE;
        }
        return null;
    }

    /**
     * Last-resort style detection for the window right after an equipment change, when the
     * client's weapon-style enum cannot be read yet. The weapon name is enough to tell the three
     * styles apart; anything unrecognised is treated as melee so the bot keeps attacking instead
     * of looping through gear switches.
     */
    private CombatStyle attackStyleFromWeapon(String weaponName) {
        if (weaponName == null) {
            return null;
        }
        String name = weaponName.toLowerCase(Locale.ROOT);
        if (name.contains("staff") || name.contains("wand") || name.contains("trident")
                || name.contains("sceptre") || name.contains("scepter")
                || name.contains("purging") || name.contains("nightmare")
                || name.contains("kodai") || name.contains("sang")
                || name.contains("shadow") || name.contains("ahrim")
                || name.contains("ayak") || name.contains("tumeken")
                || name.contains("volatile") || name.contains("eldritch")
                || name.contains("harmonised") || name.contains("dawnbringer")) {
            return CombatStyle.MAGIC;
        }
        if (name.contains("bow") || name.contains("blowpipe") || name.contains("crossbow")
                || name.contains("ballista") || name.contains("javelin")
                || name.contains("dart") || name.contains("knife")
                || name.contains("chinchompa") || name.contains("atlatl")
                || name.contains("scorching") || name.contains("twisted")
                || name.contains("seercull")) {
            return CombatStyle.RANGE;
        }
        return CombatStyle.MELEE;
    }

    private boolean isStyleEnabled(TormentedDemonConfig config, CombatStyle style) {
        switch (style) {
            case MELEE:
                return config.useMeleeStyle();
            case MAGIC:
                return config.useMagicStyle();
            case RANGE:
                return config.useRangeStyle();
            default:
                return false;
        }
    }

    private boolean isMeleeAttackStyle(String style) {
        switch (style) {
            case "stab":
            case "slash":
            case "crush":
            case "jab":
            case "swipe":
            case "pound":
            case "pummel":
            case "block":
            case "hack":
            case "smash":
            case "slam":
            case "reap":
            case "chop":
            case "lunge":
            case "fend":
            case "punch":
            case "kick":
            case "aggressive":
            case "controlled":
                return true;
            default:
                return false;
        }
    }

    private CombatStyle configuredStyle(TormentedDemonConfig config) {
        if (config.useMagicStyle() && hasGear(config.magicGear())) return CombatStyle.MAGIC;
        if (config.useRangeStyle() && hasGear(config.rangeGear())) return CombatStyle.RANGE;
        if (config.useMeleeStyle() && hasGear(config.meleeGear())) return CombatStyle.MELEE;
        return null;
    }

    private List<String> gearForStyle(TormentedDemonConfig config, CombatStyle style) {
        switch (style) {
            case MELEE:
                return parseGear(config.meleeGear());
            case MAGIC:
                return parseGear(config.magicGear());
            case RANGE:
                return parseGear(config.rangeGear());
            default:
                return Collections.emptyList();
        }
    }

    private List<String> parseGear(String gearString) {
        if (gearString == null) return Collections.emptyList();
        return Arrays.stream(gearString.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private boolean hasGear(String gearString) {
        return !parseGear(gearString).isEmpty();
    }

    private boolean isGearEquipped(List<String> gear) {
        if (gear == null || gear.isEmpty()) return false;
        boolean any = false;
        for (String item : gear) {
            if (item == null || item.trim().isEmpty()) continue;
            any = true;
            if (!Rs2Equipment.isWearing(item.trim())) return false;
        }
        return any;
    }

    private void equipGear(List<String> gear) {
        if (gear == null || gear.isEmpty()) return;

        List<String> missing = new ArrayList<>();
        for (String item : gear) {
            if (item == null || item.trim().isEmpty()) continue;
            if (Rs2Equipment.isWearing(item)) continue;
            missing.add(item.trim());
        }
        if (missing.isEmpty()) return;

        TormentedDemonConfig.GearSwitchMode mode = activeConfig != null
                ? activeConfig.gearSwitchMode()
                : TormentedDemonConfig.GearSwitchMode.NORMAL;
        long start = System.currentTimeMillis();
        sendEquipActions(missing, mode);

        boolean equipped = sleepUntil(() -> isGearEquipped(gear), 900);
        if (!equipped) {
            // A click can land while the inventory is still updating (for example after eating or
            // a previous switch). Retry only the slots that are still missing, then wait once more.
            List<String> stillMissing = new ArrayList<>();
            for (String item : missing) {
                if (!Rs2Equipment.isWearing(item)) {
                    stillMissing.add(item);
                }
            }
            if (!stillMissing.isEmpty()) {
                sendEquipActions(stillMissing, mode);
                equipped = sleepUntil(() -> isGearEquipped(gear), 700);
            } else {
                equipped = true;
            }
        }

        if (missing.size() > 1) {
            String modeLabel = mode == TormentedDemonConfig.GearSwitchMode.INSTANT ? " (instant)"
                    : mode == TormentedDemonConfig.GearSwitchMode.QUICK ? " (quick)" : "";
            Microbot.log("[TD gear] " + (equipped ? "equipped " : "partially equipped ")
                    + missing.size() + " slot(s) in " + (System.currentTimeMillis() - start) + "ms"
                    + modeLabel);
        }
    }

    /**
     * Equips every item using the configured switch mode.
     *
     * <p>NORMAL fires the regular mouse-driven equip clicks back-to-back, but each of those calls
     * still sleeps 50-100 ms internally and holds the button for 55-140 ms, so a five-slot swap
     * spreads over several hundred milliseconds.</p>
     *
     * <p>QUICK uses the same mouse-driven path but through {@link Microbot#doInvokeQuick}, whose
     * quick click holds the button for only 30-65 ms and settles for 10-25 ms. It still moves the
     * mouse to every slot, so it looks like a fast human switch rather than a single-tick dump.</p>
     *
     * <p>INSTANT queues a raw inventory menu action for every slot inside one client-thread
     * callback: no mouse movement and no per-slot sleep, so the whole switch is handled in the same
     * game tick (the same result as spam-clicking the switch manually).</p>
     *
     * <p>If the inventory widget cannot be read, every mode falls back to the regular click path
     * so the swap still happens.</p>
     */
    private void sendEquipActions(List<String> items, TormentedDemonConfig.GearSwitchMode mode) {
        if (items == null || items.isEmpty()) return;

        if (mode == TormentedDemonConfig.GearSwitchMode.INSTANT) {
            if (equipBatchOnClientThread(items) > 0) {
                return;
            }
        } else if (mode == TormentedDemonConfig.GearSwitchMode.QUICK) {
            if (equipQuick(items) > 0) {
                return;
            }
        }

        for (String item : items) {
            if (!Rs2Inventory.wield(item)) {
                logOnceToChat("Could not equip " + item + ".");
            }
        }
    }

    /**
     * Reads the equip action for every item once, then fires a quick mouse click for each. The
     * widget details have to be read on the client thread, but the clicks themselves are sent from
     * this (script) thread so the normal mouse gesture and settle timing still apply.
     */
    private int equipQuick(List<String> items) {
        List<EquipAction> actions = collectEquipActions(items);
        int sent = 0;
        for (EquipAction action : actions) {
            if (action.bounds == null) {
                continue;
            }
            NewMenuEntry entry = new NewMenuEntry()
                    .option(action.option)
                    .param0(action.slot)
                    .param1(ComponentID.INVENTORY_CONTAINER)
                    .type(action.actionIndex < 10 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY)
                    .identifier(action.actionIndex + 1)
                    .itemId(action.itemId)
                    .target(action.itemName);
            Microbot.doInvokeQuick(entry, action.bounds);
            sent++;
        }
        return sent;
    }

    /** Reads the equip menu action and click box for each item on the client thread. */
    private List<EquipAction> collectEquipActions(List<String> items) {
        Rs2Tab.switchToInventoryTab();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<EquipAction> actions = new ArrayList<>();
            if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                return actions;
            }
            Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
            if (inventory == null || inventory.isHidden()) {
                return actions;
            }

            for (String name : items) {
                Rs2ItemModel item = Rs2Inventory.get(name);
                if (item == null) {
                    continue;
                }
                Widget child = inventory.getChild(item.getSlot());
                if (child == null || child.getItemId() != item.getId()) {
                    continue;
                }
                String[] itemActions = child.getActions();
                if (itemActions == null) {
                    itemActions = item.getInventoryActions();
                }
                int actionIndex = findEquipActionIndex(itemActions);
                if (actionIndex < 0) {
                    continue;
                }
                actions.add(new EquipAction(item.getSlot(), item.getId(), actionIndex,
                        itemActions[actionIndex], item.getName(), child.getBounds()));
            }
            return actions;
        }).orElse(Collections.emptyList());
    }

    /**
     * Client-thread only. Sends one equip menu action per item without any artificial delay and
     * returns how many actions were queued. Mirrors the batching used by the action-bar style
     * plugins, including the low-priority opcode for actions beyond the first ten.
     */
    private int equipBatchOnClientThread(List<String> items) {
        Rs2Tab.switchToInventoryTab();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                return 0;
            }
            Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
            if (inventory == null || inventory.isHidden()) {
                return 0;
            }

            int sent = 0;
            for (String name : items) {
                Rs2ItemModel item = Rs2Inventory.get(name);
                if (item == null) {
                    continue;
                }
                Widget child = inventory.getChild(item.getSlot());
                if (child == null || child.getItemId() != item.getId()) {
                    continue;
                }
                String[] actions = child.getActions();
                if (actions == null) {
                    actions = item.getInventoryActions();
                }
                int actionIndex = findEquipActionIndex(actions);
                if (actionIndex < 0) {
                    continue;
                }
                Microbot.getClient().menuAction(item.getSlot(), ComponentID.INVENTORY_CONTAINER,
                        actionIndex < 10 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY,
                        actionIndex + 1, item.getId(), actions[actionIndex], item.getName());
                sent++;
            }
            return sent;
        }).orElse(0);
    }

    private static int findEquipActionIndex(String[] actions) {
        if (actions == null) {
            return -1;
        }
        for (int i = 0; i < actions.length; i++) {
            String action = actions[i];
            if ("Wear".equalsIgnoreCase(action) || "Wield".equalsIgnoreCase(action)
                    || "Equip".equalsIgnoreCase(action)) {
                return i;
            }
        }
        return -1;
    }

    /** One queued equip action: everything needed to build a menu entry without another lookup. */
    private static final class EquipAction {
        final int slot;
        final int itemId;
        final int actionIndex;
        final String option;
        final String itemName;
        final Rectangle bounds;

        EquipAction(int slot, int itemId, int actionIndex, String option, String itemName, Rectangle bounds) {
            this.slot = slot;
            this.itemId = itemId;
            this.actionIndex = actionIndex;
            this.option = option;
            this.itemName = itemName;
            this.bounds = bounds;
        }
    }

    private void saveCurrentGear() {
        if (lastGearList == null) {
            lastGearList = new ArrayList<>();
            try {
                Rs2Equipment.items().forEach(item -> {
                    if (item == null || item.getId() == -1 || item.getId() == 6512) return;
                    String name = item.getName();
                    if (name != null && !name.isEmpty()) {
                        lastGearList.add(name);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    private void restoreGear(List<String> gear) {
        if (gear == null || gear.isEmpty()) return;
        equipGear(gear);
    }

    private void restoreGearAfterPhases(TormentedDemonConfig config) {
        if (swappedForPunish || swappedForSpec) {
            if (lastGearList != null && !lastGearList.isEmpty()) {
                equipGear(lastGearList);
            }
            swappedForPunish = false;
            swappedForSpec = false;
            punishSwappedAt = 0;
            lastGearList = null;
            gearSwitchGraceUntil = System.currentTimeMillis() + GEAR_RESTORE_GRACE_MS;
        }
    }

    private void handlePhases(TormentedDemonConfig config) {
        if (currentTarget == null || isTargetDead(currentTarget)) {
            restoreGearAfterPhases(config);
            return;
        }

        long now = System.currentTimeMillis();
        if (fightStartTime == 0) {
            fightStartTime = now;
        }

        // Defenseless phase: the flames disappear 30 ticks (18 seconds) into the fight, and the
        // 30-tick timer restarts after every fire bomb attack.
        long defenselessSince = lastFirebombTime == 0 ? fightStartTime : lastFirebombTime;
        inDefenselessPhase = now - defenselessSince >= DEFENSELESS_DELAY_MS;

        // Punish window: only the fire bomb drives this. The shield drops when the fire bomb attack
        // lands and stays down until the demon is attacked once, so the swap happens after the
        // bombs have exploded and the dodge is over, and the window closes with the first player
        // hit (or after a timeout). The shield drop at the start of a fight is intentionally NOT
        // handled here. This runs before the special-attack block so a spec weapon is never used
        // for the shield-down hit.
        if (!config.usePunishGear()) {
            punishGearArmed = false;
        }

        long punishPhaseStartsAt = Math.max(lastFirebombTime + PUNISH_PHASE_DELAY_MS, dodgeUntil);

        // If a hit already landed after the bombs, the shield has been consumed and there is
        // nothing left to punish: do not waste a gear swap on it. Hits before the bomb impact
        // (while still dodging) do not count, because the shield is not down yet.
        if (punishGearArmed && lastFirebombTime > 0
                && lastPlayerHitOnTargetAt >= lastFirebombTime + PUNISH_PHASE_DELAY_MS) {
            punishGearArmed = false;
        }

        if (config.usePunishGear() && lastFirebombTime > 0 && punishGearArmed
                && !inDefenselessPhase && now >= punishPhaseStartsAt) {
            HeadIcon overhead = getHeadIcon(currentTarget);
            List<String> punishGear = overhead == null
                    ? Collections.emptyList()
                    : getPunishGearForOverhead(config, overhead);
            if (!punishGear.isEmpty()) {
                if (!swappedForPunish || !isGearEquipped(punishGear)) {
                    saveCurrentGear();
                    if (swappedForSpec) {
                        // Keep the pre-spec gear list as the restore target so the punish swap does
                        // not bounce through a pointless spec-gear restore first.
                        swappedForSpec = false;
                    }
                    logOnceToChat("Swapping to Punish gear vs " + overhead
                            + " for shield down phase.");
                    equipGear(punishGear);
                    swappedForPunish = true;
                    punishSwappedAt = System.currentTimeMillis();
                    gearSwitchGraceUntil = System.currentTimeMillis() + GEAR_SWITCH_GRACE_MS;
                }
                inPunishPhase = true;
            }
        }

        if (swappedForPunish) {
            long windowStart = Math.max(punishSwappedAt,
                    lastFirebombTime + PUNISH_PHASE_DELAY_MS);
            // A hitsplat from the player on the target is the reliable "shield consumed" signal;
            // player animations also fire for walking and running, which happens during the dodge.
            boolean attackLanded = lastPlayerHitOnTargetAt >= windowStart;
            boolean timedOut = now - punishSwappedAt >= PUNISH_MAX_HOLD_MS;
            if (attackLanded || timedOut) {
                // The shield is back up after the first attack; return to the normal prayer-based
                // rotation instead of keeping the slow weapon equipped.
                inPunishPhase = false;
                punishGearArmed = false;
                restoreGearAfterPhases(config);
            } else {
                inPunishPhase = true;
            }
        } else if (punishGearArmed && now >= punishPhaseStartsAt + PUNISH_MAX_HOLD_MS) {
            // Punish gear is not configured for the current overhead; do not stay armed forever.
            punishGearArmed = false;
        }

        // Handle special attack gear during defenseless phase. The set is chosen by the demon's
        // overhead prayer, so a spec weapon that only works against certain prayers (e.g. the Blue
        // Moon spear needing its full set) is never swapped in for a prayer it cannot beat.
        List<String> specGear = getSpecGearForOverhead(config, getHeadIcon(currentTarget));
        boolean specGearConfigured = config.useSpecialAttackGear() && !specGear.isEmpty();
        if (specGearConfigured && inDefenselessPhase) {
            int energy = Rs2Combat.getSpecEnergy();
            int threshold = specThresholdEnergy(config);
            if (energy >= threshold) {
                if (!swappedForSpec || !isGearEquipped(specGear)) {
                    saveCurrentGear();
                    logOnceToChat("Swapping to Special Attack gear for defenseless phase.");
                    debugSpecialAttack(config, "swapping spec gear energy=" + energy
                            + " threshold=" + threshold + " items=" + specGear);
                    equipGear(specGear);
                    swappedForSpec = true;
                    gearSwitchGraceUntil = System.currentTimeMillis() + GEAR_SWITCH_GRACE_MS;
                }
            } else if (swappedForSpec) {
                debugSpecialAttack(config, "spec energy below threshold energy=" + energy
                        + " threshold=" + threshold + "; restoring normal gear");
                restoreGearAfterPhases(config);
            }
        } else if (swappedForSpec) {
            // Either the defenseless phase ended or this prayer has no spec set configured; either
            // way the spec gear must not stay equipped.
            debugSpecialAttack(config, "spec window ended defenseless=" + inDefenselessPhase
                    + " gear=" + (specGear.isEmpty() ? "none" : specGear.size() + " items"));
            restoreGearAfterPhases(config);
        }
    }

    private List<String> getPunishGearForOverhead(TormentedDemonConfig config, HeadIcon overhead) {
        if (overhead == null) {
            return Collections.emptyList();
        }
        switch (overhead) {
            case MELEE:
                return parseGear(config.punishGearVsMelee());
            case RANGED:
                return parseGear(config.punishGearVsRange());
            case MAGIC:
                return parseGear(config.punishGearVsMagic());
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Resolves the special attack gear for the demon's current overhead prayer.
     * <p>
     * Per-prayer boxes take priority. When none of them are filled in, the single fallback
     * special attack gear box is used so existing single-set configurations keep working. An empty
     * result means "do not special attack against this prayer".
     */
    private List<String> getSpecGearForOverhead(TormentedDemonConfig config, HeadIcon overhead) {
        List<String> vsMelee = parseGear(config.specialAttackGearVsMelee());
        List<String> vsRange = parseGear(config.specialAttackGearVsRange());
        List<String> vsMagic = parseGear(config.specialAttackGearVsMagic());

        if (vsMelee.isEmpty() && vsRange.isEmpty() && vsMagic.isEmpty()) {
            return parseGear(config.specialAttackGear());
        }

        if (overhead == null) {
            return Collections.emptyList();
        }
        switch (overhead) {
            case MELEE:
                return vsMelee;
            case RANGED:
                return vsRange;
            case MAGIC:
                return vsMagic;
            default:
                return Collections.emptyList();
        }
    }

    private void activateOffensivePrayer(TormentedDemonConfig config, CombatStyle style) {
        if (style == null) {
            return;
        }

        Rs2PrayerEnum desired;
        switch (style) {
            case MAGIC: {
                TormentedDemonConfig.MagicOffensivePrayer option = config.magicOffensivePrayer();
                desired = option.isAuto() ? Rs2Prayer.getBestMagePrayer() : option.getPrayer();
                break;
            }
            case RANGE: {
                TormentedDemonConfig.RangedOffensivePrayer option = config.rangeOffensivePrayer();
                desired = option.isAuto() ? Rs2Prayer.getBestRangePrayer() : option.getPrayer();
                break;
            }
            case MELEE: {
                TormentedDemonConfig.MeleeOffensivePrayer option = config.meleeOffensivePrayer();
                desired = option.isAuto() ? Rs2Prayer.getBestMeleePrayer() : option.getPrayer();
                break;
            }
            default:
                return;
        }

        if (desired == null) {
            disableOffensivePrayer();
            return;
        }

        // Trust the game state over the cached field: a prayer can drop on its own (prayer points,
        // a manual click, quick-prayer changes) and the old code then never switched it back on.
        if (currentOffensivePrayer == desired && Rs2Prayer.isPrayerActive(desired)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastOffensivePrayerAttemptAt < OFFENSIVE_PRAYER_RETRY_MS) {
            return;
        }
        lastOffensivePrayerAttemptAt = now;

        if (currentOffensivePrayer != null && currentOffensivePrayer != desired) {
            setPrayerActive(currentOffensivePrayer, false);
            currentOffensivePrayer = null;
        }

        boolean activated = setPrayerActive(desired, true);
        currentOffensivePrayer = activated ? desired : null;
        if (activated) {
            logOnceToChat("Offensive prayer on: " + desired.getName() + " (" + style + ").");
        } else {
            logOnceToChat("Could not activate " + desired.getName()
                    + "; check prayer level, unlocks, and prayer points.");
        }
    }

    /**
     * Starts or stops a single prayer without the 10-second blocking wait built into
     * {@link Rs2Prayer#toggle(Rs2PrayerEnum, boolean)}. A prayer that cannot be activated (not
     * unlocked, no prayer points) would otherwise stall the whole combat loop on every attempt.
     */
    private boolean setPrayerActive(Rs2PrayerEnum prayer, boolean on) {
        if (prayer == null) {
            return false;
        }
        if (Rs2Prayer.isPrayerActive(prayer) == on) {
            return true;
        }
        if (on && Rs2Prayer.isOutOfPrayer()) {
            return false;
        }

        Microbot.doInvoke(new NewMenuEntry()
                .param0(-1)
                .param1(prayer.getIndex())
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .option(on ? "Activate" : "Deactivate"), Rs2UiHelper.getDefaultRectangle());
        return sleepUntil(() -> Rs2Prayer.isPrayerActive(prayer) == on, 1_200);
    }

    private void disableOffensivePrayer() {
        if (currentOffensivePrayer != null) {
            setPrayerActive(currentOffensivePrayer, false);
            currentOffensivePrayer = null;
        }
    }

    private int prayerThreshold(TormentedDemonConfig config) {
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        return Math.max(1, (int) Math.ceil(maxPrayer * (config.minPrayerPercent() / 100.0)));
    }

    /**
     * Leaves combat when the player can no longer be kept alive: health at the exit threshold, no
     * food left when the bot wants to eat, or no prayer doses left at the prayer threshold.
     *
     * <p>Full Auto keeps its original behaviour and retreats to Ferox Enclave to restock. Combat
     * Only cannot restock, so it stops the plugin instead of standing in the cave until it dies.</p>
     *
     * <p>Runs on every fighting tick, including after a kill and while no demon is targeted, so a
     * finished fight or an empty cave cannot hide a low-health state.</p>
     *
     * @return true when combat must stop for this tick (retreat or plugin stop)
     */
    private boolean handleSupplySafety(TormentedDemonConfig config) {
        boolean fullAuto = config.mode() == TormentedDemonConfig.MODE.FULL_AUTO;
        if (!fullAuto && !config.stopWhenOutOfSupplies()) {
            return false;
        }

        double healthPercentage = Rs2Player.getHealthPercentage();
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        boolean outOfFood = !hasHealingSupply();
        boolean outOfPrayerPotions = !hasPrayerSupply();
        boolean prayerEnabled = usesPrayer(config);

        boolean healthExit;
        if (fullAuto) {
            healthExit = healthPercentage <= config.healthThreshold()
                    || (outOfFood && healthPercentage <= config.minEatPercent());
        } else {
            // With no food the eat threshold becomes the stop point: from there on the bot can no
            // longer heal, so waiting for the lower exit threshold would only add risk.
            healthExit = outOfFood
                    && healthPercentage <= Math.max(config.healthThreshold(), config.minEatPercent());
        }
        boolean prayerExit = prayerEnabled
                && outOfPrayerPotions
                && currentPrayer <= prayerThreshold(config);

        if (!healthExit && !prayerExit) {
            return false;
        }

        String reason;
        if (prayerExit) {
            reason = String.format(Locale.ROOT,
                    "No prayer potions left and prayer is %d (threshold %d)",
                    currentPrayer, prayerThreshold(config));
        } else if (outOfFood) {
            reason = String.format(Locale.ROOT,
                    "No food left and health is %.0f%% (exit threshold %d%%)",
                    healthPercentage, config.healthThreshold());
        } else {
            reason = String.format(Locale.ROOT,
                    "Health is %.0f%%, at or below the %d%% exit threshold",
                    healthPercentage, config.healthThreshold());
        }

        if (fullAuto) {
            Microbot.status = reason + ". Retreating to Ferox Enclave to restock.";
            logOnceToChat(Microbot.status);
            currentTarget = null;
            currentTargetIndex = -1;
            currentOverheadIcon = null;
            currentCombatStyle = null;
            restoreGearAfterPhases(config);
            BOT_STATUS = State.RETREAT;
            return true;
        }

        stopForSupplies(config, reason, true);
        return true;
    }

    private boolean usesPrayer(TormentedDemonConfig config) {
        return config.enableDefensivePrayer() || config.enableOffensivePrayer();
    }

    /**
     * True when the inventory holds something the eat helper can actually consume. Noted food is
     * excluded by {@link Rs2Inventory#getInventoryFood()}, which matches the eating logic.
     */
    private boolean hasHealingSupply() {
        return !Rs2Inventory.getInventoryFood().isEmpty();
    }

    /**
     * True when any prayer-restoring dose is in the inventory. Doses are separate items, so any
     * match means at least one dose; names are compared without the (4)/(3)/(2)/(1) suffix.
     */
    private boolean hasPrayerSupply() {
        return Rs2Inventory.items().anyMatch(item -> item != null && isPrayerSupply(item.getName()));
    }

    private boolean bankHasHealingSupply() {
        return Rs2Bank.bankItems().stream()
                .anyMatch(item -> item != null && item.getQuantity() > 0 && item.isFood());
    }

    private boolean bankHasPrayerSupply() {
        return Rs2Bank.bankItems().stream()
                .anyMatch(item -> item != null && item.getQuantity() > 0 && isPrayerSupply(item.getName()));
    }

    private boolean isPrayerSupply(String itemName) {
        String baseName = normalizePotionName(itemName);
        return !baseName.isEmpty() && PRAYER_SUPPLY_NAMES.contains(baseName);
    }

    private void stopForSupplies(TormentedDemonConfig config, String reason, boolean escapeFirst) {
        // Only a stop that happens away from the bank can leave the player in the cave mid-fight.
        if (escapeFirst && shouldEscapeBeforeStopping(config)) {
            escapeBeforeStopping(config);
        }
        disableAllPrayers();
        Microbot.status = reason + ". Stopping.";
        logOnceToChat(Microbot.status);
        Microbot.stopPlugin(TormentedDemonPlugin.class);
    }

    /**
     * True when stopping right now would strand the player next to a Tormented Demon with no
     * supplies. {@link Rs2Player#isInCombat()} uses a 10 second window, so a demon that has not
     * connected for a moment still counts.
     */
    private boolean shouldEscapeBeforeStopping(TormentedDemonConfig config) {
        if (!config.emergencyTeleport()
                || config.emergencyTeleportMethod() == TormentedDemonConfig.EmergencyTeleportMethod.NONE) {
            return false;
        }
        return Rs2Player.isInCombat() || Rs2Player.isInteracting();
    }

    /**
     * Leaves the cave before the plugin shuts down. Offensive prayer is dropped because it cannot
     * be used while leaving, but protection stays up until shutdown so the last hits are still
     * reduced. The configured teleport is tried first; if it is missing or fails, the plugin works
     * down {@link #EMERGENCY_TELEPORT_FALLBACKS}. If nothing works the stop continues so the bot
     * never hangs in a dying state.
     */
    private void escapeBeforeStopping(TormentedDemonConfig config) {
        TormentedDemonConfig.EmergencyTeleportMethod method = config.emergencyTeleportMethod();
        disableOffensivePrayer();
        Microbot.status = "Teleporting out before stopping...";
        logOnceToChat(Microbot.status);

        boolean teleported = false;
        if (hasEmergencyTeleport(method)) {
            teleported = attemptEmergencyTeleport(method);
            if (!teleported) {
                logOnceToChat("Emergency teleport failed (" + method + "); trying another.");
            }
        } else {
            logOnceToChat("Emergency teleport not available (" + method + "); trying another.");
        }

        if (!teleported) {
            for (TormentedDemonConfig.EmergencyTeleportMethod fallback : EMERGENCY_TELEPORT_FALLBACKS) {
                if (fallback == method || !hasEmergencyTeleport(fallback)) {
                    continue;
                }
                logOnceToChat("Trying emergency teleport: " + fallback + ".");
                if (attemptEmergencyTeleport(fallback)) {
                    teleported = true;
                    break;
                }
            }
        }

        if (!teleported) {
            logOnceToChat("No emergency teleport available; stopping while still in combat.");
            return;
        }

        if (sleepUntil(() -> !Rs2Player.isInCombat(), EMERGENCY_TELEPORT_COMBAT_WAIT_MS)) {
            logOnceToChat("Emergency teleport complete; out of combat, stopping.");
        } else {
            logOnceToChat("Still in combat " + (EMERGENCY_TELEPORT_COMBAT_WAIT_MS / 1000)
                    + "s after the emergency teleport; stopping anyway.");
        }
    }

    private boolean attemptEmergencyTeleport(TormentedDemonConfig.EmergencyTeleportMethod method) {
        switch (method) {
            case RING_OF_DUELING:
                return teleportToFeroxEnclave();
            case AMULET_OF_GLORY:
                return teleportWithJewellery("Amulet of glory", JewelleryLocationEnum.EDGEVILLE.getDestination());
            case RING_OF_WEALTH:
                return teleportWithJewellery("Ring of wealth", JewelleryLocationEnum.GRAND_EXCHANGE.getDestination());
            case SKILLS_NECKLACE:
                return teleportWithJewellery("Skills necklace", JewelleryLocationEnum.FISHING_GUILD_NECK.getDestination());
            case COMBAT_BRACELET:
                return teleportWithJewellery("Combat bracelet", JewelleryLocationEnum.WARRIORS_GUILD.getDestination());
            case ROYAL_SEED_POD:
                return useTeleportItem(ItemID.MM2_ROYAL_SEED_POD, "Commune", "royal seed pod");
            case VARROCK_TAB:
                return useTeleportItem(ItemID.POH_TABLET_VARROCKTELEPORT, "Break", "Varrock teleport tab");
            case LUMBRIDGE_TAB:
                return useTeleportItem(ItemID.POH_TABLET_LUMBRIDGETELEPORT, "Break", "Lumbridge teleport tab");
            case FALADOR_TAB:
                return useTeleportItem(ItemID.POH_TABLET_FALADORTELEPORT, "Break", "Falador teleport tab");
            case CAMELOT_TAB:
                return useTeleportItem(ItemID.POH_TABLET_CAMELOTTELEPORT, "Break", "Camelot teleport tab");
            case ARDOUGNE_TAB:
                return useTeleportItem(ItemID.POH_TABLET_ARDOUGNETELEPORT, "Break", "Ardougne teleport tab");
            case WATCHTOWER_TAB:
                return useTeleportItem(ItemID.POH_TABLET_WATCHTOWERTELEPORT, "Break", "Watchtower teleport tab");
            case HOUSE_TAB:
                return useTeleportItem(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Break", "house teleport tab");
            case GUTHIXIAN_TEMPLE:
                return teleportToGuthixianTemple();
            default:
                return false;
        }
    }

    /**
     * True when the configured emergency teleport could actually be used right now, so the
     * fallback list only logs and tries teleports the player really has.
     */
    private boolean hasEmergencyTeleport(TormentedDemonConfig.EmergencyTeleportMethod method) {
        switch (method) {
            case RING_OF_DUELING:
                return isWearingOrHolding("Ring of dueling");
            case AMULET_OF_GLORY:
                return isWearingOrHolding("Amulet of glory");
            case RING_OF_WEALTH:
                return isWearingOrHolding("Ring of wealth");
            case SKILLS_NECKLACE:
                return isWearingOrHolding("Skills necklace");
            case COMBAT_BRACELET:
                return isWearingOrHolding("Combat bracelet");
            case ROYAL_SEED_POD:
                return Rs2Inventory.hasItem(ItemID.MM2_ROYAL_SEED_POD);
            case VARROCK_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_VARROCKTELEPORT);
            case LUMBRIDGE_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_LUMBRIDGETELEPORT);
            case FALADOR_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_FALADORTELEPORT);
            case CAMELOT_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_CAMELOTTELEPORT);
            case ARDOUGNE_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_ARDOUGNETELEPORT);
            case WATCHTOWER_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_WATCHTOWERTELEPORT);
            case HOUSE_TAB:
                return Rs2Inventory.hasItem(ItemID.POH_TABLET_TELEPORTTOHOUSE);
            case GUTHIXIAN_TEMPLE:
                return Rs2Inventory.hasItem("Guthixian temple teleport");
            default:
                return false;
        }
    }

    private boolean isWearingOrHolding(String itemName) {
        return Rs2Equipment.isWearing(itemName) || Rs2Inventory.hasItem(itemName);
    }

    /**
     * Rubs a teleport jewellery item. Worn jewellery is used directly; one in the inventory is
     * equipped first because the destinations live on the equipment menu.
     */
    private boolean teleportWithJewellery(String itemName, String destination) {
        if (!Rs2Equipment.isWearing(itemName)) {
            if (!Rs2Inventory.hasItem(itemName) || !Rs2Inventory.interact(itemName, "Wear")) {
                return false;
            }
            if (!sleepUntil(() -> Rs2Equipment.isWearing(itemName), 3000)) {
                return false;
            }
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        if (!Rs2Equipment.interact(itemName, destination)) {
            return false;
        }
        logOnceToChat("Teleporting with " + itemName + " (" + destination + ").");
        return waitForTeleport(before);
    }

    /**
     * Breaks a teleport tablet, or uses any other single-click teleport item.
     */
    private boolean useTeleportItem(int itemId, String action, String label) {
        if (!Rs2Inventory.hasItem(itemId)) {
            return false;
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        if (!Rs2Inventory.interact(itemId, action)) {
            return false;
        }
        logOnceToChat("Using the " + label + " to leave the cave.");
        return waitForTeleport(before);
    }

    /**
     * Waits for the teleport animation to finish and for the player to actually move. Comparing
     * positions works for every destination, including the player-owned house, whose location
     * differs per account.
     */
    private boolean waitForTeleport(WorldPoint before) {
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        if (before == null) {
            return true;
        }
        return sleepUntil(() -> {
            WorldPoint moved = Rs2Player.getWorldLocation();
            return moved != null && moved.distanceTo(before) > 20;
        }, 12_000);
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        currentOffensivePrayer = null;
    }

    private void attemptLooting(TormentedDemonConfig config) {
        Map<Integer, Integer> inventoryBefore = snapshotInventoryCounts();
        try {
            doLoot(config);
        } finally {
            recordLootGained(inventoryBefore);
        }
    }

    private void doLoot(TormentedDemonConfig config) {
        if (!config.toggleLoot()) {
            if (config.scatterAshes()) {
                lootAndScatterInfernalAshes(config);
            }
            return;
        }

        Microbot.log("Checking loot..");

        // Always try to loot infernal ashes directly if enabled
        if (config.lootAshes() || config.scatterAshes()) {
            lootAndScatterInfernalAshesDirect(config);
        }

        LootingParameters params = new LootingParameters(
                config.minPriceOfItemsToLoot(),
                config.maxPriceOfItemsToLoot(),
                config.lootRadius(),
                1,
                0,
                config.toggleDelayedLooting(),
                config.toggleLootMyItemsOnly()
        );
        params.setEatFoodForSpace(false);

        Rs2LootEngine.Builder builder = Rs2LootEngine.with(params)
                .withLootAction(this::lootGroundItem);

        LooterStyle style = config.looterStyle();
        if (style == LooterStyle.ITEM_LIST || style == LooterStyle.MIXED) {
            addCustomLootNames(builder, config.lootItems());
        }
        if (style == LooterStyle.GE_PRICE_RANGE || style == LooterStyle.MIXED) {
            builder.addByValue();
        }

        if (config.lootBones()) builder.addBones();
        if (config.lootAshes()) builder.addAshes();
        if (config.lootCoins()) builder.addCoins();
        if (config.lootUntradables()) builder.addUntradables();
        if (config.lootArrows()) builder.addArrows(DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS);
        if (config.lootRunes()) builder.addRunes(DEFAULT_MIN_STACK_EXCLUSIVE_RUNES);
        if (config.lootHerbs()) {
            java.util.function.Predicate<GroundItem> herbs = gi -> {
                String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
                if (n.contains("grimy")) return true;
                if (n.equals("guam leaf") || n.equals("marrentill") || n.equals("tarromin")
                        || n.equals("harralander") || n.equals("ranarr weed") || n.equals("toadflax")
                        || n.equals("irit leaf") || n.equals("avantoe") || n.equals("kwuarm")
                        || n.equals("snapdragon") || n.equals("cadantine") || n.equals("lantadyme")
                        || n.equals("dwarf weed") || n.equals("torstol")) {
                    return true;
                }
                if (n.contains("herb")) return true;
                return false;
            };
            builder.addCustom("herbs", herbs, null);
        }

        boolean looted = builder.loot();
        // The loot engine no longer eats for space itself; the inventory-full policy decides what
        // happens. If it frees a slot, give the engine another pass at the items left behind.
        for (int pass = 0; pass < LOOT_SPACE_PASSES && !looted; pass++) {
            if (!Rs2Inventory.isFull()) break;
            if (handleInventoryFull(config) != SpaceResult.FREED) break;
            looted = builder.loot();
        }

        if (config.scatterAshes()) {
            lootAndScatterInfernalAshes(config);
        }
    }

    private void lootAndScatterInfernalAshesDirect(TormentedDemonConfig config) {
        try {
            if (config.mode() == TormentedDemonConfig.MODE.FULL_AUTO
                    && Rs2Player.getHealthPercentage() <= config.healthThreshold()) {
                return;
            }

            Rs2TileItemModel ashes = Microbot.getRs2TileItemCache().query()
                    .withName("Infernal ashes")
                    .where(Rs2TileItemModel::isLootAble)
                    .within(config.lootRadius())
                    .nearest();
            if (ashes == null) return;
            if (!hasInventorySpaceForLoot(config)) return;
            Microbot.log("Looting Infernal ashes directly...");
            if (!ashes.pickup()) return;
            if (!sleepUntil(() -> Rs2Inventory.contains("Infernal ashes"), 2500)) return;

            if (config.scatterAshes()) {
                Rs2Inventory.interact("Infernal ashes", "Scatter");
                sleepUntil(() -> !Rs2Inventory.contains("Infernal ashes"), 2500);
            }
        } catch (Exception e) {
            Microbot.log("Error looting infernal ashes: " + e.getMessage());
        }
    }

    private void lootGroundItem(GroundItem groundItem) {
        if (groundItem == null) return;
        if (configHealthGuard()) return;
        Rs2GroundItem.coreLoot(groundItem);
    }

    private boolean configHealthGuard() {
        // handled by coreLoot/eat logic; keep simple
        return false;
    }

    private void addCustomLootNames(Rs2LootEngine.Builder builder, String csvNames) {
        if (csvNames == null) return;
        java.util.Set<String> needles = new java.util.HashSet<>();
        Arrays.stream(csvNames.split(","))
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .forEach(needles::add);
        if (needles.isEmpty()) return;

        java.util.function.Predicate<GroundItem> byNames = gi -> {
            final String n = gi.getName() == null ? "" : gi.getName().trim().toLowerCase();
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
            return false;
        };
        builder.addCustom("names", byNames, null);
    }

    private void lootAndScatterInfernalAshes(TormentedDemonConfig config) {
        if (config.mode() == TormentedDemonConfig.MODE.FULL_AUTO
                && Rs2Player.getHealthPercentage() <= config.healthThreshold()) {
            return;
        }

        Rs2TileItemModel ashes = Microbot.getRs2TileItemCache().query()
                .withName("Infernal ashes")
                .where(Rs2TileItemModel::isLootAble)
                .within(config.lootRadius())
                .nearest();
        if (ashes == null) return;
        if (!hasInventorySpaceForLoot(config)) return;
        if (!ashes.pickup()) return;
        if (!sleepUntil(() -> Rs2Inventory.contains("Infernal ashes"), 2500)) return;

        Rs2Inventory.interact("Infernal ashes", "Scatter");
        sleepUntil(() -> !Rs2Inventory.contains("Infernal ashes"), 2500);
    }

    private enum SpaceResult {
        /** A slot was freed, so looting can continue. */
        FREED,
        /** Nothing was freed; the item is left on the ground. */
        NOT_FREED,
        /** The configured actions asked for a stop and it has been requested. */
        STOP
    }

    /**
     * Makes room for a loot attempt when the inventory is full. The configured primary action is
     * tried first; if it cannot free a slot the fallback is tried; if that also fails the plugin
     * stops with an "Inventory full" message. Choosing <em>Do nothing</em> for either slot means
     * "leave the item on the ground" from that point on, so a Do nothing primary never stops.
     */
    private SpaceResult handleInventoryFull(TormentedDemonConfig config) {
        if (!Rs2Inventory.isFull()) {
            return SpaceResult.FREED;
        }

        TormentedDemonConfig.InventoryFullAction primary = config.inventoryFullAction();
        TormentedDemonConfig.InventoryFullAction fallback = config.inventoryFullFallback();

        if (primary == TormentedDemonConfig.InventoryFullAction.DO_NOTHING) {
            logOnceToChat("Inventory full; leaving the item on the ground.");
            return SpaceResult.NOT_FREED;
        }

        SpaceResult result = applyInventoryFullAction(config, primary);
        if (result != SpaceResult.NOT_FREED) {
            return result;
        }

        if (fallback == TormentedDemonConfig.InventoryFullAction.DO_NOTHING || fallback == primary) {
            logOnceToChat("Inventory still full; leaving the item on the ground.");
            return SpaceResult.NOT_FREED;
        }

        result = applyInventoryFullAction(config, fallback);
        if (result != SpaceResult.NOT_FREED) {
            return result;
        }

        if (!inventoryFullStopIssued) {
            inventoryFullStopIssued = true;
            logOnceToChat("Inventory full; no configured action freed a slot.");
            stopForSupplies(config, "Inventory full", true);
        }
        return SpaceResult.STOP;
    }

    private SpaceResult applyInventoryFullAction(TormentedDemonConfig config,
                                                 TormentedDemonConfig.InventoryFullAction action) {
        if (!Rs2Inventory.isFull()) {
            return SpaceResult.FREED;
        }

        switch (action) {
            case EAT_FOOD:
                return eatFoodForSpace() ? SpaceResult.FREED : SpaceResult.NOT_FREED;
            case SCATTER_ASHES:
                return freeSlotWithAshesOrBones() ? SpaceResult.FREED : SpaceResult.NOT_FREED;
            case DEMONIC_OFFERING:
                return castDemonicOfferingForSpace() ? SpaceResult.FREED : SpaceResult.NOT_FREED;
            case STOP_PLUGIN:
                if (!inventoryFullStopIssued) {
                    inventoryFullStopIssued = true;
                    stopForSupplies(config, "Inventory full", true);
                }
                return SpaceResult.STOP;
            default:
                return SpaceResult.NOT_FREED;
        }
    }

    private boolean hasInventorySpaceForLoot(TormentedDemonConfig config) {
        return !Rs2Inventory.isFull() || handleInventoryFull(config) == SpaceResult.FREED;
    }

    /**
     * Eats one food so the slot it occupied can take loot. Food is always eaten, even at full
     * health, because the point is the slot and not the healing.
     */
    private boolean eatFoodForSpace() {
        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            return false;
        }
        int before = Rs2Inventory.emptySlotCount();
        logOnceToChat("Inventory full; eating food for space.");
        if (!Rs2Player.eatAt(100)) {
            return false;
        }
        Rs2Player.waitForAnimation();
        return sleepUntil(() -> Rs2Inventory.emptySlotCount() > before, 3000);
    }

    /**
     * Scatters one ashes item or buries one bones item. Bones are used before ashes, and plain
     * ashes before infernal ashes, so the most valuable remains are kept as long as possible.
     */
    private boolean freeSlotWithAshesOrBones() {
        Rs2ItemModel target = findScatterTarget();
        if (target == null) {
            return false;
        }

        String name = target.getName();
        boolean bones = isBonesName(name);
        int before = Rs2Inventory.emptySlotCount();
        logOnceToChat("Inventory full; using " + name + " to free a slot.");
        if (!Rs2Inventory.interact(target, bones ? "Bury" : "Scatter")) {
            return false;
        }
        Rs2Player.waitForAnimation();
        return sleepUntil(() -> Rs2Inventory.emptySlotCount() > before, 3000);
    }

    private Rs2ItemModel findScatterTarget() {
        List<Rs2ItemModel> remains = Rs2Inventory.items(item -> !item.isNoted()
                        && (isBonesName(item.getName()) || isAshesName(item.getName())))
                .collect(Collectors.toList());
        if (remains.isEmpty()) {
            return null;
        }

        Rs2ItemModel otherAshes = null;
        Rs2ItemModel infernalAshes = null;
        for (Rs2ItemModel item : remains) {
            String name = normalizeItemName(item.getName());
            if (isBonesName(name)) {
                return item;
            }
            if (name.contains("infernal ashes")) {
                infernalAshes = item;
            } else if (otherAshes == null) {
                otherAshes = item;
            }
        }
        return otherAshes != null ? otherAshes : infernalAshes;
    }

    private boolean isBonesName(String name) {
        String normalized = normalizeItemName(name);
        return normalized.equals("bones") || normalized.endsWith(" bones");
    }

    private boolean isAshesName(String name) {
        return normalizeItemName(name).endsWith("ashes");
    }

    private String normalizeItemName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Casts Demonic Offering to convert up to three demonic ashes into prayer points, freeing the
     * slots they occupied. Silently ignored when the player is not on Arceuus, lacks the level or
     * runes, has no demonic ashes, or the 5.4 s cooldown has not elapsed.
     */
    private boolean castDemonicOfferingForSpace() {
        if (!canCastDemonicOffering()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDemonicOfferingAt < DEMONIC_OFFERING_COOLDOWN_MS) {
            return false;
        }
        if (!Rs2Magic.cast(Rs2Spells.DEMONIC_OFFERING)) {
            return false;
        }
        lastDemonicOfferingAt = now;
        logOnceToChat("Inventory full; casting Demonic Offering for space.");
        return sleepUntil(() -> !Rs2Inventory.isFull(), 3000);
    }

    private boolean canCastDemonicOffering() {
        return Rs2Magic.isSpellbook(Rs2Spellbook.ARCEUUS)
                && Rs2Spells.DEMONIC_OFFERING.hasRequiredLevel()
                && hasDemonicAshes()
                && Rs2Magic.hasRequiredRunes(Rs2Spells.DEMONIC_OFFERING);
    }

    private boolean hasDemonicAshes() {
        return Rs2Inventory.items(item -> !item.isNoted()
                        && DEMONIC_ASHES_NAMES.contains(normalizeItemName(item.getName())))
                .findAny()
                .isPresent();
    }

    private List<String> parseLootItems(String lootFilter) {
        if (lootFilter == null || lootFilter.trim().isEmpty()) return Collections.emptyList();
        return Arrays.stream(lootFilter.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private void evaluateAndConsumePotions(TormentedDemonConfig config) {
        int threshold = config.boostedStatsThreshold();

        if (!isCombatPotionActive(config.combatPotionType(), threshold)) {
            consumeCombatPotion(config.combatPotionType());
        }

        if (!isRangingPotionActive(config.rangingPotionType(), threshold)) {
            consumeRangingPotion(config.rangingPotionType());
        }
    }

    private boolean isCombatPotionActive(TormentedDemonConfig.CombatPotionType combatPotionType, int threshold) {
        switch (combatPotionType) {
            case SUPER_COMBAT:
                return Rs2Player.hasAttackActive(threshold) && Rs2Player.hasStrengthActive(threshold);
            case DIVINE_SUPER_COMBAT:
                return Rs2Player.hasDivineCombatActive();
            default:
                return true;
        }
    }

    private boolean isRangingPotionActive(TormentedDemonConfig.RangingPotionType rangingPotionType, int threshold) {
        switch (rangingPotionType) {
            case RANGING:
                return Rs2Player.hasRangingPotionActive(threshold);
            case DIVINE_RANGING:
                return Rs2Player.hasDivineRangedActive();
            case BASTION:
                return Rs2Player.hasDivineBastionActive();
            default:
                return true;
        }
    }

    private void consumeCombatPotion(TormentedDemonConfig.CombatPotionType combatPotionType) {
        String potion;
        switch (combatPotionType) {
            case SUPER_COMBAT:
                potion = "super combat";
                break;
            case DIVINE_SUPER_COMBAT:
                potion = "divine super combat";
                break;
            default:
                return;
        }
        consumePotion(potion);
    }

    private void consumeRangingPotion(TormentedDemonConfig.RangingPotionType rangingPotionType) {
        String potion;
        switch (rangingPotionType) {
            case RANGING:
                potion = "ranging potion";
                break;
            case DIVINE_RANGING:
                potion = "divine ranging potion";
                break;
            case BASTION:
                potion = "bastion potion";
                break;
            default:
                return;
        }
        consumePotion(potion);
    }

    private void consumePotion(String keyword) {
        String expectedName = potionBaseName(keyword);
        Rs2Inventory.getPotions().stream()
                .filter(potion -> potion.getName() != null)
                .filter(potion -> normalizePotionName(potion.getName()).equals(expectedName))
                .findFirst()
                .ifPresent(potion -> {
                    Rs2Inventory.interact(potion, "Drink");
                    logOnceToChat("Drinking potion: " + potion.getName());
                });
    }

    private String potionBaseName(String keyword) {
        String name = keyword.toLowerCase().trim();
        return name.endsWith(" potion") ? name : name + " potion";
    }

    private static String normalizePotionName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
    }

    /** Average kill time across this session, 0 when no kill has been timed yet. */
    public static long getAverageKillTimeMs() {
        return timedKills == 0 ? 0 : totalKillTimeMs / timedKills;
    }

    /** Average loot value per kill on the current trip, 0 when the trip has no kills yet. */
    public static long getAverageLootPerKill() {
        return tripKills == 0 ? 0 : tripLootValue / tripKills;
    }

    /** Total loot value gathered since the current trip started. */
    public static long getTripLootValue() {
        return tripLootValue;
    }

    /** Readable form of {@link #BOT_STATUS} for the overlay. */
    public static String getStatusDisplay() {
        switch (BOT_STATUS) {
            case BANKING:
                return "Banking";
            case RETREAT:
                return "Retreating";
            case TRAVEL_TO_TORMENTED:
                return "Travelling";
            case FIGHTING:
                return "Fighting";
            default:
                return String.valueOf(BOT_STATUS);
        }
    }

    private void resetTripStats() {
        tripKills = 0;
        tripLootValue = 0;
    }

    /**
     * Clears every overlay statistic: kill count, kill times, and the current trip's loot. Called
     * by the "Reset Statistics" button in the Overlay config section.
     */
    public static void resetStatistics() {
        killCount = 0;
        lastKillTimeMs = 0;
        totalKillTimeMs = 0;
        timedKills = 0;
        tripKills = 0;
        tripLootValue = 0;
    }

    /**
     * Records the duration of a finished fight. Fights started before the plugin (or ones that
     * timed out waiting for a respawn) are skipped so the averages stay meaningful.
     */
    private void recordKillTime(long fightStart) {
        if (fightStart <= 0) {
            return;
        }
        long duration = System.currentTimeMillis() - fightStart;
        if (duration <= 0 || duration > MAX_TRACKED_KILL_TIME_MS) {
            return;
        }
        lastKillTimeMs = duration;
        totalKillTimeMs += duration;
        timedKills++;
        tripKills++;
    }

    private Map<Integer, Integer> snapshotInventoryCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        Rs2Inventory.items().forEach(item -> counts.merge(item.getId(), item.getQuantity(), Integer::sum));
        return counts;
    }

    /**
     * Adds the value of everything that entered the inventory since {@code before} to the trip
     * total. Items that were consumed or equipped during looting are ignored, so eating food or
     * scattering ashes cannot lower the recorded loot.
     */
    private void recordLootGained(Map<Integer, Integer> before) {
        try {
            List<Rs2ItemModel> items = Rs2Inventory.items().collect(Collectors.toList());
            long gained = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                long total = 0;
                for (Rs2ItemModel item : items) {
                    int delta = item.getQuantity() - before.getOrDefault(item.getId(), 0);
                    if (delta <= 0) {
                        continue;
                    }
                    total += Microbot.getItemManager().getItemPrice(item.getId()) * delta;
                }
                return total;
            }).orElse(0L);
            if (gained > 0) {
                tripLootValue += gained;
            }
        } catch (Exception ex) {
            logOnceToChat("Unable to total loot value: " + ex.getMessage());
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
        releasePluginSpecialAttack(null);
        disableAllPrayers();
        if (swappedForPunish || swappedForSpec) {
            if (lastGearList != null && !lastGearList.isEmpty()) {
                try {
                    equipGear(lastGearList);
                } catch (Exception ignored) {
                }
            }
            swappedForPunish = false;
            swappedForSpec = false;
        }
        BOT_STATUS = State.BANKING;
        travelStep = TravelStep.LOCATION_ONE;
        bankingStep = BankingStep.DRINK;
        currentTarget = null;
        currentTargetIndex = -1;
        currentOverheadIcon = null;
        currentCombatStyle = null;
        killCount = 0;
        lootAttempted = false;
        lastMarkCastAt = 0;
        lastMarkAttemptAt = 0;
        lastThrallCastAttempt = 0;
        lastWeaponCheckAt = 0;
        lastSpecialAttackAttemptAt = 0;
        specialAttackEnabledByPlugin = false;
        lastSpecialAttackDebug = "";
        warnedArceuus = false;
        lastChatMessage = "";
        dodgeUntil = 0;
        bombWalkLockUntil = 0;
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        logOnceToChat("Shutting down Tormented Demon script");
    }
}
