package net.runelite.client.plugins.microbot.tormenteddemons;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
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
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TormentedDemonScript extends Script {

    public static int killCount = 0;
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
    private String lastChatMessage = "";
    public volatile long dodgeUntil = 0;

    private static final WorldPoint SAFE_LOCATION = new WorldPoint(3150, 3634, 0);
    private static final int FEROX_POOL_ID = 39651;
    private static final long MARK_REFRESH_MARGIN_MS = 10_000L;
    private static final long MARK_RETRY_MS = 5_000L;
    private static final long THRALL_RETRY_MS = 3_000L;
    private static final long WEAPON_CHECK_RETRY_MS = 2_000L;
    private static final long SPECIAL_ATTACK_RETRY_MS = 600L;
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_ARROWS = 9;
    private static final int DEFAULT_MIN_STACK_EXCLUSIVE_RUNES = 1;

    private enum State {BANKING, RETREAT, TRAVEL_TO_TORMENTED, FIGHTING}

    public static State BOT_STATUS = State.BANKING;

    private enum TravelStep {LOCATION_ONE, CLIMB_FIRST_STAIRS, CLIMB_SECOND_STAIRS, CLIMB_THROUGH, LOCATION_THREE}

    private TravelStep travelStep = TravelStep.LOCATION_ONE;

    private enum BankingStep {DRINK, BANK, LOAD_INVENTORY}

    private BankingStep bankingStep = BankingStep.DRINK;

    private enum CombatStyle {MELEE, RANGE, MAGIC}

    public boolean run(TormentedDemonConfig config) {
        if (config.mode() == TormentedDemonConfig.MODE.FULL_AUTO) {
            BOT_STATUS = State.BANKING;
        } else if (config.mode() == TormentedDemonConfig.MODE.COMBAT_ONLY) {
            BOT_STATUS = State.FIGHTING;
        }

        bankingStep = BankingStep.DRINK;
        travelStep = TravelStep.LOCATION_ONE;
        dodgeUntil = 0;
        lastWeaponCheckAt = 0;
        lastSpecialAttackAttemptAt = 0;
        specialAttackEnabledByPlugin = false;
        lastSpecialAttackDebug = "";
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (System.currentTimeMillis() < dodgeUntil) return;

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
        WorldPoint targetLocationOne = new WorldPoint(4062, 4558, 0);
        WorldPoint targetFinalLocation = new WorldPoint(4073, 4432, 0);

        switch (travelStep) {
            case LOCATION_ONE:
                if (isWithin(targetLocationOne, 20)) {
                    travelStep = TravelStep.CLIMB_FIRST_STAIRS;
                    break;
                }
                Microbot.status = "Teleporting to Guthixian temple...";
                if (Rs2Inventory.interact("Guthixian temple teleport", "Teleport")) {
                    Rs2Player.waitForAnimation();
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    if (sleepUntil(() -> isWithin(targetLocationOne, 20), 12000)) {
                        travelStep = TravelStep.CLIMB_FIRST_STAIRS;
                    } else {
                        logOnceToChat("Guthixian temple teleport did not complete; retrying.");
                    }
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
                bankingStep = BankingStep.LOAD_INVENTORY;
                break;
            }

            case LOAD_INVENTORY: {
                Microbot.status = "Loading inventory and equipment setup...";
                Rs2InventorySetup inventorySetup = new Rs2InventorySetup("tormented", mainScheduledFuture);
                boolean equipmentLoaded = inventorySetup.loadEquipment();
                boolean inventoryLoaded = inventorySetup.loadInventory();

                if (equipmentLoaded && inventoryLoaded) {
                    Rs2Bank.closeBank();
                    bankingStep = BankingStep.DRINK;
                    BOT_STATUS = State.TRAVEL_TO_TORMENTED;
                } else {
                    logOnceToChat("Failed to load inventory/equipment setup 'tormented'; stopping.");
                    Microbot.stopPlugin(TormentedDemonPlugin.class);
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

    private void handleFighting(TormentedDemonConfig config) {
        if (currentTarget == null || isTargetDead(currentTarget)) {
            releasePluginSpecialAttack(config);
            disableAllPrayers();

            if (!lootAttempted) {
                if (currentTarget != null && isTargetDead(currentTarget)) {
                    killCount++;
                    if (config.lootDelay() > 0) {
                        sleep(config.lootDelay());
                    }
                }
                attemptLooting(config);
                lootAttempted = true;
            }

            currentTarget = findNewTarget();
            if (currentTarget == null) {
                currentTargetIndex = -1;
                logOnceToChat("No Tormented Demon found; waiting...");
                return;
            }

            currentTargetIndex = getTargetIndex(currentTarget);
            currentOverheadIcon = null;
            currentCombatStyle = null;
            lastWeaponCheckAt = 0;
            lootAttempted = false;
        }

        HeadIcon overheadIcon = getHeadIcon(currentTarget);
        if (overheadIcon != null && overheadIcon != currentOverheadIcon) {
            currentOverheadIcon = overheadIcon;
            CombatStyle newStyle = config.autoGearSwitch() ? switchGear(config, overheadIcon) : detectStyle(config);
            updateCombatStyle(newStyle);
        } else if (System.currentTimeMillis() >= gearSwitchGraceUntil) {
            updateCombatStyle(detectStyle(config));
            HeadIcon activeIcon = overheadIcon != null ? overheadIcon : currentOverheadIcon;
            updateCombatStyle(ensureCorrectWeapon(config, activeIcon));
        }

        evaluateAndConsumePotions(config);

        if (config.mode() == TormentedDemonConfig.MODE.FULL_AUTO && shouldRetreat(config)) {
            currentTarget = null;
            currentTargetIndex = -1;
            currentOverheadIcon = null;
            currentCombatStyle = null;
            BOT_STATUS = State.RETREAT;
            return;
        }

        Rs2Player.eatAt(config.minEatPercent());
        if (config.enableDefensivePrayer() || config.enableOffensivePrayer()) {
            Rs2Player.drinkPrayerPotionAt(prayerThreshold(config));
        }

        if (currentCombatStyle == CombatStyle.MAGIC && handleMagicUtility(config)) {
            return;
        }

        if (currentTarget == null || isTargetDead(currentTarget)) {
            return;
        }

        if (config.useSpecialAttack()) {
            handleSpecialAttack(config);
        } else {
            releasePluginSpecialAttack(config);
        }

        if (getInteractingTargetIndex() != currentTargetIndex) {
            if (!currentTarget.click("attack")) {
                logOnceToChat("Attack failed for target.");
                currentTarget = null;
                currentTargetIndex = -1;
                currentOverheadIcon = null;
                currentCombatStyle = null;
                lastWeaponCheckAt = 0;
                return;
            }

            Rs2Player.waitForAnimation();
            sleepUntil(() -> getInteractingTargetIndex() == currentTargetIndex, 3000);
        }

        if (config.enableOffensivePrayer()) {
            if (currentCombatStyle != null) {
                activateOffensivePrayer(config, currentCombatStyle);
            }
        } else {
            disableOffensivePrayer();
        }
    }

    private void updateCombatStyle(CombatStyle newStyle) {
        if (newStyle == null || newStyle == currentCombatStyle) {
            return;
        }
        currentCombatStyle = newStyle;
        logOnceToChat("Combat style detected: " + newStyle + ".");
        disableOffensivePrayer();
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
            gearSwitchGraceUntil = System.currentTimeMillis() + 1500L;
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
        if (System.currentTimeMillis() < gearSwitchGraceUntil) {
            return null;
        }

        List<CombatStyle> candidates = counterStyles(config, combatNpcHeadIcon);
        if (candidates.isEmpty()) {
            return null;
        }

        CombatStyle actual = detectAttackStyle(config);
        String weaponName = getEquippedWeaponName();
        boolean styleCorrect = actual != null && candidates.contains(actual);
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
        String current = actual != null ? actual.name() : "unknown";
        logOnceToChat("Weapon check failed: " + equipped + " (" + current + ") does not counter "
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
        int requiredEnergy = getSpecialAttackEnergyRequired(weaponName);
        int energy = Rs2Combat.getSpecEnergy();

        if (requiredEnergy < 0) {
            releasePluginSpecialAttack(config);
            debugSpecialAttack(config, "unsupported weapon=" + weaponName + " energy=" + energy);
            return;
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
        if (config == null || !config.debugPrayer()) {
            return;
        }
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

        try {
            String attackStyle = Rs2Combat.getWeaponAttackStyle();
            if (attackStyle != null) {
                String style = attackStyle.toLowerCase();
                if (style.equals("magic") || style.equals("cast") || style.equals("casting")) {
                    return config.useMagicStyle() ? CombatStyle.MAGIC : null;
                }
                if (style.equals("ranged") || style.equals("ranging") || style.equals("rapid")) {
                    return config.useRangeStyle() ? CombatStyle.RANGE : null;
                }
                if (isMeleeAttackStyle(style)) {
                    return config.useMeleeStyle() ? CombatStyle.MELEE : null;
                }
            }

            if (config.useMagicStyle() && Rs2Magic.getCurrentAutoCastSpell() != null) {
                return CombatStyle.MAGIC;
            }
        } catch (Exception ignored) {
        }
        return null;
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
        for (String item : gear) {
            if (!Rs2Equipment.isWearing(item)) return false;
        }
        return true;
    }

    private void equipGear(List<String> gear) {
        for (String item : gear) {
            if (Rs2Equipment.isWearing(item)) continue;
            if (!Rs2Inventory.wield(item)) {
                logOnceToChat("Could not equip " + item + ".");
            }
            sleep(50);
        }
    }

    private void activateOffensivePrayer(TormentedDemonConfig config, CombatStyle style) {
        Rs2PrayerEnum newOffensivePrayer;
        switch (style) {
            case MAGIC: {
                TormentedDemonConfig.MagicOffensivePrayer option = config.magicOffensivePrayer();
                newOffensivePrayer = option.isAuto() ? Rs2Prayer.getBestMagePrayer() : option.getPrayer();
                break;
            }
            case RANGE: {
                TormentedDemonConfig.RangedOffensivePrayer option = config.rangeOffensivePrayer();
                newOffensivePrayer = option.isAuto() ? Rs2Prayer.getBestRangePrayer() : option.getPrayer();
                break;
            }
            case MELEE: {
                TormentedDemonConfig.MeleeOffensivePrayer option = config.meleeOffensivePrayer();
                newOffensivePrayer = option.isAuto() ? Rs2Prayer.getBestMeleePrayer() : option.getPrayer();
                break;
            }
            default:
                return;
        }

        if (newOffensivePrayer == null) {
            disableOffensivePrayer();
            return;
        }

        if (newOffensivePrayer != currentOffensivePrayer) {
            logOnceToChat("Changing offensive prayer to " + newOffensivePrayer.getName() + ".");
            switchOffensivePrayer(newOffensivePrayer);
            sleep(100);
        }
    }

    private void switchOffensivePrayer(Rs2PrayerEnum newOffensivePrayer) {
        if (currentOffensivePrayer != null) {
            Rs2Prayer.toggle(currentOffensivePrayer, false);
        }
        boolean activated = Rs2Prayer.toggle(newOffensivePrayer, true);
        if (activated) {
            currentOffensivePrayer = newOffensivePrayer;
        } else {
            currentOffensivePrayer = null;
            logOnceToChat("Could not activate " + newOffensivePrayer.getName()
                    + "; check prayer level, unlocks, and prayer points.");
        }
    }

    private void disableOffensivePrayer() {
        if (currentOffensivePrayer != null) {
            Rs2Prayer.toggle(currentOffensivePrayer, false);
            currentOffensivePrayer = null;
        }
    }

    private int prayerThreshold(TormentedDemonConfig config) {
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        return Math.max(1, (int) Math.ceil(maxPrayer * (config.minPrayerPercent() / 100.0)));
    }

    private boolean shouldRetreat(TormentedDemonConfig config) {
        double healthPercentage = Rs2Player.getHealthPercentage();
        int currentPrayer = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
        int prayerThreshold = prayerThreshold(config);
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPrayerPotions = Rs2Inventory.items()
                .noneMatch(item -> item != null && item.getName() != null
                        && item.getName().toLowerCase().contains("prayer potion"));

        if (healthPercentage <= config.healthThreshold()) return true;
        if (noFood && healthPercentage <= config.minEatPercent()) return true;
        boolean usesPrayer = config.enableDefensivePrayer() || config.enableOffensivePrayer();
        return usesPrayer && noPrayerPotions && currentPrayer <= prayerThreshold;
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        currentOffensivePrayer = null;
    }

    private void attemptLooting(TormentedDemonConfig config) {
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
        params.setEatFoodForSpace(config.mode() == TormentedDemonConfig.MODE.FULL_AUTO);

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

        builder.loot();

        if (config.scatterAshes()) {
            lootAndScatterInfernalAshes(config);
        }
    }

    private void lootAndScatterInfernalAshesDirect(TormentedDemonConfig config) {
        try {
            if (Rs2Inventory.isFull()) return;
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
        if (Rs2Inventory.isFull()) return;
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
        if (!ashes.pickup()) return;
        if (!sleepUntil(() -> Rs2Inventory.contains("Infernal ashes"), 2500)) return;

        Rs2Inventory.interact("Infernal ashes", "Scatter");
        sleepUntil(() -> !Rs2Inventory.contains("Infernal ashes"), 2500);
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

    private String normalizePotionName(String name) {
        return name.toLowerCase().replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim();
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
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        logOnceToChat("Shutting down Tormented Demon script");
    }
}
