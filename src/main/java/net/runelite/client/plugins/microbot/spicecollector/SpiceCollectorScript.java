package net.runelite.client.plugins.microbot.spicecollector;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends a cat into the Hell-Rat Behemoth alcoves in Evil Dave's Basement of Doom
 * and collects the 4-dose spice that is awarded after every kill.
 */
@Slf4j
public class SpiceCollectorScript extends Script {

    /**
     * The four alcove curtains in the Basement of Doom. The gameval name is
     * {@code _100_DAVE_CRYPT_CURTAINS_BIGRAT} and its value is 539.
     */
    private static final int CURTAIN_ID = ObjectID._100_DAVE_CRYPT_CURTAINS_BIGRAT;

    /**
     * Hell-Rat Behemoth. This NPC is visible in the main basement as well as in the fight
     * instance, so its presence alone must never be used to detect the start of a fight.
     */
    private static final int BEHEMOTH_ID = 6793;

    /** Roughly the middle of the Basement of Doom, used when routing in from outside. */
    private static final WorldPoint BASEMENT_CENTER = new WorldPoint(3075, 9888, 0);

    private static final long LOOP_DELAY_MS = 600L;
    private static final long CURTAIN_RETRY_MS = 3_000L;
    private static final long ENTER_TIMEOUT_MS = 15_000L;
    private static final long FIGHT_START_TIMEOUT_MS = 30_000L;
    /** The post-insert "If you walk away..." prompt can appear a tick after the instance does. */
    private static final long FIGHT_START_DIALOGUE_GRACE_MS = 5_000L;
    private static final long FIGHT_END_GRACE_MS = 2_000L;
    private static final long FINISH_SETTLE_MS = 1_500L;
    private static final long FIGHT_LINGER_TIMEOUT_MS = 8_000L;
    private static final int MAX_FIGHTS_WITHOUT_SPICE = 3;

    /**
     * The curtain's "too big to enter" dialogue is a sprite dialogue (widget 193/2).
     * {@link Rs2Dialogue#hasContinue()} does not recognise it, so it has to be advanced
     * with a key press instead.
     */
    private static final int SPRITE_DIALOGUE_GROUP = 193;
    private static final int SPRITE_DIALOGUE_TEXT_CHILD = 2;
    private static final long DIALOGUE_ADVANCE_MS = 900L;

    private static final long TRACE_PERIOD_MS = 1_000L;

    private static final int[] CAT_ITEM_IDS = {
            ItemID.KITTENOBJECT,
            ItemID.KITTENOBJECT_LIGHT,
            ItemID.KITTENOBJECT_BROWN,
            ItemID.KITTENOBJECT_BLACK,
            ItemID.KITTENOBJECT_BROWNGREY,
            ItemID.KITTENOBJECT_BLUEGREY,
            ItemID.KITTENOBJECT_HELL,
            ItemID.GROWNCATOBJECT,
            ItemID.GROWNCATOBJECT_LIGHT,
            ItemID.GROWNCATOBJECT_BROWN,
            ItemID.GROWNCATOBJECT_BLACK,
            ItemID.GROWNCATOBJECT_BROWNGREY,
            ItemID.GROWNCATOBJECT_BLUEGREY,
            ItemID.GROWNCATOBJECT_HELL
    };

    private static final String[] CAT_ITEM_NAME_PARTS = {
            "kitten", "pet cat", "overgrown cat", "lazy cat", "wily cat", "hellcat"
    };

    /** Name fragments used to find the follower NPC. "kitten" does not contain "cat". */
    private static final String[] CAT_NPC_NAME_PARTS = {"cat", "kitten"};

    @Getter
    private volatile SpiceCollectorState state = SpiceCollectorState.IDLE;
    @Getter
    private volatile String stopReason = "";
    @Getter
    private volatile SpiceType spiceType = SpiceType.RED;
    @Getter
    private volatile int targetSpices = 0;
    @Getter
    private volatile int collectedSpices = 0;
    @Getter
    private volatile int fightsCompleted = 0;
    @Getter
    private volatile String catName = "";
    @Getter
    private volatile int catHealthPercent = -1;
    @Getter
    private volatile int foodCount = 0;

    private int startSpiceCount = 0;
    private int spiceCountBeforeFight = 0;
    private int foodId = -1;
    private int failedFights = 0;

    private long lastCurtainClickAt = 0L;
    private long lastDialogueAdvanceAt = 0L;
    private long enteringStartedAt = 0L;
    private long waitingStartedAt = 0L;
    private long fightEndedAt = 0L;
    private long finishingStartedAt = 0L;
    private long fightStartedAt = 0L;
    private long lastFeedAt = 0L;
    private long catMissingSince = 0L;

    private long lastTraceAt = 0L;
    private long lastCurtainTraceAt = 0L;
    private long lastFightTraceAt = 0L;

    private volatile String stopAfterFightReason = "";

    public boolean run(SpiceCollectorConfig config) {
        SpiceCollectorDebug.configure(config.debug());

        spiceType = config.spiceType();
        targetSpices = config.targetSpices();
        startSpiceCount = Rs2Inventory.count(spiceType.getFourDoseItemId());
        collectedSpices = 0;
        fightsCompleted = 0;
        failedFights = 0;
        foodId = resolveFoodId(config);
        foodCount = foodId > 0 ? Rs2Inventory.count(foodId) : 0;
        catName = "";
        catHealthPercent = -1;
        state = SpiceCollectorState.IDLE;
        stopReason = "";
        stopAfterFightReason = "";
        lastCurtainClickAt = 0L;
        lastDialogueAdvanceAt = 0L;
        enteringStartedAt = 0L;
        waitingStartedAt = 0L;
        fightEndedAt = 0L;
        finishingStartedAt = 0L;
        fightStartedAt = 0L;
        lastFeedAt = 0L;
        catMissingSince = 0L;
        lastTraceAt = 0L;
        lastCurtainTraceAt = 0L;
        lastFightTraceAt = 0L;

        Microbot.log("[SpiceCollector] Collecting " + spiceType.getItemName()
                + (targetSpices > 0 ? " until " + targetSpices + " spices." : " endlessly."));
        SpiceCollectorDebug.trace("run: spice=%s target=%s foodName=%s foodId=%s foodCount=%s startSpiceCount=%s",
                spiceType, targetSpices, config.foodName(), foodId, foodCount, startSpiceCount);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Microbot.pauseAllScripts.get()) return;

                updateStats(config);
                traceTick();

                switch (state) {
                    case IDLE:
                        handleIdle(config);
                        break;
                    case WALKING_TO_CURTAIN:
                        handleWalkingToCurtain(config);
                        break;
                    case ENTERING:
                        handleEntering(config);
                        break;
                    case WAITING_FOR_FIGHT:
                        handleWaitingForFight(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                    case FINISHING:
                        handleFinishing(config);
                        break;
                    case STOPPED:
                        return;
                }
            } catch (Exception ex) {
                log.error("[SpiceCollector] loop error: {}", ex.getMessage(), ex);
                SpiceCollectorDebug.trace("loop error: %s", ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleIdle(SpiceCollectorConfig config) {
        Microbot.status = "Spice: checking setup";

        if (targetReached()) {
            requestStop("Collected " + targetSpices + " " + spiceType.getItemName() + ".");
            return;
        }

        if (!hasAnyCat()) {
            long now = System.currentTimeMillis();
            if (catMissingSince == 0L) {
                catMissingSince = now;
                SpiceCollectorDebug.trace("setup: no cat found yet");
            } else if (now - catMissingSince >= 6_000L) {
                requestStop("No cat is following you or in your inventory.");
                return;
            }
            Microbot.status = "Spice: waiting for cat";
            return;
        }
        catMissingSince = 0L;

        if (!hasFood(config)) {
            requestStop("Out of " + config.foodName() + ".");
            return;
        }

        if (Rs2Inventory.isFull()) {
            requestStop("Inventory full.");
            return;
        }

        setState(SpiceCollectorState.WALKING_TO_CURTAIN, "setup ok");
    }

    private void handleWalkingToCurtain(SpiceCollectorConfig config) {
        if (targetReached()) {
            requestStop("Collected " + targetSpices + " " + spiceType.getItemName() + ".");
            return;
        }

        if (config.stopWhenOutOfFood() && !hasFood(config)) {
            requestStop("Out of " + config.foodName() + ".");
            return;
        }

        Rs2TileObjectModel curtain = findCurtain();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        if (curtain == null) {
            if (playerLocation.distanceTo(BASEMENT_CENTER) > 12) {
                Microbot.status = "Spice: walking to the Basement of Doom";
                Rs2Walker.walkTo(BASEMENT_CENTER);
            } else {
                Microbot.status = "Spice: walking to the " + spiceType.getItemName() + " alcove";
                Rs2Walker.walkTo(spiceType.getLocation());
            }
            return;
        }

        int distance = curtain.getWorldLocation().distanceTo(playerLocation);
        if (distance > 3) {
            Microbot.status = "Spice: walking to the " + spiceType.getItemName() + " curtain";
            Rs2Walker.walkTo(curtain.getWorldLocation(), 2);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCurtainClickAt < CURTAIN_RETRY_MS) {
            return;
        }
        lastCurtainClickAt = now;

        Microbot.status = "Spice: opening the " + spiceType.getItemName() + " curtain";
        SpiceCollectorDebug.trace("curtain click: id=%s action=Enter loc=%s distance=%s player=%s",
                CURTAIN_ID, curtain.getWorldLocation(), distance, playerLocation);
        if (!curtain.click("Enter")) {
            SpiceCollectorDebug.trace("curtain click: Enter failed; will retry");
            Microbot.log("[SpiceCollector] Could not click the curtain; retrying.");
            return;
        }

        enteringStartedAt = now;
        setState(SpiceCollectorState.ENTERING, "curtain Enter clicked");
    }

    private void handleEntering(SpiceCollectorConfig config) {
        // The instance can be entered before the post-insert dialogue is dismissed. Never
        // treat that as "fighting" while a dialogue is still open.
        if (handleDialogueBeforeFight()) {
            return;
        }

        if (fightDetected()) {
            startFight(config);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - enteringStartedAt >= ENTER_TIMEOUT_MS) {
            Microbot.log("[SpiceCollector] The alcove did not open; retrying.");
            setState(SpiceCollectorState.WALKING_TO_CURTAIN, "enter timed out");
            lastCurtainClickAt = 0L;
            return;
        }

        if (!Rs2Dialogue.isInDialogue() && now - lastCurtainClickAt >= CURTAIN_RETRY_MS) {
            Rs2TileObjectModel curtain = findCurtain();
            if (curtain != null && curtain.click("Enter")) {
                lastCurtainClickAt = now;
                SpiceCollectorDebug.trace("curtain click: retried Enter at %s", curtain.getWorldLocation());
            }
        }
    }

    private void handleWaitingForFight(SpiceCollectorConfig config) {
        if (handleDialogueBeforeFight()) {
            return;
        }

        if (fightDetected()) {
            startFight(config);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - waitingStartedAt >= FIGHT_START_TIMEOUT_MS) {
            Microbot.log("[SpiceCollector] The fight did not start; returning to the curtain.");
            setState(SpiceCollectorState.WALKING_TO_CURTAIN, "fight never started");
            lastCurtainClickAt = 0L;
            return;
        }

        Microbot.status = "Spice: waiting for the behemoth";
    }

    /**
     * Returns true while any dialogue is still open before the fight starts. The curtain's
     * follow-up prompt ("If you walk away...") appears after the client enters the instance,
     * so the instance flag alone is not enough to begin feeding.
     */
    private boolean handleDialogueBeforeFight() {
        boolean dialogueVisible = Rs2Dialogue.isInDialogue() || !spriteDialogueText().isEmpty();
        if (!dialogueVisible) {
            return false;
        }

        if (handleInsertDialogue()) {
            return true;
        }

        // A dialogue is open but not one of the known curtain dialogues. Do not start the
        // fight underneath it; the loops below still apply their own timeouts.
        SpiceCollectorDebug.trace("dialogue: visible but unhandled; waiting before fight start");
        return true;
    }

    /**
     * Handles both curtain dialogue paths: a cat in the inventory and a cat following the player.
     * Returns true when a dialogue action was taken.
     */
    private boolean handleInsertDialogue() {
        boolean select = Rs2Dialogue.hasSelectAnOption();
        boolean cont = Rs2Dialogue.hasContinue();
        String options = dialogueOptions();
        String sprite = spriteDialogueText();

        SpiceCollectorDebug.trace("dialogue: select=%s cont=%s options=[%s] sprite=[%s] text=[%s]",
                select, cont, options, sprite, dialogueText());

        if (select) {
            // Cat in the inventory, or a following cat: "Insert your cat (...)".
            if (Rs2Dialogue.hasDialogueOption("Insert your cat")) {
                Microbot.status = "Spice: inserting the cat";
                boolean clicked = Rs2Dialogue.clickOption("Insert your cat");
                SpiceCollectorDebug.trace("dialogue: clickOption(Insert your cat) -> %s", clicked);
                if (clicked) {
                    waitingStartedAt = System.currentTimeMillis();
                    lastDialogueAdvanceAt = 0L;
                    setState(SpiceCollectorState.WAITING_FOR_FIGHT, "insert option clicked");
                    return true;
                }
            }

            // Some companion setups only offer a plain prompt to enter the encounter.
            if (Rs2Dialogue.hasDialogueOption("Enter the encounter")) {
                boolean clicked = Rs2Dialogue.clickOption("Enter the encounter");
                SpiceCollectorDebug.trace("dialogue: clickOption(Enter the encounter) -> %s", clicked);
                if (clicked) {
                    waitingStartedAt = System.currentTimeMillis();
                    lastDialogueAdvanceAt = 0L;
                    setState(SpiceCollectorState.WAITING_FOR_FIGHT, "enter encounter option clicked");
                    return true;
                }
            }

            if (Rs2Dialogue.hasDialogueOption("Enter")) {
                boolean clicked = Rs2Dialogue.clickOption("Enter");
                SpiceCollectorDebug.trace("dialogue: clickOption(Enter) -> %s", clicked);
                if (clicked) {
                    waitingStartedAt = System.currentTimeMillis();
                    lastDialogueAdvanceAt = 0L;
                    setState(SpiceCollectorState.WAITING_FOR_FIGHT, "enter option clicked");
                    return true;
                }
            }

            if (Rs2Dialogue.hasDialogueOption("Don't insert your cat")) {
                requestStop("The curtain refused the cat. Is a cat following you?");
                return true;
            }
        }

        if (cont) {
            Rs2Dialogue.clickContinue();
            SpiceCollectorDebug.trace("dialogue: clickContinue()");
            return true;
        }

        // The curtain displays "You're too big to enter the narrow alcove, but your cat
        // could get in." as a sprite dialogue on 193/2. Rs2Dialogue#hasContinue()
        // does not detect that widget, so advance it with a throttled space press.
        if (!sprite.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastDialogueAdvanceAt >= DIALOGUE_ADVANCE_MS) {
                lastDialogueAdvanceAt = now;
                Microbot.status = "Spice: continuing the curtain dialogue";
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                SpiceCollectorDebug.trace("dialogue: keyPress(VK_SPACE) for sprite dialogue");
            }
            return true;
        }

        return false;
    }

    private void startFight(SpiceCollectorConfig config) {
        spiceCountBeforeFight = Rs2Inventory.count(spiceType.getFourDoseItemId());
        fightEndedAt = 0L;
        fightStartedAt = System.currentTimeMillis();
        lastFeedAt = 0L;
        setState(SpiceCollectorState.FIGHTING, "instanced fight detected, dialogue dismissed");
    }

    private void handleFighting(SpiceCollectorConfig config) {
        long now = System.currentTimeMillis();

        // The follow-up prompt can appear a tick after the instance flag flips. If that
        // happened just as we entered FIGHTING, dismiss it before offering any food.
        if (Rs2Dialogue.hasContinue() && now - fightStartedAt < FIGHT_START_DIALOGUE_GRACE_MS) {
            Rs2Dialogue.clickContinue();
            SpiceCollectorDebug.trace("fight: clicked post-insert continue prompt at +%sms",
                    now - fightStartedAt);
            return;
        }

        if (!fightDetected()) {
            if (fightEndedAt == 0L) {
                fightEndedAt = now;
                SpiceCollectorDebug.trace("fight: instance exited at %s", now);
            } else if (now - fightEndedAt >= FIGHT_END_GRACE_MS) {
                finishingStartedAt = now;
                setState(SpiceCollectorState.FINISHING,
                        "instance exited for " + (now - fightEndedAt) + "ms");
            }
            Microbot.status = "Spice: leaving the alcove";
            return;
        }

        fightEndedAt = 0L;
        Microbot.status = "Spice: cat fighting";

        feedCat(config);
    }

    private void feedCat(SpiceCollectorConfig config) {
        long now = System.currentTimeMillis();
        if (now - lastFeedAt < config.feedIntervalMs()) {
            return;
        }

        Rs2NpcModel cat = findCatNpc();
        if (cat != null) {
            catHealthPercent = healthPercent(cat);
            if (catHealthPercent > config.healAtPercent()) {
                SpiceCollectorDebug.trace("feed: skip, %s at %s%% (threshold %s%%)",
                        cat.getName(), catHealthPercent, config.healAtPercent());
                return;
            }
        } else {
            // The inserted cat is not always exposed as a follower/NPC. Feeding through
            // the curtain still works, so treat an unknown health as "needs healing".
            catHealthPercent = -1;
        }

        int currentFoodId = resolveFoodId(config);
        SpiceCollectorDebug.trace("feed: cat=%s hp=%s%% threshold=%s%% foodId=%s foodCount=%s",
                cat == null ? "not found" : cat.getName(), catHealthPercent,
                config.healAtPercent(), currentFoodId,
                currentFoodId > 0 ? Rs2Inventory.count(currentFoodId) : 0);

        if (currentFoodId <= 0) {
            if (config.stopWhenOutOfFood() && stopAfterFightReason.isEmpty()) {
                stopAfterFightReason = "Out of " + config.foodName() + ".";
                Microbot.log("[SpiceCollector] Out of cat food; finishing this fight and stopping.");
                SpiceCollectorDebug.trace("feed: %s", stopAfterFightReason);
            }
            return;
        }

        lastFeedAt = now;
        boolean fed = false;
        if (cat != null) {
            Microbot.status = "Spice: feeding " + cat.getName();
            fed = Rs2Inventory.useItemOnNpc(currentFoodId, cat.getNpc());
            SpiceCollectorDebug.trace("feed: useItemOnNpc(%s, %s) -> %s",
                    currentFoodId, cat.getName(), fed);
        } else {
            Microbot.status = "Spice: feeding the cat through the curtain";
            SpiceCollectorDebug.trace("feed: cat NPC not found; using curtain fallback");
        }

        // Direct feeding was added in 2025. Keep the old curtain method as a fallback.
        if (!fed) {
            boolean curtain = Rs2Inventory.useItemOnObject(currentFoodId, CURTAIN_ID);
            SpiceCollectorDebug.trace("feed: useItemOnObject(%s, curtain %s) -> %s",
                    currentFoodId, CURTAIN_ID, curtain);
            if (curtain) {
                return;
            }
        }
        if (fed) {
            return;
        }

        Microbot.log("[SpiceCollector] Could not feed the cat this tick.");
    }

    private void handleFinishing(SpiceCollectorConfig config) {
        long now = System.currentTimeMillis();
        if (now - finishingStartedAt < FINISH_SETTLE_MS) {
            Microbot.status = "Spice: waiting for the reward";
            return;
        }

        if (fightDetected() && now - finishingStartedAt < FIGHT_LINGER_TIMEOUT_MS) {
            Microbot.status = "Spice: leaving the alcove";
            return;
        }

        int current = Rs2Inventory.count(spiceType.getFourDoseItemId());
        if (current <= spiceCountBeforeFight && Rs2Inventory.emptySlotCount() > 0) {
            Microbot.status = "Spice: picking up dropped spice";
            int spiceId = spiceType.getFourDoseItemId();
            // Loot by item id first: ground item names include the dose, e.g. "Red spice (4)".
            boolean looted = Rs2GroundItem.loot(spiceId, 3)
                    || Rs2GroundItem.loot(spiceType.getItemName(), 3);
            SpiceCollectorDebug.trace("finish: ground spice loot -> %s", looted);
            if (looted) {
                sleep(400, 700);
                current = Rs2Inventory.count(spiceId);
            }
        }

        SpiceCollectorDebug.trace("finish: spice before=%s after=%s baseline=%s fightsWithoutSpice=%s",
                spiceCountBeforeFight, current, startSpiceCount, failedFights);

        if (current > spiceCountBeforeFight) {
            fightsCompleted++;
            failedFights = 0;
        } else {
            failedFights++;
            Microbot.log("[SpiceCollector] No spice was awarded for that fight ("
                    + failedFights + "/" + MAX_FIGHTS_WITHOUT_SPICE + ").");
        }

        updateStats(config);

        if (targetReached()) {
            requestStop("Collected " + targetSpices + " " + spiceType.getItemName() + ".");
            return;
        }

        if (!stopAfterFightReason.isEmpty()) {
            requestStop(stopAfterFightReason);
            return;
        }

        if (failedFights >= MAX_FIGHTS_WITHOUT_SPICE) {
            requestStop("No spice from the last " + failedFights + " fights.");
            return;
        }

        if (config.stopWhenOutOfFood() && !hasFood(config)) {
            requestStop("Out of " + config.foodName() + ".");
            return;
        }

        if (Rs2Inventory.isFull()) {
            requestStop("Inventory full.");
            return;
        }

        setState(SpiceCollectorState.WALKING_TO_CURTAIN, "fight complete");
        lastCurtainClickAt = 0L;
    }

    private void updateStats(SpiceCollectorConfig config) {
        int current = Rs2Inventory.count(spiceType.getFourDoseItemId());
        collectedSpices = Math.max(0, current - startSpiceCount);
        targetSpices = config.targetSpices();

        foodId = resolveFoodId(config);
        foodCount = foodId > 0 ? Rs2Inventory.count(foodId) : 0;

        Rs2NpcModel cat = findCatNpc();
        if (cat != null) {
            String name = cat.getName();
            catName = name == null ? "" : name;
            catHealthPercent = healthPercent(cat);
        } else {
            catHealthPercent = -1;
        }
    }

    private boolean targetReached() {
        return targetSpices > 0 && collectedSpices >= targetSpices;
    }

    private boolean hasFood(SpiceCollectorConfig config) {
        if (foodId <= 0) {
            foodId = resolveFoodId(config);
        }
        return foodId > 0 && Rs2Inventory.count(foodId) > 0;
    }

    private int resolveFoodId(SpiceCollectorConfig config) {
        String name = config.foodName();
        if (name == null || name.trim().isEmpty()) {
            return -1;
        }

        Rs2ItemModel food = Rs2Inventory.get(name.trim(), true);
        if (food == null) {
            food = Rs2Inventory.get(name.trim(), false);
        }
        return food == null ? -1 : food.getId();
    }

    private boolean hasAnyCat() {
        if (findCatNpc() != null) {
            return true;
        }

        if (Rs2Inventory.contains(CAT_ITEM_IDS)) {
            return true;
        }

        for (String namePart : CAT_ITEM_NAME_PARTS) {
            if (Rs2Inventory.get(namePart, false) != null) {
                return true;
            }
        }
        return false;
    }

    private Rs2TileObjectModel findCurtain() {
        List<Rs2TileObjectModel> curtains = Microbot.getRs2TileObjectCache().query()
                .withId(CURTAIN_ID)
                .toList();

        WorldPoint target = spiceType.getLocation();
        Rs2TileObjectModel best = curtains.stream()
                .filter(curtain -> curtain.getWorldLocation() != null)
                .filter(curtain -> curtain.getWorldLocation().getPlane() == target.getPlane())
                .filter(curtain -> curtain.hasAction("Enter"))
                .min(Comparator.comparingInt(curtain -> curtain.getWorldLocation().distanceTo(target)))
                .orElse(null);

        if (SpiceCollectorDebug.isEnabled()) {
            long now = System.currentTimeMillis();
            if (now - lastCurtainTraceAt >= TRACE_PERIOD_MS) {
                lastCurtainTraceAt = now;
                StringBuilder locations = new StringBuilder();
                for (Rs2TileObjectModel curtain : curtains) {
                    if (locations.length() > 0) locations.append(", ");
                    locations.append(curtain.getWorldLocation());
                }
                SpiceCollectorDebug.trace("curtain: found=%s target=%s locations=[%s] selected=%s",
                        curtains.size(), target, locations, best == null ? "none" : best.getWorldLocation());
            }
        }
        return best;
    }

    private static Rs2NpcModel findCatNpc() {
        NPC follower = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getFollower())
                .orElse(null);
        if (follower != null) {
            Rs2NpcModel followerModel = new Rs2NpcModel(follower);
            if (isCatName(followerModel.getName())) {
                return followerModel;
            }
        }

        for (String namePart : CAT_NPC_NAME_PARTS) {
            List<Rs2NpcModel> cats = Microbot.getRs2NpcCache().query()
                    .withNameContains(namePart)
                    .toListOnClientThread();
            for (Rs2NpcModel cat : cats) {
                if (isCatName(cat.getName())) {
                    return cat;
                }
            }
        }
        return null;
    }

    private static boolean isCatName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return (lower.contains("cat") || lower.contains("kitten")) && !lower.contains("rat");
    }

    /**
     * A fight is only running once the client is in the instanced alcove. The Hell-Rat Behemoth
     * NPCs are also visible in the ordinary basement, so they cannot be used as the trigger.
     */
    private boolean fightDetected() {
        boolean instanced = isInstanced();
        if (SpiceCollectorDebug.isEnabled()) {
            long now = System.currentTimeMillis();
            if (now - lastFightTraceAt >= TRACE_PERIOD_MS) {
                lastFightTraceAt = now;
                SpiceCollectorDebug.trace("fightDetected: instance=%s behemothVisible=%s "
                        + "(visible behemoths in the main world do not start a fight)",
                        instanced, behemothPresent());
            }
        }
        return instanced;
    }

    private static boolean isInstanced() {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().isInInstancedRegion())
                .orElse(false);
    }

    private static boolean behemothPresent() {
        Rs2NpcModel behemoth = Microbot.getRs2NpcCache().query()
                .withId(BEHEMOTH_ID)
                .nearest();
        return behemoth != null && !behemoth.isDead();
    }

    private static int healthPercent(Rs2NpcModel npc) {
        if (npc == null) return -1;
        // Rs2NpcModel hops to the client thread internally; raw NPC.getHealthRatio()
        // and getHealthScale() throw "must be called on client thread" off-thread.
        double percentage = npc.getHealthPercentage();
        if (percentage < 0) {
            return -1;
        }
        return Math.min(100, (int) Math.round(percentage));
    }

    private void setState(SpiceCollectorState newState, String reason) {
        if (state == newState) {
            SpiceCollectorDebug.trace("state stays %s (%s)", newState, reason);
            return;
        }
        SpiceCollectorState oldState = state;
        state = newState;
        SpiceCollectorDebug.trace("state %s -> %s (%s) | instance=%s dialogue=%s cat=%s hp=%s spice=%s food=%s",
                oldState, newState, reason, isInstanced(), Rs2Dialogue.isInDialogue(),
                catName, catHealthPercent, collectedSpices, foodCount);
    }

    private void traceTick() {
        if (!SpiceCollectorDebug.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTraceAt < TRACE_PERIOD_MS) {
            return;
        }
        lastTraceAt = now;

        SpiceCollectorDebug.trace("tick: state=%s status=[%s] instance=%s behemoth=%s dialogue=%s "
                        + "select=%s cont=%s options=[%s] sprite=[%s] cat=%s hp=%s spice=%s/%s food=%s stop=[%s]",
                state, Microbot.status, isInstanced(), behemothPresent(), Rs2Dialogue.isInDialogue(),
                Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue.hasContinue(), dialogueOptions(),
                spriteDialogueText(), catName, catHealthPercent, collectedSpices,
                targetSpices > 0 ? targetSpices : "\u221e", foodCount, stopReason);
    }

    private static String dialogueOptions() {
        try {
            List<Widget> options = Rs2Dialogue.getDialogueOptions();
            if (options == null || options.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Widget option : options) {
                String text = option == null ? null : option.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) sb.append(" / ");
                sb.append(text.replace("<br>", " ").trim());
            }
            return sb.toString();
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }

    private static String dialogueText() {
        try {
            String text = Rs2Dialogue.getDialogueText();
            return text == null ? "" : text.replace("<br>", " ").trim();
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }

    /** Returns the visible sprite dialogue text, or an empty string when it is not showing. */
    private static String spriteDialogueText() {
        if (!Rs2Widget.isWidgetVisible(SPRITE_DIALOGUE_GROUP, SPRITE_DIALOGUE_TEXT_CHILD)) {
            return "";
        }
        String text = Rs2Widget.getChildWidgetText(SPRITE_DIALOGUE_GROUP, SPRITE_DIALOGUE_TEXT_CHILD);
        if (text == null) {
            return "";
        }
        return text.replace("<br>", " ").trim();
    }

    private static boolean hasSpriteDialogueText() {
        return !spriteDialogueText().isEmpty();
    }

    public void requestStop(String reason) {
        if (state == SpiceCollectorState.STOPPED) return;
        stopReason = reason;
        Microbot.log("[SpiceCollector] Stopping: " + reason);
        setState(SpiceCollectorState.STOPPED, reason);
    }

    @Override
    public void shutdown() {
        setState(SpiceCollectorState.STOPPED, "shutdown");
        SpiceCollectorDebug.close();
        super.shutdown();
    }
}
