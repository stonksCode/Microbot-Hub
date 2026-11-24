package net.runelite.client.plugins.microbot.templetrekking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Automates the easy (Route 1 / Mort Myre Swamp) Temple Trekking loop.
 *
 * The world is entered through a chain of instanced scenes. All detection is by
 * entity ID in the client cache rather than hardcoded world points — see
 * docs/TEMPLE_TREKKING_RESEARCH.md.
 */
@Slf4j
public class TempleTrekkingScript extends StateMachineScript<TempleTrekkingScript.State> {

    // --- Constants -------------------------------------------------------------------------------

    public enum State {
        FIND_FOLLOWER,
        PICK_UP,
        SELECT_ROUTE,
        TREK_TRAVEL,
        COMBAT_EVADE,
        BRIDGE_PLANKS,
        BRIDGE_LOGS,
        VINE,
        TREK_COMPLETE,
        REDEEM_REWARDS,
        RECOVER
    }

    private static final int REGION_PATERDOMUS = 13622;
    private static final int REGION_BURGH_DE_ROTT = 13874;
    private static final int ROUTE_EASY_WIDGET = InterfaceID.TempletrekMap.TEMPLETREK_ROUTE_1;
    // The route prompt is an NPC dialogue in the left chat box. Use generated constants rather
    // than raw packed IDs so a client interface change is caught at compile time.
    private static final int ROUTE_DIALOGUE_TEXT_WIDGET = InterfaceID.ChatLeft.TEXT;
    private static final int ROUTE_DIALOGUE_CONTINUE_WIDGET = InterfaceID.ChatLeft.CONTINUE;

    private static final int[] VINE_OBJECTS = {
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_VINES,
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_1,
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_2,
            ObjectID.TEMPLETREK_SWAMPTREE_BRANCH_VINE
    };
    // Only these three tree stages have the Cut-vine option. The branch is a different object
    // before and after the long vine is attached.
    private static final int[] CUT_VINE_OBJECTS = {
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_VINES,
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_1,
            ObjectID.TEMPLETREK_SWAMPTREE_SMALL_2
    };
    private static final int[] BRANCH_OBJECTS = {
            ObjectID.TEMPLETREK_SWAMPTREE_BRANCH,
            ObjectID.TEMPLETREK_SWAMPTREE_BRANCH_VINE
    };
    // Green and brown path variants exist in the swamp instances.
    private static final int[] EVADE_PATHS = {
            ObjectID.TEMPLETREK_SWAMP_EVADE_PATH,
            ObjectID.TEMPLETREK_SWAMP_EVADE_PATH_BROWN
    };
    private static final int[] CONTINUE_PATHS = {
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH,
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH_BROWN,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_1,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_2
    };
    private static final int[] COMBAT_NPCS = {
            NpcID.TEMPLETREK_SNAKE_1,
            NpcID.TEMPLETREK_SNAKE_2,
            NpcID.TEMPLETREK_SNAKE_3,
            NpcID.TEMPLETREK_SHADE_1,
            NpcID.TEMPLETREK_SHADE_2,
            NpcID.TEMPLETREK_SHADE_3,
            NpcID.TEMPLETREK_GHAST_INVIS_1,
            NpcID.TEMPLETREK_GHAST_INVIS_2,
            NpcID.TEMPLETREK_GHAST_INVIS_3,
            NpcID.TEMPLETREK_GHAST_VIS_1,
            NpcID.TEMPLETREK_GHAST_VIS_2,
            NpcID.TEMPLETREK_GHAST_VIS_3,
            NpcID.TEMPLETREK_GIANTSNAIL_1,
            NpcID.TEMPLETREK_GIANTSNAIL_2,
            NpcID.TEMPLETREK_GIANTSNAIL_3,
            NpcID.TEMPLETREK_VAMPIRE_1,
            NpcID.TEMPLETREK_VAMPIRE_2,
            NpcID.TEMPLETREK_VAMPIRE_3,
            NpcID.TEMPLETREK_TENTACLE_ARM,
            NpcID.TEMPLETREK_TENTACLE_HEAD,
            NpcID.TTREK2_NAIL_BEAST_1,
            NpcID.TTREK2_NAIL_BEAST_2,
            NpcID.TTREK2_NAIL_BEAST_3,
            // TEMPLETREK_ZOMBIE is a bridge guard, not a generic combat event.
            NpcID.MORYTANIA_BOOT_SAMARITAN
    };

    // The scheduler already runs every 300 ms. A 600 ms cooldown skipped every other tick and
    // made every dialogue/event transition visibly lag; 300 ms keeps one action per loop without
    // outrunning the 600 ms game tick.
    private static final long ACTION_COOLDOWN_MS = 300;
    private static final long PROGRESS_TIMEOUT_MS = 45000;
    private static final long STATE_TIMEOUT_MS = 120000;
    private static final long RECOVERY_WAIT_MS = 1500;
    private static final long ROUTE_GRACE_MS = 1500;
    private static final long ROUTE_DIALOGUE_TIMEOUT_MS = 12000;
    private static final int MAX_ROUTE_DIALOGUE_ATTEMPTS = 24;

    // --- Runtime state ---------------------------------------------------------------------------

    private TempleTrekkingConfig config;
    private volatile OverlaySnapshot overlaySnapshot = OverlaySnapshot.initial();

    private boolean followerReady;
    private boolean routeDialogueReady;
    private boolean routeDialogueSeen;
    private boolean routeDialogueFailed;
    private int routeDialogueAttempts;
    private long routeDialogueStartedAt;
    private boolean routeSelected;
    private boolean routeSkipped;
    private boolean escortActive;
    private boolean evadeSucceeded;
    private boolean vineSolved;
    private boolean destinationReached;
    private boolean rewardCollected;
    private boolean trekFinalized;
    private boolean recoveryDone;
    private boolean pendingShutdown;

    private boolean vineAttached;
    private boolean vineSwung;
    private long vineSwingStartedAt;
    private int vineRegionAtSwing = -1;
    private int vineContinueCount;

    private int rewardTokenBaseline;

    private long lastActionAt;
    private long lastProgressAt = System.currentTimeMillis();
    private long stateEnteredAt = System.currentTimeMillis();
    private long recoveryStartedAt;
    private long lastWarningAt;

    private int treksCompleted;
    private int eventsHandled;

    // Two independent bridge implementations. They share only plumbing, never event rules.
    private final TempleTrekkingBridgeContext bridgeContext = new TempleTrekkingBridgeContext() {
        @Override
        public TempleTrekkingConfig config() {
            return config;
        }

        @Override
        public boolean canAct() {
            return TempleTrekkingScript.this.canAct();
        }

        @Override
        public void markAction() {
            TempleTrekkingScript.this.markAction();
        }

        @Override
        public void noteProgress() {
            lastProgressAt = System.currentTimeMillis();
        }

        @Override
        public boolean clearDialogue() {
            return TempleTrekkingScript.this.clearBridgeDialogue();
        }

        @Override
        public boolean interactObject(int[] ids, String action) {
            return TempleTrekkingScript.this.interactObject(ids, action);
        }

        @Override
        public void maybeEat() {
            TempleTrekkingScript.this.maybeEat();
        }

        @Override
        public void warn(String message) {
            warnThrottled(message);
        }
    };
    private final TempleTrekkingPlankBridgeHandler plankBridge =
            new TempleTrekkingPlankBridgeHandler(bridgeContext);
    private final TempleTrekkingLogBridgeHandler logBridge =
            new TempleTrekkingLogBridgeHandler(bridgeContext);
    private final TempleTrekkingRewardHandler rewardHandler =
            new TempleTrekkingRewardHandler(new TempleTrekkingRewardHandler.Context() {
                @Override
                public TempleTrekkingConfig config() {
                    return config;
                }

                @Override
                public boolean canAct() {
                    return TempleTrekkingScript.this.canAct();
                }

                @Override
                public void markAction() {
                    TempleTrekkingScript.this.markAction();
                }

                @Override
                public void warn(String message) {
                    warnThrottled(message);
                }
            });
    private TempleTrekkingBridgeHandler activeBridge;

    private String currentSide = "Unknown";
    private String currentEvent = "None";
    private String lastMessage = "";

    // --- Lifecycle -------------------------------------------------------------------------------

    public boolean run(TempleTrekkingConfig config) {
        this.config = config;
        resetAllState();
        log.info("[TempleTrekking] Starting easy-route script (maxTreks={}, farmLumberjacks={})",
                config.maxTreks(), config.farmLumberjacks());

        Rs2Antiban.setActivity(Activity.TEMPLE_TREKKING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        if (config.checkSupplies()) {
            if (!Rs2Inventory.hasItem(ItemID.KNIFE)) {
                log.warn("[TempleTrekking] Missing Knife — swamp vine event cannot be solved");
            }
            if (!Rs2Inventory.hasItem(ItemID.HAMMER)) {
                log.warn("[TempleTrekking] Missing Hammer — broken bridge event cannot be solved");
            }
            if (!Rs2Inventory.hasItem(ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE,
                    ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE, ItemID.RUNE_AXE,
                    ItemID.BLACK_AXE, ItemID.DRAGON_AXE)) {
                log.warn("[TempleTrekking] No axe found — bridge logs cannot be chopped");
            }
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                step();
            } catch (Exception ex) {
                log.error("[TempleTrekking] Unexpected error in scheduled loop", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("[TempleTrekking] Stopped after {} treks", treksCompleted);
    }

    private void resetAllState() {
        followerReady = routeDialogueReady = routeSelected = routeSkipped = escortActive = false;
        routeDialogueSeen = false;
        routeDialogueFailed = false;
        routeDialogueAttempts = 0;
        routeDialogueStartedAt = 0;
        vineSolved = destinationReached = rewardCollected = trekFinalized = false;
        recoveryDone = vineAttached = vineSwung = false;
        pendingShutdown = false;
        evadeSucceeded = false;
        vineSwingStartedAt = 0;
        vineRegionAtSwing = -1;
        vineContinueCount = 0;
        rewardTokenBaseline = 0;
        lastActionAt = 0;
        lastProgressAt = System.currentTimeMillis();
        stateEnteredAt = lastProgressAt;
        recoveryStartedAt = 0;
        treksCompleted = 0;
        eventsHandled = 0;
        activeBridge = null;
        plankBridge.reset();
        logBridge.reset();
        rewardHandler.reset();
        currentSide = "Unknown";
        currentEvent = "None";
        lastMessage = "";
    }

    // --- State machine definition ---------------------------------------------------------------

    @Override
    protected State initialState() {
        return State.FIND_FOLLOWER;
    }

    @Override
    protected List<Transition<State>> defineTransitions() {
        return List.of(
                // Death has absolute priority. Without this, a lethal combat event could leave the
                // script in VINE/BRIDGE_* forever because the scene disappears on respawn.
                Transition.<State>from(State.TREK_TRAVEL)
                        .when(this::isLocalPlayerDead, "isLocalPlayerDead()")
                        .because("Died during trek").goTo(State.RECOVER),
                Transition.<State>from(State.COMBAT_EVADE)
                        .when(this::isLocalPlayerDead, "isLocalPlayerDead()")
                        .because("Died during combat").goTo(State.RECOVER),
                Transition.<State>from(State.VINE)
                        .when(this::isLocalPlayerDead, "isLocalPlayerDead()")
                        .because("Died during vine event").goTo(State.RECOVER),
                Transition.<State>from(State.BRIDGE_PLANKS)
                        .when(this::isLocalPlayerDead, "isLocalPlayerDead()")
                        .because("Died during plank bridge").goTo(State.RECOVER),
                Transition.<State>from(State.BRIDGE_LOGS)
                        .when(this::isLocalPlayerDead, "isLocalPlayerDead()")
                        .because("Died during log bridge").goTo(State.RECOVER),

                // Combat must win over vine/bridge detection. The Riyl shadow scene contains
                // choppable Dead trees as decoration, so a tree-only log check can misclassify a
                // lethal combat event. The Evade-event path is unambiguous.
                Transition.<State>from(State.TREK_TRAVEL)
                        .when(this::shouldHandleCombat, "shouldHandleCombat()")
                        .because("Hostile event NPCs / Evade path").goTo(State.COMBAT_EVADE),
                Transition.<State>from(State.VINE)
                        .when(this::shouldHandleCombat, "shouldHandleCombat()")
                        .because("Combat started during vine event").goTo(State.COMBAT_EVADE),
                Transition.<State>from(State.BRIDGE_PLANKS)
                        .when(this::shouldHandleCombat, "shouldHandleCombat()")
                        .because("Combat started during plank bridge").goTo(State.COMBAT_EVADE),
                Transition.<State>from(State.BRIDGE_LOGS)
                        .when(this::shouldHandleCombat, "shouldHandleCombat()")
                        .because("Combat started during log bridge").goTo(State.COMBAT_EVADE),
                Transition.<State>from(State.RECOVER)
                        .when(this::shouldHandleCombat, "shouldHandleCombat()")
                        .because("Combat still active during recovery").goTo(State.COMBAT_EVADE),

                // An instance means an escort is already active. Check this BEFORE looking for a
                // hub follower, otherwise we pick up the in-instance follower and spam "Escort".
                Transition.<State>from(State.FIND_FOLLOWER)
                        .when(this::isInTrekScene, "isInTrekScene()")
                        .because("Started inside a trek instance").goTo(State.SELECT_ROUTE),

                // Redeem at the hub, before picking another follower. This keeps the reward
                // interface away from instanced scenes and event object detection.
                Transition.<State>from(State.FIND_FOLLOWER)
                        .when(this::shouldRedeemRewards, "shouldRedeemRewards()")
                        .because("Reward token threshold reached").goTo(State.REDEEM_REWARDS),

                Transition.<State>from(State.FIND_FOLLOWER)
                        .when(() -> followerReady, "followerReady")
                        .because("Found an easy follower at the hub").goTo(State.PICK_UP),

                // Loading into an instance after clicking Escort must not keep clicking Escort.
                Transition.<State>from(State.PICK_UP)
                        .when(this::isInTrekScene, "isInTrekScene()")
                        .because("Escort started; route awaits selection").goTo(State.SELECT_ROUTE),

                Transition.<State>from(State.PICK_UP)
                        .when(() -> routeDialogueReady, "routeDialogueReady")
                        .because("Route selection dialogue is open").goTo(State.SELECT_ROUTE),

                Transition.<State>from(State.PICK_UP)
                        .when(() -> !hasFollower(), "!hasFollower()")
                        .because("Follower disappeared").goTo(State.FIND_FOLLOWER),

                Transition.<State>from(State.SELECT_ROUTE)
                        .when(() -> routeSelected || routeSkipped, "routeSelected || routeSkipped")
                        .because("Route is selected").goTo(State.TREK_TRAVEL),

                Transition.<State>from(State.SELECT_ROUTE)
                        .when(() -> routeDialogueFailed, "routeDialogueFailed")
                        .because("Route dialogue would not open the map").goTo(State.RECOVER),

                Transition.<State>from(State.SELECT_ROUTE)
                        .when(this::stalled, "stalled")
                        .because("Route selection stalled").goTo(State.RECOVER),

                Transition.<State>from(State.TREK_TRAVEL)
                        .when(() -> destinationReached, "destinationReached")
                        .because("You reach your destination").goTo(State.TREK_COMPLETE),

                // The vine event must win over both bridge handlers. Its follower dialogue says
                // "can't get over this bog", which is not a bridge, and its Swamp tree objects
                // can still be cached while the player walks towards the vine. The solved latch
                // stops the same vine scene from re-entering VINE before Continue-trek loads.
                Transition.<State>from(State.TREK_TRAVEL)
                        .when(() -> hasVine() && !vineSolved, "hasVine() && !vineSolved")
                        .because("Swamp vine event").goTo(State.VINE),

                // Strict object/NPC detection only. Never classify a bridge event from follower
                // dialogue: "can't get over this bog" is the vine event, not a bridge.
                Transition.<State>from(State.TREK_TRAVEL)
                        .when(this::hasLogBridgeEvent, "hasLogBridgeEvent()")
                        .because("Dead tree / log bridge event").goTo(State.BRIDGE_LOGS),

                Transition.<State>from(State.TREK_TRAVEL)
                        .when(this::hasPlankBridgeEvent, "hasPlankBridgeEvent()")
                        .because("Undead Lumberjack / plank bridge event").goTo(State.BRIDGE_PLANKS),

                Transition.<State>from(State.TREK_TRAVEL)
                        .when(this::stalled, "stalled")
                        .because("No event progress").goTo(State.RECOVER),

                Transition.<State>from(State.COMBAT_EVADE)
                        .when(() -> evadeSucceeded || destinationReached, "evadeSucceeded || destinationReached")
                        .because("Combat event cleared").goTo(State.TREK_TRAVEL),

                Transition.<State>from(State.COMBAT_EVADE)
                        .when(this::stalled, "stalled")
                        .because("Evade did not resolve").goTo(State.RECOVER),

                Transition.<State>from(State.BRIDGE_PLANKS)
                        .when(() -> destinationReached || (activeBridge != null && activeBridge.isSolved()),
                                "destinationReached || plankBridge.isSolved()")
                        .because("Plank bridge event cleared").goTo(State.TREK_TRAVEL),

                Transition.<State>from(State.BRIDGE_PLANKS)
                        .when(this::stalled, "stalled")
                        .because("Plank bridge repair stalled").goTo(State.RECOVER),

                Transition.<State>from(State.BRIDGE_LOGS)
                        .when(() -> destinationReached || (activeBridge != null && activeBridge.isSolved()),
                                "destinationReached || logBridge.isSolved()")
                        .because("Log bridge event cleared").goTo(State.TREK_TRAVEL),

                Transition.<State>from(State.BRIDGE_LOGS)
                        .when(this::stalled, "stalled")
                        .because("Log bridge repair stalled").goTo(State.RECOVER),

                Transition.<State>from(State.VINE)
                        .when(() -> vineSolved || destinationReached, "vineSolved || destinationReached")
                        .because("Vine event cleared").goTo(State.TREK_TRAVEL),

                Transition.<State>from(State.VINE)
                        .when(this::stalled, "stalled")
                        .because("Vine event stalled").goTo(State.RECOVER),

                Transition.<State>from(State.TREK_COMPLETE)
                        .when(() -> rewardCollected && isAtHub(), "rewardCollected && isAtHub()")
                        .because("Reward collected; ready for next follower").goTo(State.FIND_FOLLOWER),

                Transition.<State>from(State.TREK_COMPLETE)
                        .when(this::stalled, "stalled")
                        .because("Reward dialogue stalled").goTo(State.RECOVER),

                Transition.<State>from(State.REDEEM_REWARDS)
                        .when(rewardHandler::isDone, "rewardHandler.isDone()")
                        .because("Reward tokens redeemed").goTo(State.FIND_FOLLOWER),

                Transition.<State>from(State.REDEEM_REWARDS)
                        .when(this::stalled, "stalled")
                        .because("Reward redemption stalled").goTo(State.RECOVER),

                Transition.<State>from(State.RECOVER)
                        .when(() -> recoveryDone, "recoveryDone")
                        .because("Recovery complete").goTo(State.FIND_FOLLOWER)
        );
    }

    @Override
    protected void onTransition(State from, State to, String reason) {
        super.onTransition(from, to, reason);
        lastProgressAt = System.currentTimeMillis();
        stateEnteredAt = lastProgressAt;

        switch (to) {
            case FIND_FOLLOWER:
                if (from == State.REDEEM_REWARDS && pendingShutdown) {
                    log.info("[TempleTrekking] Rewards redeemed; stopping at maxTreks={}",
                            config.maxTreks());
                    shutdown();
                    break;
                }
                followerReady = false;
                currentEvent = "Finding follower";
                // Route selection is per trek. Without this reset the second trek sees the
                // previous routeSelected flag and skips the "which route?" dialogue/map.
                routeSelected = false;
                routeSkipped = false;
                routeDialogueReady = false;
                rewardCollected = false;
                trekFinalized = false;
                destinationReached = false;
                escortActive = false;
                vineSolved = false;
                vineAttached = false;
                vineSwung = false;
                vineSwingStartedAt = 0;
                vineRegionAtSwing = -1;
                vineContinueCount = 0;
                activeBridge = null;
                plankBridge.reset();
                logBridge.reset();
                break;
            case PICK_UP:
                currentEvent = "Picking up follower";
                break;
            case SELECT_ROUTE:
                currentEvent = "Selecting route";
                routeSelected = false;
                routeSkipped = false;
                routeDialogueSeen = false;
                routeDialogueFailed = false;
                routeDialogueAttempts = 0;
                routeDialogueStartedAt = 0;
                break;
            case TREK_TRAVEL:
                currentEvent = "Travelling";
                // Keep the handler's solved latch. The fixed bridge object can linger for a few
                // ticks after crossing; resetting here would immediately re-enter the event.
                activeBridge = null;
                break;
            case COMBAT_EVADE:
                currentEvent = "Combat — evading";
                evadeSucceeded = false;
                recoveryDone = false;
                eventsHandled++;
                break;
            case BRIDGE_PLANKS:
                activeBridge = plankBridge;
                plankBridge.enter();
                currentEvent = plankBridge.describe();
                eventsHandled++;
                break;
            case BRIDGE_LOGS:
                activeBridge = logBridge;
                logBridge.enter();
                currentEvent = logBridge.describe();
                eventsHandled++;
                break;
            case VINE:
                currentEvent = "Swamp vine";
                vineSolved = false;
                vineAttached = false;
                vineSwung = false;
                vineSwingStartedAt = 0;
                vineRegionAtSwing = -1;
                vineContinueCount = 0;
                eventsHandled++;
                break;
            case TREK_COMPLETE:
                currentEvent = "Delivering reward";
                rewardTokenBaseline = Rs2Inventory.count("Reward token");
                break;
            case REDEEM_REWARDS:
                currentEvent = "Redeeming rewards";
                rewardHandler.enter();
                break;
            case RECOVER:
                currentEvent = "Recovering";
                recoveryStartedAt = System.currentTimeMillis();
                recoveryDone = false;
                break;
            default:
                break;
        }
        publishOverlaySnapshot();
    }

    @Override
    protected State onError(State state, Exception e) {
        log.error("[TempleTrekking] Error in state {}: {}", state, e.getMessage(), e);
        return state;
    }

    // --- State actions ---------------------------------------------------------------------------

    @Override
    protected void onState(State state) {
        updateSide();

        switch (state) {
            case FIND_FOLLOWER:
                doFindFollower();
                break;
            case PICK_UP:
                doPickUp();
                break;
            case SELECT_ROUTE:
                doSelectRoute();
                break;
            case TREK_TRAVEL:
                // Event detection happens in transition guards. Release the vine latch once the
                // solved scene is gone so a later vine event in another instance can be detected.
                if (vineSolved && !hasVine()) {
                    vineSolved = false;
                    vineRegionAtSwing = -1;
                }
                // Clear any trailing follower chatter from the previous event while we wait for
                // the next scene.
                dismissDialogueIfOpen();
                break;
            case COMBAT_EVADE:
                maybeEat();
                doEvade();
                break;
            case BRIDGE_PLANKS:
            case BRIDGE_LOGS:
                if (activeBridge != null) {
                    activeBridge.step();
                    currentEvent = activeBridge.describe();
                }
                break;
            case VINE:
                maybeEat();
                doVine();
                break;
            case TREK_COMPLETE:
                doComplete();
                break;
            case REDEEM_REWARDS:
                rewardHandler.step();
                currentEvent = rewardHandler.describe();
                break;
            case RECOVER:
                doRecover();
                break;
            default:
                break;
        }
    }

    private void doFindFollower() {
        if (!isAtHub()) {
            warnThrottled("Not at a known hub; current region=" + currentRegion());
            return;
        }

        Rs2NpcModel follower = findFollower();
        if (follower != null) {
            followerReady = true;
            log.info("[TempleTrekking] Found follower '{}' at {}", follower.getName(), currentSide);
        } else {
            warnThrottled("No (easy) follower visible at " + currentSide);
        }
    }

    private void doPickUp() {
        if (isInTrekScene()) {
            return; // transition handles it; never click Escort inside an instance
        }
        Rs2NpcModel follower = findFollower();
        if (follower == null) {
            return;
        }
        if (!Rs2Dialogue.isInDialogue()) {
            if (escortActive) {
                return;
            }
            if (canAct()) {
                boolean clicked = follower.click("Escort");
                markAction();
                // Latch here instead of waiting for the chat message. Otherwise the 300 ms loop
                // keeps sending Escort while the instance loads, which delays the route prompt.
                if (clicked) {
                    escortActive = true;
                }
            }
            return;
        }

        // Destination confirmation can open immediately after Escort. It must still be answered
        // even though escortActive is already latched.
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            markAction();
        } else if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("I can take you to Burgh de Rott.",
                    "I can take you to Paterdomus.", "Yes", "Okay");
            markAction();
        }
    }

    private void doSelectRoute() {
        if (routeSelected || routeSkipped) {
            return;
        }

        long now = System.currentTimeMillis();

        // The map is only usable after the NPC's "Okay, which route should we take?"
        // dialogue has been continued. Prefer the map whenever it is visible.
        if (Rs2Widget.isWidgetVisible(ROUTE_EASY_WIDGET)) {
            if (canAct() && Rs2Widget.clickWidget(ROUTE_EASY_WIDGET)) {
                log.info("[TempleTrekking] Selected easy route via widget {}", ROUTE_EASY_WIDGET);
                markAction();
            }
            return;
        }

        if (hasRouteDialogue()) {
            if (!routeDialogueSeen) {
                routeDialogueSeen = true;
                routeDialogueStartedAt = now;
                routeDialogueAttempts = 0;
                currentEvent = "Dismissing route dialogue";
                log.info("[TempleTrekking] Route dialogue detected; clicking Continue");
            }
            lastProgressAt = now;
            if (canAct()
                    && routeDialogueAttempts < MAX_ROUTE_DIALOGUE_ATTEMPTS
                    && now - routeDialogueStartedAt < ROUTE_DIALOGUE_TIMEOUT_MS) {
                if (clickRouteDialogueContinue()) {
                    routeDialogueAttempts++;
                    markAction();
                }
            } else {
                warnThrottled("Route dialogue did not open the map; resetting route selection");
                routeDialogueFailed = true;
            }
            return;
        }

        // If the route prompt was seen and has now closed, allow the map interface a moment to
        // appear rather than immediately assuming the route was already selected.
        if (routeDialogueSeen && now - routeDialogueStartedAt < ROUTE_DIALOGUE_TIMEOUT_MS) {
            return;
        }

        // The prompt was dismissed but the map never appeared. Recover instead of blindly
        // continuing the trek without a route.
        if (routeDialogueSeen) {
            warnThrottled("Route map did not appear after the dialogue; resetting route selection");
            routeDialogueFailed = true;
            return;
        }

        // Started mid-trek: the route widget is gone and no route dialogue is open, so the
        // route was already selected before the plugin started.
        if (now - stateEnteredAt > ROUTE_GRACE_MS) {
            log.info("[TempleTrekking] Route widget absent; assuming route already selected");
            routeSkipped = true;
        }
    }

    /**
     * Dismiss the route prompt. The text can render a tick before the dialogue APIs see it, so
     * click the exact Continue widget from the original recording first, then fall back to the
     * dialogue API and finally Space.
     */
    private boolean clickRouteDialogueContinue() {
        if (Rs2Widget.isWidgetVisible(ROUTE_DIALOGUE_CONTINUE_WIDGET)
                && Rs2Widget.clickWidget(ROUTE_DIALOGUE_CONTINUE_WIDGET)) {
            return true;
        }
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            return true;
        }
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        return true;
    }

    /**
     * Reads the NPC dialogue text widget directly. {@link Rs2Dialogue#isInDialogue()} can report
     * false while the route prompt's text is already rendered because the continue arrow is
     * added a tick later.
     */
    private boolean hasRouteDialogue() {
        String text = currentDialogueText();
        return text != null && text.toLowerCase().contains("which route");
    }

    private void doEvade() {
        if (evadeSucceeded || destinationReached || isLocalPlayerDead()) {
            return;
        }
        // Eat before the next hit lands; the shade event killed the player from 16 HP in ~19 s.
        maybeEat();
        if (canAct() && interactObject(EVADE_PATHS, "Evade-event")) {
            log.info("[TempleTrekking] Clicked Evade-event");
            markAction();
        }
    }

    /**
     * Clears dialogue while already inside a bridge state. Detection never uses dialogue text,
     * so the vine event's "can't get over this bog" line cannot be mistaken for a bridge.
     */
    private boolean clearBridgeDialogue() {
        String text = currentDialogueText();
        boolean inDialogue = Rs2Dialogue.isInDialogue();
        if (!canAct() || ((text == null || text.isEmpty()) && !inDialogue)) {
            return false;
        }
        String lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("attempt to fix the bridge") || lower.contains("fix the bridge")) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                Rs2Dialogue.clickOption("Yes.", "Yes");
            } else if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            } else {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
        } else if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Continue", "Okay", "Cancel");
        } else if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
        } else {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        markAction();
        return true;
    }

    private String currentDialogueText() {
        if (Rs2Widget.isWidgetVisible(ROUTE_DIALOGUE_TEXT_WIDGET)) {
            String direct = Rs2Widget.getChildWidgetText(
                    ROUTE_DIALOGUE_TEXT_WIDGET >>> 16, ROUTE_DIALOGUE_TEXT_WIDGET & 0xFFFF);
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
        }
        String dialogue = Rs2Dialogue.getDialogueText();
        return dialogue == null ? "" : dialogue;
    }

    /** The post-swing follower line is a reliable signal that the vine event is finished. */
    private boolean dialogueIndicatesHeadingOff() {
        String text = currentDialogueText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("heading off") || lower.contains("should be going");
    }

    // --- Vine event -----------------------------------------------------------------------------

    private void doVine() {
        if (vineSolved || destinationReached || isLocalPlayerDead()) {
            return;
        }

        // After the swing the attached branch despawns and the follower starts chatting. The old
        // code cleared dialogue first and then looked for the branch, which left the event waiting
        // for an object that no longer exists. Try the Continue-trek path immediately; only clear
        // dialogue when the path is not clickable yet.
        if (vineSwung) {
            // Scene change is definitive; it can happen before we even sample the next path.
            if (vineRegionAtSwing > 0 && currentRegion() > 0
                    && currentRegion() != vineRegionAtSwing) {
                vineSolved = true;
                log.info("[TempleTrekking] Vine event cleared (scene changed {} -> {})",
                        vineRegionAtSwing, currentRegion());
                return;
            }
            // A real NPC dialogue blocks object clicks. Clear that first, but do not trust the
            // chat-text widget alone here: it can retain stale text and mask a clickable path.
            if ((Rs2Dialogue.isInDialogue() || Rs2Dialogue.hasContinue()
                    || Rs2Dialogue.hasSelectAnOption()) && dismissDialogueIfOpen()) {
                return;
            }
            if (continueVineTrek()) {
                return;
            }
            // No path yet. Only now is it safe to use text-only dialogue detection as a fallback.
            if (dismissDialogueIfOpen()) {
                return;
            }
            if (vineContinueCount == 0
                    && System.currentTimeMillis() - vineSwingStartedAt > 6000) {
                warnThrottled("Vine swing landed but no Continue-trek path was clickable "
                        + "(region=" + currentRegion() + ", dialogue='" + currentDialogueText()
                        + "', headingOff=" + dialogueIndicatesHeadingOff() + ")");
            }
            return;
        }

        // Before the swing, dialogue blocks every object interaction, so clear it first.
        if (dismissDialogueIfOpen()) {
            return;
        }

        // Combat after the swing is handled by the state-machine transition guard. Setting
        // vineSolved here would skip the required Continue-trek clicks and cause the VINE loop.

        // Check every tick: either the chat confirmation or the transformed branch object means the
        // long rope is already tied to the tree. Once attached, never cut more short rope.
        if (!vineAttached && nearestObject(ObjectID.TEMPLETREK_SWAMPTREE_BRANCH_VINE) != null) {
            vineAttached = true;
            log.info("[TempleTrekking] Long vine attached to branch");
        }

        if (vineAttached) {
            Rs2TileObjectModel swingObject =
                    nearestObject(ObjectID.TEMPLETREK_SWAMPTREE_BRANCH_VINE);
            if (swingObject == null) {
                // Some instances keep the pre-attach id but expose Swing-from on it.
                swingObject = nearestBranch();
            }
            if (swingObject == null) {
                // The branch can disappear on a scene change before the swing lands. Recheck the
                // inventory instead of pretending the rope is still attached.
                vineAttached = false;
                return;
            }
            currentEvent = "Swamp vine — swinging from branch";
            if (canAct() && swingObject.click("Swing-from")) {
                vineSwung = true;
                vineSwingStartedAt = System.currentTimeMillis();
                vineRegionAtSwing = currentRegion();
                markAction();
                log.info("[TempleTrekking] Swung from vine (region {}, continuePaths={})",
                        vineRegionAtSwing, countObjects(CONTINUE_PATHS));
            }
            return;
        }

        // Prefer an already-made long rope. This ordering is the key fix: the old code checked the
        // short-rope count first, so after combining, the leftover short rope made it cut again
        // instead of attaching the long rope.
        if (Rs2Inventory.count(ItemID.TEMPLETREK_LONG_VINE) > 0) {
            currentEvent = "Swamp vine — attaching long rope";
            Rs2TileObjectModel branch = nearestBranch();
            if (branch != null && canAct()
                    && Rs2Inventory.useItemOnObject(ItemID.TEMPLETREK_LONG_VINE, branch.getId())) {
                markAction();
                log.info("[TempleTrekking] Used Long vine on Swamp tree branch");
            }
            return;
        }

        int shortVines = Rs2Inventory.count(ItemID.TEMPLETREK_SHORT_VINE);
        if (shortVines >= 3) {
            currentEvent = "Swamp vine — combining short rope";
            if (canAct() && Rs2Inventory.combineClosest(
                    ItemID.TEMPLETREK_SHORT_VINE, ItemID.TEMPLETREK_SHORT_VINE)) {
                markAction();
                log.info("[TempleTrekking] Combined Short vines into a Long vine");
            }
            return;
        }

        currentEvent = "Swamp vine — cutting short rope (" + shortVines + "/3)";
        if (canAct() && interactObject(CUT_VINE_OBJECTS, "Cut-vine")) {
            markAction();
        }
    }

    /**
     * Continues the trek after the swing without requiring the despawned attached branch.
     * Returns true when a click was sent, so the caller can skip text-only dialogue handling.
     */
    private boolean continueVineTrek() {
        currentEvent = "Swamp vine — continuing trek";

        // Do NOT require the attached-branch object here. The recording proves it despawns before
        // the first Continue-trek click; that stale gate caused a ~91 s stall and a later death.
        if (!canAct()) {
            return false;
        }
        if (interactObject(CONTINUE_PATHS, "Continue-trek")) {
            vineContinueCount++;
            markAction();
            log.info("[TempleTrekking] Clicked Continue-trek after vine swing ({})",
                    vineContinueCount);
            // Two clicks finish the normal vine scene. Requiring the vine objects to be gone as
            // well prevents a false "solved" when a click was accepted but did not advance.
            if (vineContinueCount >= 2 && !hasVine()) {
                vineSolved = true;
                log.info("[TempleTrekking] Vine event cleared (two Continue-trek clicks)");
            }
            return true;
        }

        // Throttle retries while the next Continue path is still being created.
        markAction();
        return false;
    }

    private int countObjects(int[] ids) {
        int count = 0;
        for (int id : ids) {
            if (nearestObject(id) != null) {
                count++;
            }
        }
        return count;
    }

    /** Dismisses any active NPC dialogue, including the vine event's follower chatter. */
    private boolean dismissDialogueIfOpen() {
        if (!canAct()) {
            return false;
        }
        String text = currentDialogueText();
        boolean hasControls = Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption();
        if ((text == null || text.isEmpty()) && !hasControls) {
            return false;
        }
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Continue", "Okay", "Yes");
        } else if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
        } else {
            // Text can render a tick before the continue arrow appears.
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        markAction();
        return true;
    }

    private Rs2TileObjectModel nearestBranch() {
        for (int id : BRANCH_OBJECTS) {
            Rs2TileObjectModel branch = nearestObject(id);
            if (branch != null) {
                return branch;
            }
        }
        return null;
    }

    // --- Completion / recovery -------------------------------------------------------------------

    private void doComplete() {
        if (Rs2Dialogue.isInDialogue()) {
            String text = Rs2Dialogue.getDialogueText();
            if (text != null && text.toLowerCase().contains("reward token")) {
                // In game this arrives as a dialogue change, not a GAMEMESSAGE.
                rewardCollected = true;
            }
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                markAction();
            } else if (Rs2Dialogue.hasSelectAnOption()) {
                Rs2Dialogue.clickOption("Continue", "Okay", "Yes");
                markAction();
            }
            return;
        }

        // Fallback: the reward token appears in the inventory. This covers cases where the
        // dialogue is already gone by the time we sample it.
        if (!rewardCollected && Rs2Inventory.count("Reward token") > rewardTokenBaseline) {
            rewardCollected = true;
        }

        if (!trekFinalized && rewardCollected && isAtHub()) {
            treksCompleted++;
            trekFinalized = true;
            log.info("[TempleTrekking] Trek {} completed at {}", treksCompleted, currentSide);

            if (config.maxTreks() > 0 && treksCompleted >= config.maxTreks()) {
                if (config.redeemRewards()
                        && rewardHandler.tokenCount() >= config.rewardThreshold()) {
                    // Redeem the final batch before stopping.
                    pendingShutdown = true;
                    log.info("[TempleTrekking] Reached maxTreks={}; redeeming rewards before stop",
                            config.maxTreks());
                } else {
                    log.info("[TempleTrekking] Reached maxTreks={}; stopping", config.maxTreks());
                    shutdown();
                }
            }
        }
    }

    private void doRecover() {
        if (isLocalPlayerDead()) {
            warnThrottled("Died during the trek; waiting to respawn before finding a new follower");
            if (Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                markAction();
            }
            return;
        }
        if (System.currentTimeMillis() - recoveryStartedAt < RECOVERY_WAIT_MS) {
            return;
        }
        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            } else if (Rs2Dialogue.hasSelectAnOption()) {
                Rs2Dialogue.clickOption("Cancel", "Continue", "Okay");
            }
            markAction();
            return;
        }
        if (Rs2Inventory.isItemSelected()) {
            Rs2Inventory.deselect();
        }
        routeSelected = false;
        routeSkipped = false;
        routeDialogueFailed = false;
        activeBridge = null;
        plankBridge.reset();
        logBridge.reset();
        vineSolved = false;
        vineAttached = false;
        vineSwung = false;
        vineSwingStartedAt = 0;
        vineRegionAtSwing = -1;
        vineContinueCount = 0;
        evadeSucceeded = false;
        rewardCollected = false;
        destinationReached = false;
        recoveryDone = true;
        log.info("[TempleTrekking] Recovered; re-evaluating scene");
    }

    // --- Chat message feed -----------------------------------------------------------------------

    /**
     * Called from the plugin's chat subscriber. Sets one-shot flags consumed by transitions.
     * Never mutates counters here — counters are finalized once in {@link #doComplete()}.
     */
    public void noteMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        lastMessage = message;
        lastProgressAt = System.currentTimeMillis();

        if (activeBridge != null) {
            activeBridge.noteMessage(message);
        }

        String lower = message.toLowerCase();
        if (lower.contains("which route should we take")) {
            routeDialogueReady = true;
        } else if (lower.contains("slow route through mort myre swamp")) {
            routeSelected = true;
        } else if (lower.contains("middle of an escort")) {
            escortActive = true;
        } else if (lower.contains("you manage to evade the enemies")) {
            evadeSucceeded = true;
        } else if (lower.contains("you throw the vine up")) {
            vineAttached = true;
        } else if (lower.contains("you reach your destination")) {
            destinationReached = true;
        } else if (lower.contains("gives you a reward token")
                || lower.contains("here's your reward")) {
            rewardCollected = true;
        }
        publishOverlaySnapshot();
    }

    // --- Detection helpers ----------------------------------------------------------------------

    private boolean hasFollower() {
        return findFollower() != null;
    }

    private Rs2NpcModel findFollower() {
        // startSide is only a hint for the very first pickup. Once one trek is done we must
        // accept followers at either hub so the plugin can run back and forth indefinitely.
        if (treksCompleted == 0 && config != null
                && config.startSide() != TempleTrekkingStartSide.AUTO) {
            int required = config.startSide() == TempleTrekkingStartSide.PATERDOMUS
                    ? REGION_PATERDOMUS : REGION_BURGH_DE_ROTT;
            if (currentRegion() != required) {
                return null;
            }
        }
        // 20 tiles missed followers spawned near the far edge of either hub. A wider query finds
        // the next companion as soon as the previous trek drops us at the other end.
        return Microbot.getRs2NpcCache().query()
                .withNameContains("(easy)")
                .within(35)
                .nearest();
    }

    /** The Evade-event path only spawns for combat encounters and is never part of a bridge. */
    private boolean hasEvadePath() {
        for (int id : EVADE_PATHS) {
            if (nearestObject(id) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCombatEvent() {
        // The Evade path is the definitive combat marker. The NPC fallback is only used when no
        // vine/bridge objects are in the scene, so a stale NPC cannot steal a puzzle event.
        return hasEvadePath()
                || (hasCombatNpcs() && !hasVine() && !plankBridge.isPresent()
                    && !logBridge.isPresent());
    }

    /** Combat takeover guard; never drag a dead player back into an encounter. */
    private boolean shouldHandleCombat() {
        return !isLocalPlayerDead() && hasCombatEvent();
    }

    /** Log bridge wins when both variants appear to be present: a choppable tree defines it. */
    private boolean hasLogBridgeEvent() {
        return !hasVine() && !hasCombatEvent()
                && logBridge.isPresent() && !plankBridge.hasLumberjack();
    }

    private boolean hasPlankBridgeEvent() {
        return !hasVine() && !hasCombatEvent()
                && plankBridge.isPresent() && !logBridge.hasChoppableTree();
    }

    private boolean hasVine() {
        for (int id : VINE_OBJECTS) {
            if (nearestObject(id) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCombatNpcs() {
        // Undead Lumberjacks are bridge workers, not a generic combat event.
        if (plankBridge.hasLumberjack()) {
            return false;
        }
        return Microbot.getRs2NpcCache().query().withIds(COMBAT_NPCS).nearest() != null;
    }

    private Rs2TileObjectModel nearestObject(int id) {
        return Microbot.getRs2TileObjectCache().query().withId(id).nearest();
    }

    private boolean interactObject(int[] ids, String action) {
        for (int id : ids) {
            Rs2TileObjectModel object = nearestObject(id);
            if (object != null && object.click(action)) {
                return true;
            }
        }
        return Rs2GameObject.interact(ids, action);
    }

    private boolean isAtHub() {
        int region = currentRegion();
        return region == REGION_PATERDOMUS || region == REGION_BURGH_DE_ROTT;
    }

    private boolean shouldRedeemRewards() {
        return config != null && config.redeemRewards() && isAtHub()
                && rewardHandler.tokenCount() >= config.rewardThreshold();
    }

    private boolean isInTrekScene() {
        int region = currentRegion();
        return region > 0 && region != REGION_PATERDOMUS && region != REGION_BURGH_DE_ROTT;
    }

    private int currentRegion() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc == null ? -1 : loc.getRegionID();
    }

    private boolean isLocalPlayerDead() {
        if (Microbot.getClient() == null) {
            return false;
        }
        Player local = Microbot.getClient().getLocalPlayer();
        return local != null && local.isDead();
    }

    private void updateSide() {
        int region = currentRegion();
        if (region == REGION_PATERDOMUS) {
            currentSide = "Paterdomus";
        } else if (region == REGION_BURGH_DE_ROTT) {
            currentSide = "Burgh de Rott";
        } else {
            currentSide = "Trek instance";
        }
    }

    private boolean stalled() {
        long now = System.currentTimeMillis();
        return now - lastProgressAt > PROGRESS_TIMEOUT_MS
                || now - stateEnteredAt > STATE_TIMEOUT_MS;
    }

    private boolean canAct() {
        return System.currentTimeMillis() - lastActionAt >= ACTION_COOLDOWN_MS;
    }

    private void markAction() {
        lastActionAt = System.currentTimeMillis();
        lastProgressAt = lastActionAt;
    }

    private void maybeEat() {
        if (config == null || config.foodName().isEmpty()) {
            return;
        }
        if (Rs2Player.getHealthPercentage() < config.eatAtHpPercent()
                && canAct() && Rs2Inventory.interact(config.foodName(), "Eat")) {
            markAction();
        }
    }

    private void warnThrottled(String message) {
        if (System.currentTimeMillis() - lastWarningAt > 15000) {
            log.warn("[TempleTrekking] {}", message);
            lastWarningAt = System.currentTimeMillis();
        }
    }

    // --- Overlay / debug snapshot ---------------------------------------------------------------

    String getRuntimeStatus() {
        State current = getCurrentState();
        return current != null ? current.name() : "NOT_INITIALIZED";
    }

    OverlaySnapshot getOverlaySnapshot() {
        publishOverlaySnapshot();
        return overlaySnapshot;
    }

    private void publishOverlaySnapshot() {
        State current = getCurrentState();
        overlaySnapshot = new OverlaySnapshot(
                current != null ? current.name() : "NOT_INITIALIZED",
                currentSide,
                currentEvent,
                treksCompleted,
                eventsHandled,
                config != null ? config.maxTreks() : 0,
                rewardHandler.tokenCount(),
                config != null && config.redeemRewards() ? config.rewardThreshold() : 0,
                lastMessage,
                stalled()
        );
    }

    static final class OverlaySnapshot {
        final String state;
        final String side;
        final String event;
        final int treksCompleted;
        final int eventsHandled;
        final int maxTreks;
        final int rewardTokens;
        final int rewardTarget;
        final String lastMessage;
        final boolean stalled;

        private OverlaySnapshot(String state, String side, String event, int treksCompleted,
                                int eventsHandled, int maxTreks, int rewardTokens, int rewardTarget,
                                String lastMessage, boolean stalled) {
            this.state = state;
            this.side = side;
            this.event = event;
            this.treksCompleted = treksCompleted;
            this.eventsHandled = eventsHandled;
            this.maxTreks = maxTreks;
            this.rewardTokens = rewardTokens;
            this.rewardTarget = rewardTarget;
            this.lastMessage = lastMessage;
            this.stalled = stalled;
        }

        static OverlaySnapshot initial() {
            return new OverlaySnapshot("NOT_INITIALIZED", "Unknown", "None", 0, 0, 0, 0, 0, "", false);
        }
    }
}
