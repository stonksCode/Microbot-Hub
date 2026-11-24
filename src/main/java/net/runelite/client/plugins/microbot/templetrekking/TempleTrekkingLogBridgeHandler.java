package net.runelite.client.plugins.microbot.templetrekking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Bridge version B: the Dead tree / Logs encounter.
 *
 * Its repaired stages are the TEMPLETREK_BRIDGE_FIXED_* ids. No plank/beam object or item is
 * referenced here; this class is the complete, separate implementation of the log event.
 */
@Slf4j
final class TempleTrekkingLogBridgeHandler implements TempleTrekkingBridgeHandler {
    private static final int BROKEN_BRIDGE = ObjectID.TEMPLETREK_BRIDGE_BROKEN;
    private static final int[] LOG_BRIDGES = {
            ObjectID.TEMPLETREK_BRIDGE_FIXED_1,
            ObjectID.TEMPLETREK_BRIDGE_FIXED_2,
            ObjectID.TEMPLETREK_BRIDGE_FIXED_3
    };
    private static final int[] BRIDGE_OBJECTS = {
            BROKEN_BRIDGE,
            LOG_BRIDGES[0], LOG_BRIDGES[1], LOG_BRIDGES[2]
    };
    private static final int[] CHOP_TREES = {
            ObjectID.DEADTREE2_SWAMP, ObjectID.DEADTREE2_DARK
    };
    private static final int[] TREE_STUMPS = {
            ObjectID.DEADTREE2_STUMP_SWAMP, ObjectID.DEADTREE2_STUMP_DARK
    };
    private static final int[] CONTINUE_PATHS = {
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH,
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH_BROWN,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_1,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_2
    };
    private static final int REQUIRED_LOGS = 3;
    private static final int MAX_CROSS_ATTEMPTS = 12;
    private static final int MAX_USE_FAILURES = 3;

    private final TempleTrekkingBridgeContext context;

    private int logsPlaced;
    private int useFailures;
    private int crossAttempts;
    private boolean crossed;
    private boolean solved;
    private String detail = "waiting";

    TempleTrekkingLogBridgeHandler(TempleTrekkingBridgeContext context) {
        this.context = context;
    }

    @Override
    public boolean isPresent() {
        Rs2TileObjectModel bridge = nearestBridge();
        if (bridge == null) {
            // The instance has moved on. Forget the completed event so a later bridge can enter.
            if (solved) {
                reset();
            }
            return false;
        }
        // The fixed object can linger after Cross/Continue-trek. Keep the latch until it despawns.
        if (solved) {
            return false;
        }
        return hasChoppableTree() || hasStump() || stageFromScene() > 0;
    }

    boolean hasChoppableTree() {
        for (int id : CHOP_TREES) {
            if (nearestObject(id) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStump() {
        for (int id : TREE_STUMPS) {
            if (nearestObject(id) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void enter() {
        reset();
        log.info("[TempleTrekking] Entered Dead tree / log bridge event");
        detail = "gathering logs";
    }

    @Override
    public void reset() {
        logsPlaced = 0;
        useFailures = 0;
        crossAttempts = 0;
        crossed = false;
        solved = false;
        detail = "waiting";
    }

    @Override
    public boolean isSolved() {
        return solved;
    }

    @Override
    public String describe() {
        return "Bridge - Dead trees / logs (" + detail + ")";
    }

    @Override
    public void noteMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String lower = message.toLowerCase();
        if (lower.contains("place a log down") || lower.contains("tap the log into place")) {
            int stage = stageFromScene();
            if (stage > logsPlaced) {
                logsPlaced = stage;
            }
            detail = "logs placed " + logsPlaced + "/" + REQUIRED_LOGS;
            context.noteProgress();
        } else if (lower.contains("you walk across the fixed bridge")) {
            crossed = true;
            detail = "continuing trek";
            context.noteProgress();
        }
    }

    @Override
    public void step() {
        if (solved) {
            return;
        }

        context.maybeEat();

        if (context.clearDialogue()) {
            detail = "clearing dialogue";
            return;
        }

        if (crossed) {
            continueTrek();
            return;
        }

        Rs2TileObjectModel bridge = nearestBridge();
        if (bridge == null) {
            detail = "waiting for bridge";
            return;
        }

        int stage = stageFromScene();
        if (stage > logsPlaced) {
            logsPlaced = stage;
        }
        if (logsPlaced >= REQUIRED_LOGS || stage >= REQUIRED_LOGS) {
            cross(bridge);
            return;
        }

        int needed = REQUIRED_LOGS - logsPlaced;
        if (Rs2Inventory.count(ItemID.LOGS) < needed) {
            if (chopTree()) {
                return;
            }
            detail = "waiting for logs";
            return;
        }

        useLog(stage);
    }

    private boolean chopTree() {
        for (int id : CHOP_TREES) {
            Rs2TileObjectModel tree = nearestObject(id);
            if (tree != null && context.canAct() && tree.click("Chop down")) {
                detail = "chopping Dead tree";
                context.markAction();
                log.info("[TempleTrekking] Chopping Dead tree for bridge logs");
                return true;
            }
        }
        return false;
    }

    private void useLog(int stage) {
        Rs2TileObjectModel target = bridgeForStage(stage);
        if (target == null || !context.canAct()) {
            return;
        }
        if (Rs2Inventory.isItemSelected()) {
            Rs2Inventory.deselect();
        }
        if (Rs2Inventory.useItemOnObject(ItemID.LOGS, target.getId())) {
            useFailures = 0;
            detail = "placing log " + (logsPlaced + 1) + "/" + REQUIRED_LOGS;
            context.markAction();
            log.info("[TempleTrekking] Used Logs on log bridge stage {}", stage);
            return;
        }
        useFailures++;
        if (useFailures >= MAX_USE_FAILURES) {
            Rs2Inventory.deselect();
            useFailures = 0;
        }
        context.markAction();
    }

    private void cross(Rs2TileObjectModel bridge) {
        if (crossAttempts >= MAX_CROSS_ATTEMPTS) {
            context.warn("Log bridge fixed but Cross did not resolve");
            return;
        }
        Rs2TileObjectModel fixed = bridgeForStage(REQUIRED_LOGS);
        if (fixed == null) {
            fixed = bridge;
        }
        if (context.canAct()) {
            boolean clicked = fixed.click("Cross");
            if (!clicked) {
                clicked = context.interactObject(new int[]{LOG_BRIDGES[2]}, "Cross");
            }
            if (clicked) {
                crossAttempts++;
                detail = "crossing (" + crossAttempts + ")";
                context.markAction();
                log.info("[TempleTrekking] Clicked Cross on fixed log bridge (attempt {})",
                        crossAttempts);
            }
        }
    }

    private void continueTrek() {
        if (context.canAct() && context.interactObject(CONTINUE_PATHS, "Continue-trek")) {
            solved = true;
            detail = "cleared";
            context.markAction();
            log.info("[TempleTrekking] Log bridge event cleared");
        }
    }

    private int stageFromScene() {
        Rs2TileObjectModel bridge = nearestBridge();
        if (bridge == null) {
            return logsPlaced;
        }
        int id = bridge.getId();
        if (id == BROKEN_BRIDGE) {
            return 0;
        }
        for (int i = 0; i < LOG_BRIDGES.length; i++) {
            if (id == LOG_BRIDGES[i]) {
                return i + 1;
            }
        }
        return logsPlaced;
    }

    private Rs2TileObjectModel bridgeForStage(int stage) {
        if (stage <= 0) {
            return nearestObject(BROKEN_BRIDGE);
        }
        if (stage >= REQUIRED_LOGS) {
            return nearestObject(LOG_BRIDGES[2]);
        }
        return nearestObject(LOG_BRIDGES[stage - 1]);
    }

    private Rs2TileObjectModel nearestBridge() {
        return Microbot.getRs2TileObjectCache().query().withIds(BRIDGE_OBJECTS).nearest();
    }

    private Rs2TileObjectModel nearestObject(int id) {
        return Microbot.getRs2TileObjectCache().query().withId(id).nearest();
    }
}
