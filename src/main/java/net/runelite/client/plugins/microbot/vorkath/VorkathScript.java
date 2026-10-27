/**
 * Credits to Jrod7938
 */

package net.runelite.client.plugins.microbot.vorkath;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;


enum State {
    BANKING,
    TELEPORT_TO_RELLEKKA,
    WALK_TO_VORKATH_ISLAND,
    WALK_TO_VORKATH,
    PREPARE_FIGHT,
    FIGHT_VORKATH,
    ZOMBIE_SPAWN,
    ACID,
    LOOT_ITEMS,
    TELEPORT_AWAY,
    DEAD_WALK,
    SELLING_ITEMS
}

public class VorkathScript extends Script {

    private final VorkathConfig config;
    @Getter
    public final int acidProjectileId = 1483;
    final String ZOMBIFIED_SPAWN = "Zombified Spawn";
    private final int whiteProjectileId = 395;
    private final int redProjectileId = 1481;
    private final int acidRedProjectileId = 1482;
    private static final int CRUMBLE_UNDEAD_PROJECTILE_ID = 146;
    private static final int[] SLAYER_STAFF_IDS = {ItemID.SLAYER_STAFF, ItemID.SLAYER_STAFF_ENCHANTED};
    @Getter
    private final HashSet<WorldPoint> acidPools = new HashSet<>();
    public int vorkathSessionKills = 0;
    public int tempVorkathKills = 0;
    public int kcPerTrip = 0;
    State state = State.BANKING;
    Rs2NpcModel vorkath;
    boolean hasEquipment = false;
    boolean hasInventory = false;
    boolean init = true;
    String primaryBolts = "";
    Rs2InventorySetup rs2InventorySetup;

    /**
     * Projectile ids currently in flight, refreshed once per loop iteration so that reaction logic
     * never has to walk the live projectile deque more than once per tick.
     */
    private final Set<Integer> activeProjectileIds = new HashSet<>();
    private long lastAttackClickAt = 0;
    private long lastRedBallMoveAt = 0;
    private long lastAcidMoveAt = 0;
    private long lastZombieCastAt = 0;
    private long lastPokeAt = 0;
    private long lastCenterWalkAt = 0;
    private WorldPoint redBallTarget = null;

    /** Set once per trip after we confirm the Zombified Spawn can be handled. */
    private boolean crumbleVerified = false;
    /** True while the Slayer's staff is equipped for the spawn and the primary weapon still needs restoring. */
    private boolean slayerStaffEquippedForSpawn = false;
    /** Weapon id to re-equip once the spawn is dealt with. */
    private int primaryWeaponId = -1;
    private long lastFatalMessageAt = 0;
    /** True once Vorkath has been poked for the current kill (poking and walking must not share a tick). */
    private boolean pokedVorkath = false;
    /** Set from the client thread when a venom hitsplat lands on the player. */
    private volatile boolean venomApplied = false;
    /** Last state logged, used to emit single-line transition logs when verbose logging is enabled. */
    private State lastLoggedState = null;
    /** True while the acid barrage is active, used to log the phase boundary exactly once. */
    private boolean acidPhaseActive = false;
    /** Tile the acid-phase walk is currently heading towards, used to chain steps without stopping. */
    private WorldPoint acidWalkTarget = null;
    /** Latched once the freeze special begins; holds the Slayer's staff for the whole special. */
    private boolean freezePhaseActive = false;
    /** True once Crumble Undead has been cast (airborne) during the current freeze special. */
    private boolean crumbleCastThisFreeze = false;
    private long lastFreezeSignalAt = 0;
    private long lastStaffEquipAt = 0;
    /** Latched once Vorkath drops below the ruby cutoff so bolts are not swapped back mid-kill. */
    private boolean usingDiamondBolts = false;

    /**
     * Logs a message prefixed with the plugin name when the user has enabled verbose logging.
     */
    private void debug(String message) {
        if (config.verboseLogging()) {
            log("[Vorkath] " + message);
        }
    }

    /**
     * The states the fight itself cycles through every tick; transitions between them are not
     * interesting enough to log and would otherwise flood the log during the acid phase.
     */
    private static boolean isCombatSubState(State s) {
        return s == State.FIGHT_VORKATH || s == State.ACID || s == State.ZOMBIE_SPAWN;
    }

    private static void walkToCenter() {
        Rs2Walker.walkFastLocal(
                LocalPoint.fromScene(48, 58, Microbot.getClient().getTopLevelWorldView().getScene())
        );
    }

    private static void drinkPrayer() {
        if ((Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / Microbot.getClient().getRealSkillLevel(Skill.PRAYER) < Rs2Random.between(25, 30)) {
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
        }
    }

    private void calculateState() {
        // Only an alive Vorkath means the fight is ongoing. A dead/despawning Vorkath still lingers in
        // the cache for its death animation; treating that as a live fight caused the loot phase to
        // bounce straight back into FIGHT_VORKATH and stall on the ground items.
        Rs2NpcModel liveVorkath = Microbot.getRs2NpcCache().query().withId(NpcID.VORKATH).first();
        if (liveVorkath != null && !liveVorkath.isDead()) {
            state = State.FIGHT_VORKATH;
            return;
        }
        if (Microbot.getRs2NpcCache().query().withId(NpcID.VORKATH_SLEEPING).first() != null) {
            state = State.PREPARE_FIGHT;
            return;
        }
        if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.UNGAEL_CRATER_ENTRANCE).first() != null) {
            state = State.WALK_TO_VORKATH;
            return;
        }
        if (isCloseToRelleka()) {
            state = State.WALK_TO_VORKATH_ISLAND;
            return;
        }
        if (Microbot.getRs2NpcCache().query().withId(NpcID.TORFINN_COLLECT_UNGAEL).first() != null) {
            state = State.WALK_TO_VORKATH;
        }
    }

    @Inject
    public VorkathScript(VorkathConfig config) {
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Microbot.pauseAllScripts.compareAndSet(true, false);
        init = true;
        state = State.BANKING;
        hasEquipment = false;
        hasInventory = false;
        tempVorkathKills = config.SellItemsAtXKills();
        crumbleVerified = false;
        slayerStaffEquippedForSpawn = false;
        primaryWeaponId = -1;
        pokedVorkath = false;
        venomApplied = false;
        acidPhaseActive = false;
        acidWalkTarget = null;
        freezePhaseActive = false;
        crumbleCastThisFreeze = false;
        usingDiamondBolts = false;
        Microbot.getSpecialAttackConfigs().setSpecialAttack(true);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                refreshProjectiles();
                if (Rs2AntibanSettings.naturalMouse) {
                    Rs2AntibanSettings.naturalMouse = false;
                    log("Woox walk is not compatible with natural mouse.");
                }

                if (init) {
                    rs2InventorySetup = new Rs2InventorySetup("vorkath", mainScheduledFuture);
                    if (!rs2InventorySetup.hasSpellBook()) {
                        Microbot.showMessage("Your spellbook is not matching the inventory setup.");
                        sleep(10000);
                        return;
                    }
                    calculateState();
                    primaryBolts = Rs2Equipment.get(EquipmentInventorySlot.AMMO) != null ? Rs2Equipment.get(EquipmentInventorySlot.AMMO).getName() : "";
                }

                if (state == State.FIGHT_VORKATH && Rs2Equipment.get(EquipmentInventorySlot.AMMO) == null) {
                    leaveVorkath();
                }

                if (state != lastLoggedState && !(isCombatSubState(state) && isCombatSubState(lastLoggedState))) {
                    debug("State: " + lastLoggedState + " -> " + state);
                    lastLoggedState = state;
                }

                switch (state) {
                    case BANKING:
                        kcPerTrip = 0;
                        crumbleVerified = false;
                        pokedVorkath = false;
                        venomApplied = false;
                        acidPhaseActive = false;
                        acidWalkTarget = null;
                        freezePhaseActive = false;
                        crumbleCastThisFreeze = false;
                        usingDiamondBolts = false;
                        if (checkSellingItems(config)) return;
                        if (!init && Rs2Equipment.get(EquipmentInventorySlot.AMMO) == null) {
                            Microbot.showMessage("Out of ammo!");
                            sleep(5000);
                            return;
                        }
                        if (isCloseToRelleka() && Rs2Inventory.count() >= 27) {
                            state = State.WALK_TO_VORKATH_ISLAND;
                        }
                        hasEquipment = rs2InventorySetup.doesEquipmentMatch();
                        hasInventory = rs2InventorySetup.doesInventoryMatch();
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.walkToBankAndUseBank();
                        }
                        if (!hasEquipment) {
                            hasEquipment = rs2InventorySetup.loadEquipment();
                        }
                        if (!hasInventory && rs2InventorySetup.doesEquipmentMatch()) {
                            hasInventory = rs2InventorySetup.loadInventory();
                            sleep(1000);
                        }
                        if (hasEquipment && hasInventory) {
                            healAndDrinkPrayerPotion();
                            if (hasEquipment && hasInventory) {
                                state = State.TELEPORT_TO_RELLEKKA;
                            }
                        }
                        break;
                    case TELEPORT_TO_RELLEKKA:
                        if (!config.pohInRellekka() && !Rs2Inventory.hasItem("Rellekka teleport")) {
                            state = State.BANKING;
                            return;
                        }
                        if (config.pohInRellekka() && (!Rs2Magic.hasRequiredRunes(Rs2Spells.TELEPORT_TO_HOUSE) && !Rs2Inventory.hasItem("Teleport to house"))) {
                            state = State.BANKING;
                            return;
                        }
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                        }
                        if (!isCloseToRelleka()) {

                            if (config.pohInRellekka()) {
                                teleToPoh();
                                sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(4525).first() != null);
                                Rs2TileObjectModel pohPortal = Microbot.getRs2TileObjectCache().query().withId(4525).first();
                                if (pohPortal != null) {
                                    pohPortal.click("Enter");
                                }
                                sleepUntil(this::isCloseToRelleka);

                            } else {
                                Rs2Inventory.interact("Rellekka teleport", "break");
                                sleepUntil(this::isCloseToRelleka);
                            }
                        }
                        if (isCloseToRelleka()) {
                            state = State.WALK_TO_VORKATH_ISLAND;
                        }
                        break;
                    case WALK_TO_VORKATH_ISLAND:
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Walker.walkTo(new WorldPoint(2640, 3693, 0));
                        Rs2NpcModel torfin = Microbot.getRs2NpcCache().query().withId(NpcID.TORFINN_COLLECT_RELLEKKA).first();
                        if (torfin != null) {
                            torfin.click("Ungael");
                            sleepUntil(() -> Microbot.getRs2NpcCache().query().withId(NpcID.TORFINN_COLLECT_UNGAEL).first() != null);
                        }
                        if (Microbot.getRs2NpcCache().query().withId(NpcID.TORFINN_COLLECT_UNGAEL).first() != null) {
                            state = State.WALK_TO_VORKATH;
                        }
                        break;
                    case WALK_TO_VORKATH:
                        kcPerTrip = 0;
                        Rs2Walker.walkTo(new WorldPoint(2272, 4052, 0));
                        Rs2TileObjectModel iceChunks = Microbot.getRs2TileObjectCache().query().withId(ObjectID.UNGAEL_CRATER_ENTRANCE).first();
                        if (iceChunks != null) {
                            iceChunks.click("Climb-over");
                            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(ObjectID.UNGAEL_CRATER_ENTRANCE).first() == null);
                        }
                        if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.UNGAEL_CRATER_ENTRANCE).first() == null) {
                            state = State.PREPARE_FIGHT;
                        }
                        break;
                    case PREPARE_FIGHT:
                        Rs2Player.toggleRunEnergy(false);

                        // Verify we can actually kill the Zombified Spawn before committing to the fight.
                        if (!ensureCrumbleUndeadReady()) break;

                        // Drink the opening dose of every potion; returns false while a dose is still pending.
                        if (!drinkPotions()) break;

                        // Trim surplus food down to the configured cap so there is room for loot.
                        if (config.foodToKeep() > 0 && Rs2Inventory.getInventoryFood().size() > config.foodToKeep()) {
                            Rs2Player.useFood();
                            Rs2Player.waitForAnimation();
                            break;
                        }

                        Rs2NpcModel awakeVorkath = Microbot.getRs2NpcCache().query().withId(NpcID.VORKATH).first();
                        Rs2NpcModel sleepingVorkath = Microbot.getRs2NpcCache().query().withId(NpcID.VORKATH_SLEEPING).first();

                        if (awakeVorkath == null && sleepingVorkath != null) {
                            // Poke to wake Vorkath. A walk click issued in the same tick cancels the poke,
                            // so we never reposition while it is still asleep. Re-poke only as a fallback.
                            if (!pokedVorkath || System.currentTimeMillis() - lastPokeAt >= 3000) {
                                sleepingVorkath.click("Poke");
                                lastPokeAt = System.currentTimeMillis();
                                pokedVorkath = true;
                                debug("Poked sleeping Vorkath");
                            }
                            handlePrayer();
                            break;
                        }

                        // Vorkath is awake: take position, then start the fight.
                        if (walkToCenterIfNeeded()) {
                            handlePrayer();
                            break;
                        }
                        handlePrayer();
                        if (awakeVorkath != null) {
                            if (doesProjectileExistById(redProjectileId)) {
                                handleRedBall();
                            }
                            state = State.FIGHT_VORKATH;
                        }
                        break;
                    case FIGHT_VORKATH:
                    case ZOMBIE_SPAWN:
                    case ACID:
                        handleCombat();
                        break;
                    case LOOT_ITEMS:
                        togglePrayer(false);

                        // No room for drops: eat surplus food to free a slot (when enabled), otherwise
                        // move on so the plugin does not stall on a full inventory.
                        if (Rs2Inventory.isFull()) {
                            if (config.eatFoodForSpace() && !Rs2Inventory.getInventoryFood().isEmpty()) {
                                Rs2Player.useFood();
                                Rs2Player.waitForAnimation();
                            } else {
                                state = State.PREPARE_FIGHT;
                            }
                            break;
                        }

                        LootingParameters valueParams = new LootingParameters(
                                config.priceOfItemsToLoot(),
                                Integer.MAX_VALUE,
                                20,
                                1,
                                0,
                                false,
                                false
                        );

                        var vorkathHead = Microbot.getRs2TileItemCache().query().withName("Vorkath's head").within(20).first();
                        if (vorkathHead != null) {
                            vorkathHead.pickup();
                        }
                        Rs2GroundItem.lootItemBasedOnValue(valueParams);
                        int foodInventorySize = Rs2Inventory.getInventoryFood().size();
                        boolean hasVenom = Rs2Inventory.hasItem("venom");
                        boolean hasSuperAntifire = Rs2Inventory.hasItem("super antifire");
                        boolean hasPrayerPotion = Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new));
                        boolean hasRangePotion = Rs2Inventory.hasItem(Rs2Potion.getRangePotionsVariants().toArray(String[]::new));
                        sleep(600, 2000);
                        if (!Rs2GroundItem.isItemBasedOnValueOnGround(config.priceOfItemsToLoot(), 20)
                                && Microbot.getRs2TileItemCache().query().withName("Vorkath's head").within(20).first() == null) {
                            if (config.KillsPerTrip() > 0 && kcPerTrip >= config.KillsPerTrip()) {
                                leaveVorkath();
                            }
                            if (foodInventorySize < 3 || !hasVenom || !hasSuperAntifire || !hasRangePotion || (!hasPrayerPotion && !Rs2Player.hasPrayerPoints())) {
                                leaveVorkath();
                            } else {
                                calculateState();
                            }

                        }
                        break;
                    case TELEPORT_AWAY:
                        togglePrayer(false);
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Inventory.wield(primaryBolts);
                        boolean reachedDestination = Rs2Bank.walkToBank();
                        if (reachedDestination) {
                            healAndDrinkPrayerPotion();
                            state = State.BANKING;
                        }
                        break;
                    case DEAD_WALK:
                        if (isCloseToRelleka()) {
                            Rs2Walker.walkTo(new WorldPoint(2640, 3693, 0));
                            torfin = Microbot.getRs2NpcCache().query().withId(NpcID.TORFINN_COLLECT_RELLEKKA).first();
                            if (torfin != null) {
                                torfin.click("Collect");
                                sleepUntil(() -> Rs2Widget.hasWidget("Retrieval Service"), 1500);
                                if (Rs2Widget.hasWidget("I'm afraid I don't have anything")) { // this means we looted all our stuff
                                    leaveVorkath();
                                    return;
                                }
                                final int invSize = Rs2Inventory.count();
                                Rs2Widget.clickWidget(39452678);
                                sleep(600);
                                Rs2Widget.clickWidget(39452678);
                                sleepUntil(() -> Rs2Inventory.count() != invSize);
                                boolean isWearingOriginalEquipment = rs2InventorySetup.wearEquipment();
                                if (!isWearingOriginalEquipment) {
                                    int finalInvSize = Rs2Inventory.count();
                                    Rs2Widget.clickWidget(39452678);
                                    sleepUntil(() -> Rs2Inventory.count() != finalInvSize);
                                    rs2InventorySetup.wearEquipment();
                                }
                            }
                        } else {
                            togglePrayer(false);
                            if (Rs2Inventory.hasItem("Rellekka teleport")) {
                                Rs2Inventory.interact("Rellekka teleport", "break");
                                Rs2Player.waitForAnimation();
                                return;
                            }
                            Rs2Bank.walkToBank();
                            Rs2Bank.openBank();
                            Rs2Bank.withdrawItem("Rellekka teleport");
                            sleep(150, 400);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> Rs2Inventory.hasItem("Rellekka teleport"), 1000);
                        }
                        break;
                    case SELLING_ITEMS:
                        boolean soldAllItems = Rs2GrandExchange.sellLoot("vorkath", Arrays.stream(config.ItemsToNotSell().split(",")).collect(Collectors.toList()));
                        if (soldAllItems) {
                            state = State.BANKING;
                        }
                        break;
                }

                init = false;

            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                } else {
                    Microbot.logStackTrace("Vorkath", ex);
                }
            }
        }, 0, 80, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Checks if we have killed x amount of vorkaths based on a config to sell items
     *
     * @param config
     * @return true if we need to sell items
     */
    private boolean checkSellingItems(VorkathConfig config) {
        int sellAt = config.SellItemsAtXKills();
        if (sellAt <= 0) return false;
        if (tempVorkathKills > 0) return false;
        tempVorkathKills = sellAt;
        state = State.SELLING_ITEMS;
        return true;
    }

    /**
     * will heal and drink pray pots
     */
    private void healAndDrinkPrayerPotion() {
        while (!Rs2Player.isFullHealth() && !Rs2Inventory.getInventoryFood().isEmpty()) {
            Rs2Bank.closeBank();
            Rs2Player.eatAt(99);
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
        while (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) != Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) && Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new))) {
            Rs2Bank.closeBank();
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
            Rs2Player.waitForAnimation();
            hasInventory = false;
        }
    }

    private void leaveVorkath() {
        kcPerTrip = 0;
        togglePrayer(false);
        Rs2Player.toggleRunEnergy(true);
        switch (config.teleportMode()) {
            case VARROCK_TAB:
                Rs2Inventory.interact(config.teleportMode().getItemName(), config.teleportMode().getAction());
                break;
            case CRAFTING_CAPE:
                if (Rs2Equipment.isWearing("crafting cape")) {
                    Rs2Equipment.interact("crafting cape", "teleport");
                } else {
                    Rs2Inventory.interact("crafting cape", "teleport");
                }
                break;
            case JEWELLERY_BOX:
                teleToPoh();
                if (config.rejuvinationPool()) {
                    Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query().withId(29241).first();
                    if (pool != null) {
                        pool.click("Drink");
                        sleepUntil(() -> Rs2Player.isFullHealth());
                    }
                }
                Rs2TileObjectModel jewelleryBox = Microbot.getRs2TileObjectCache().query().withId(29156).first();
                if (jewelleryBox != null) {
                    jewelleryBox.click("Teleport Menu");
                }
                sleepUntil(() -> Rs2Widget.hasWidget("Castle Wars"));
                Rs2Widget.clickWidget("Castle Wars");
                break;
        }
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Microbot.getClient().isInInstancedRegion());
        state = State.TELEPORT_AWAY;

    }

    private boolean drinkPotions() {
        if (Rs2Player.isAnimating()) return false;
        boolean drinkRangePotion = !Rs2Player.hasDivineBastionActive() && !Rs2Player.hasDivineRangedActive() && !Rs2Player.hasRangingPotionActive(5);
        boolean drinkAntiFire = !Rs2Player.hasAntiFireActive() && !Rs2Player.hasSuperAntiFireActive();
        boolean drinkAntiVenom = !Rs2Player.hasAntiVenomActive();

        if (drinkRangePotion) {
            Rs2Inventory.interact(Rs2Potion.getRangePotionsVariants().toArray(String[]::new), "drink");
        }
        if (drinkAntiFire) {
            Rs2Inventory.interact("super antifire", "drink");
        }
        if (drinkAntiVenom) {
            Rs2Inventory.interact(Rs2Potion.getAntiVenomVariants().toArray(String[]::new), "drink");
        }

        return !drinkRangePotion && !drinkAntiFire && !drinkAntiVenom;
    }

    /**
     * Cures venom mid-fight by drinking an anti-venom potion, but only when the player is actually
     * venomed and not already immune. Venom is reported from the client-thread hitsplat handler.
     */
    private void handleVenom() {
        if (!venomApplied) return;
        if (Rs2Player.hasAntiVenomActive()) {
            venomApplied = false;
            return;
        }
        String[] antiVenoms = Rs2Potion.getAntiVenomVariants().toArray(String[]::new);
        if (!Rs2Inventory.hasItem(antiVenoms)) {
            debug("Venom applied but no anti-venom in inventory");
            return;
        }
        if (Rs2Inventory.interact(antiVenoms, "drink")) {
            debug("Venom cured with anti-venom");
            venomApplied = false;
        }
    }

    /**
     * Called from the client thread when a venom hitsplat lands on the local player.
     */
    public void onVenomApplied() {
        venomApplied = true;
    }

    public void togglePrayer(boolean onOff) {
        if (Rs2Prayer.isOutOfPrayer()) return;
        if (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 74 && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70 && config.activateRigour()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, onOff);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.EAGLE_EYE, onOff);
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, onOff);
    }

    /**
     * Single non-blocking reaction tick for the whole fight. Threat handling runs in priority order
     * and never sleeps, so every loop iteration re-evaluates the game state from scratch. This is
     * what keeps the player in sync with Vorkath's specials.
     */
    private void handleCombat() {
        vorkath = Microbot.getRs2NpcCache().query().withId(NpcID.VORKATH).first();

        if (vorkath == null || vorkath.isDead()) {
            onVorkathDefeated();
            return;
        }
        if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) <= 0) {
            state = State.DEAD_WALK;
            return;
        }
        if (config.KillsPerTrip() > 0 && kcPerTrip >= config.KillsPerTrip()) {
            leaveVorkath();
            return;
        }
        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            double treshHold = (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            if (treshHold < 50) {
                leaveVorkath();
                return;
            }
        }

        // Cure venom as soon as it lands, before handling any special.
        handleVenom();

        // Priority 1: the freeze special (white projectile) and its Zombified Spawn. The phase is
        // latched from the first freeze signal and ends as soon as Crumble Undead is airborne, at
        // which point we swap back to the primary weapon and resume attacking Vorkath instead of
        // standing still until the spawn model disappears.
        Rs2NpcModel zombieSpawn = Microbot.getRs2NpcCache().query().withName(ZOMBIFIED_SPAWN).first();
        boolean crumbleInFlight = doesProjectileExistById(CRUMBLE_UNDEAD_PROJECTILE_ID);
        boolean whiteProjectile = doesProjectileExistById(whiteProjectileId);
        boolean freezeSignal = zombieSpawn != null || whiteProjectile || crumbleInFlight;

        if (freezeSignal) {
            lastFreezeSignalAt = System.currentTimeMillis();
        }
        if (!crumbleCastThisFreeze && freezeSignal) {
            freezePhaseActive = true;
        }

        if (freezePhaseActive) {
            boolean timedOut = System.currentTimeMillis() - lastFreezeSignalAt > 8000;
            if (crumbleInFlight || crumbleCastThisFreeze || timedOut) {
                // Crumble Undead is on its way (a connected cast is a guaranteed kill) or the special
                // timed out: swap back to the primary weapon and fall through to attack Vorkath now.
                if (crumbleInFlight) crumbleCastThisFreeze = true;
                freezePhaseActive = false;
                if (slayerStaffEquippedForSpawn) {
                    restorePrimaryWeapon();
                    slayerStaffEquippedForSpawn = false;
                    debug("Spawn handled, resuming attack on Vorkath");
                }
            } else {
                state = State.ZOMBIE_SPAWN;
                handleZombieSpawn(zombieSpawn, crumbleInFlight);
                return;
            }
        }

        // Re-arm for the next freeze once no spawn or freeze projectile remains.
        if (!freezeSignal) {
            crumbleCastThisFreeze = false;
        }

        // Priority 2: the fireball special. Sidestep the landing tile, then let upkeep re-attack.
        if (doesProjectileExistById(redProjectileId)) {
            state = State.FIGHT_VORKATH;
            handleRedBall();
            return;
        }
        redBallTarget = null;

        // Priority 3: the acid phase. Move to a safe tile in the woox-walk row.
        HashSet<WorldPoint> acidTiles = queryAcidTiles();
        if (doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId) || !acidTiles.isEmpty()) {
            if (!acidPhaseActive) {
                acidPhaseActive = true;
                debug("Acid phase started (pools=" + acidTiles.size() + ")");
            }
            state = State.ACID;
            acidPools.addAll(acidTiles);
            handleAcidWalk();
            return;
        }

        if (acidPhaseActive) {
            acidPhaseActive = false;
            acidWalkTarget = null;
            debug("Acid phase ended");
        }

        // Priority 4: normal upkeep.
        state = State.FIGHT_VORKATH;
        walkToCenterIfNeeded();
        if (!Rs2Combat.inCombat() && System.currentTimeMillis() - lastAttackClickAt >= 600) {
            if (vorkath.click("Attack")) {
                lastAttackClickAt = System.currentTimeMillis();
            }
        }
        drinkPotions();
        handlePrayer();
        Rs2Player.eatAt(config.healThreshold());

        // Swap to diamond bolts once and stay there for the rest of the kill. Vorkath's health only
        // falls, so a latch avoids the flip-flop a jittery health reading around the 30% line caused.
        double healthPercent = vorkath.getHealthPercentage();
        if (healthPercent >= 0 && !usingDiamondBolts && healthPercent < 30) {
            Rs2Inventory.wield("diamond bolts (e)", "diamond dragon bolts (e)");
            usingDiamondBolts = true;
            debug(String.format("Health %.1f%% (< 30%%), switching to diamond bolts (e)", healthPercent));
        } else if (healthPercent >= 50 && !usingDiamondBolts && !Rs2Equipment.isWearing(primaryBolts)) {
            Rs2Inventory.wield(primaryBolts);
            debug(String.format("Health %.1f%% (>= 50%%), switching to primary bolts: %s", healthPercent, primaryBolts));
        }
    }

    private void onVorkathDefeated() {
        vorkathSessionKills++;
        tempVorkathKills--;
        kcPerTrip++;
        pokedVorkath = false;
        usingDiamondBolts = false;
        togglePrayer(false);
        Rs2Inventory.wield(primaryBolts);
        state = State.LOOT_ITEMS;
    }

    private void handleZombieSpawn(Rs2NpcModel zombieSpawn, boolean crumbleInFlight) {
        Rs2Player.eatAt(config.healThreshold());

        if (config.useSlayerStaff()) {
            // Equip the staff as soon as the freeze is telegraphed so the autocast is ready.
            if (!Rs2Equipment.isWearing(SLAYER_STAFF_IDS)) {
                equipSlayerStaff();
                return;
            }
            slayerStaffEquippedForSpawn = true;

            if (zombieSpawn != null && !zombieSpawn.isDead()
                    && System.currentTimeMillis() - lastZombieCastAt >= 600) {
                // With the staff equipped and Crumble Undead preset, a normal attack autocasts it.
                if (zombieSpawn.click("Attack")) {
                    lastZombieCastAt = System.currentTimeMillis();
                }
            }
            return;
        }

        Rs2Tab.switchToMagicTab();
        if (zombieSpawn != null && !zombieSpawn.isDead() && !crumbleInFlight
                && System.currentTimeMillis() - lastZombieCastAt >= 600) {
            if (Rs2Magic.castOn(MagicAction.CRUMBLE_UNDEAD, zombieSpawn)) {
                lastZombieCastAt = System.currentTimeMillis();
            }
        }
    }

    private static boolean isSlayerStaffItem(int itemId) {
        for (int id : SLAYER_STAFF_IDS) {
            if (id == itemId) return true;
        }
        return false;
    }

    /**
     * Wears the Slayer's staff, remembering the weapon it replaces so it can be restored afterwards.
     */
    private void equipSlayerStaff() {
        if (System.currentTimeMillis() - lastStaffEquipAt < 600) return;
        lastStaffEquipAt = System.currentTimeMillis();
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (weapon != null && !isSlayerStaffItem(weapon.getId())) {
            primaryWeaponId = weapon.getId();
        }
        slayerStaffEquippedForSpawn = true;
        Rs2Inventory.wield(SLAYER_STAFF_IDS);
        debug("Equipping Slayer's staff for the spawn");
    }

    private void restorePrimaryWeapon() {
        if (primaryWeaponId != -1 && !Rs2Equipment.isWearing(primaryWeaponId)) {
            Rs2Inventory.wield(primaryWeaponId);
        }
    }

    /**
     * Confirms the Zombified Spawn can be killed before a fight starts, once per trip. Uses the
     * Slayer's staff availability when configured, otherwise a full spellbook/rune/level check.
     */
    private boolean ensureCrumbleUndeadReady() {
        if (crumbleVerified) return true;

        boolean ready;
        if (config.useSlayerStaff()) {
            ready = Rs2Equipment.isWearing(SLAYER_STAFF_IDS) || Rs2Inventory.hasItem(SLAYER_STAFF_IDS);
        } else {
            ready = Rs2Magic.canCast(MagicAction.CRUMBLE_UNDEAD);
        }

        if (!ready) {
            reportFatal(config.useSlayerStaff()
                    ? "Crumble Undead is not available. Have a Slayer's staff (with Crumble Undead set as its autocast) equipped or in your inventory."
                    : "You cannot cast Crumble Undead. Check your magic level, spellbook and runes.");
            return false;
        }

        crumbleVerified = true;
        return true;
    }

    /**
     * Shows a fatal validation message at most once every 10 seconds, without blocking the loop.
     */
    private void reportFatal(String message) {
        if (System.currentTimeMillis() - lastFatalMessageAt < 10000) return;
        lastFatalMessageAt = System.currentTimeMillis();
        Microbot.showMessage(message);
    }

    /**
     * Walks back to the fight tile if the player has drifted off it.
     *
     * @return true if a walk was issued this tick (i.e. the player is not yet settled on the tile).
     */
    private boolean walkToCenterIfNeeded() {
        if (Microbot.getClient().getLocalPlayer() == null) return false;
        if (Microbot.getClient().getLocalPlayer().getLocalLocation().getSceneY() >= 59
                && System.currentTimeMillis() - lastCenterWalkAt >= 600) {
            walkToCenter();
            lastCenterWalkAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void handleRedBall() {
        if (!doesProjectileExistById(redProjectileId)) {
            redBallTarget = null;
            return;
        }

        WorldPoint playerLocation = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        if (playerLocation == null) return;

        if (redBallTarget == null) {
            int dx = Rs2Random.between(0, 2) == 1 ? 2 : -2;
            redBallTarget = new WorldPoint(playerLocation.getX() + dx, playerLocation.getY(), playerLocation.getPlane());
            debug("Red ball: sidestep target " + redBallTarget + " (player=" + playerLocation + ")");
        }

        if (playerLocation.equals(redBallTarget)) {
            Rs2Player.eatAt(config.healThreshold());
            return;
        }

        if (System.currentTimeMillis() - lastRedBallMoveAt >= 300) {
            Rs2Player.eatAt(config.healThreshold());
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), redBallTarget));
            lastRedBallMoveAt = System.currentTimeMillis();
            debug("Red ball: walking to " + redBallTarget);
        }
    }

    private void handlePrayer() {
        drinkPrayer();
        togglePrayer(true);
    }

    /**
     * Reads the live projectile deque once per loop iteration. Besides caching the active ids it
     * records the predicted landing tile of acid projectiles so the acid-walk logic can avoid tiles
     * that are about to be hit.
     */
    private void refreshProjectiles() {
        activeProjectileIds.clear();
        acidPools.clear();
        try {
            for (Projectile projectile : Microbot.getClient().getProjectiles()) {
                activeProjectileIds.add(projectile.getId());
                if (projectile.getId() == acidProjectileId || projectile.getId() == acidRedProjectileId) {
                    WorldPoint landing = projectile.getTargetPoint();
                    if (landing != null) {
                        acidPools.add(landing);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean doesProjectileExistById(int id) {
        return activeProjectileIds.contains(id);
    }

    private boolean isCloseToRelleka() {
        if (Microbot.getClient().getLocalPlayer() == null) return false;
        return Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2670, 3634, 0))) < 80;
    }

    private boolean teleToPoh() {
        if (Rs2Magic.canCast(MagicAction.TELEPORT_TO_HOUSE)) {
            Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(4525).first() != null);
            return true;
        } else if (Rs2Inventory.hasItem("Teleport to house")) {
            Rs2Inventory.interact("Teleport to house", "break");
            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(4525).first() != null);
            return true;
        }
        return false;
    }

    /**
     * Picks the next tile for the acid-phase walk. The plugin shuttles the full width of the back
     * line so each leg is a long, continuous stride (4-5 tiles) rather than a short hop that stops
     * on the spot, leaving time to eat between runs.
     */
    private WorldPoint chooseAcidStep(WorldPoint vorkathLoc, WorldPoint playerLoc, boolean onLine) {
        int y = vorkathLoc.getY() - 8;
        WorldPoint left = new WorldPoint(vorkathLoc.getX() + 1, y, 0);
        WorldPoint right = new WorldPoint(vorkathLoc.getX() + 5, y, 0);

        if (!onLine) {
            // Step straight onto the back line at the player's own column (clamped to the row), so the
            // time spent off the guaranteed-safe tiles is as short as possible.
            int clampedX = Math.max(left.getX(), Math.min(playerLoc.getX(), right.getX()));
            return new WorldPoint(clampedX, y, 0);
        }
        // On the line: run to the far end for a long, continuous stride.
        return playerLoc.distanceTo(left) >= playerLoc.distanceTo(right) ? left : right;
    }

    private HashSet<WorldPoint> queryAcidTiles() {
        HashSet<WorldPoint> tiles = new HashSet<>();
        for (Rs2TileObjectModel object : Microbot.getRs2TileObjectCache().query().withId(ObjectID.VORKATH_ACID).toList()) {
            WorldPoint location = object.getWorldLocation();
            if (location != null) {
                tiles.add(location);
            }
        }
        return tiles;
    }

    private void handleAcidWalk() {
        if (vorkath == null) {
            state = State.FIGHT_VORKATH;
            return;
        }

        WorldPoint playerLocation = Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        WorldPoint vorkathLoc = vorkath.getWorldLocation();
        if (playerLocation == null || vorkathLoc == null) return;

        int rowY = vorkathLoc.getY() - 8;
        boolean onLine = playerLocation.getY() == rowY;

        // The back line is the only guaranteed-safe ground, so never begin a new leg until the player
        // is actually standing on it. While off the line we keep walking to the line tile we picked
        // (only re-issuing when the walk has stopped), so the player always ends up back on the row.
        boolean needNewLeg;
        if (!onLine) {
            needNewLeg = acidWalkTarget == null
                    || acidWalkTarget.getY() != rowY
                    || !Rs2Player.isMoving();
        } else {
            // On the line: chain the next full-width leg as the current one finishes, without stopping.
            needNewLeg = acidWalkTarget == null
                    || playerLocation.distanceTo(acidWalkTarget) <= 1;
        }

        if (!needNewLeg) {
            if (Rs2Player.isMoving()) Rs2Player.eatAt(config.healThreshold());
            return;
        }
        if (System.currentTimeMillis() - lastAcidMoveAt < 100) return;

        WorldPoint step = chooseAcidStep(vorkathLoc, playerLocation, onLine);
        if (step == null) {
            debug("Acid: no step available (player=" + playerLocation + ", pools=" + acidPools.size() + ")");
            return;
        }

        acidWalkTarget = step;
        Rs2Player.eatAt(config.healThreshold());
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), step));
        lastAcidMoveAt = System.currentTimeMillis();
        debug("Acid: step -> " + step + " (player=" + playerLocation + ", onLine=" + onLine + ", pools=" + acidPools.size() + ")");
    }
}
