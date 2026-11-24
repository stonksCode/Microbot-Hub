package net.runelite.client.plugins.microbot.templetrekking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Bridge version A: the Undead Lumberjack / Plank encounter.
 *
 * Its repaired stages are the RAMBLE_BRIDGE_BEAM_FIX_* ids, NOT the TEMPLETREK_BRIDGE_FIXED_*
 * ids used by the log version. Keeping those two progressions in separate classes is the point
 * of this rewrite — no material or object ID is shared between them.
 */
@Slf4j
final class TempleTrekkingPlankBridgeHandler implements TempleTrekkingBridgeHandler {
    private static final int BROKEN_BRIDGE = ObjectID.TEMPLETREK_BRIDGE_BROKEN;
    private static final int[] BEAM_BRIDGES = {
            ObjectID.RAMBLE_BRIDGE_BEAM_FIX_1,
            ObjectID.RAMBLE_BRIDGE_BEAM_FIX_2,
            ObjectID.RAMBLE_BRIDGE_BEAM_FIX_3
    };
    private static final int[] BRIDGE_OBJECTS = {
            BROKEN_BRIDGE,
            BEAM_BRIDGES[0], BEAM_BRIDGES[1], BEAM_BRIDGES[2]
    };
    private static final int[] CONTINUE_PATHS = {
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH,
            ObjectID.TEMPLETREK_SWAMP_SUCCESS_PATH_BROWN,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_1,
            ObjectID.TEMPLETREK_SWAMP_CONTINUE_2
    };
    private static final String[] OUTFIT_DROPS = {
            "Lumberjack hat", "Lumberjack top", "Lumberjack legs",
            "Lumberjack gloves", "Lumberjack boots"
    };
    private static final int REQUIRED_PLANKS = 3;
    private static final int MAX_CROSS_ATTEMPTS = 12;
    private static final int MAX_USE_FAILURES = 3;

    private final TempleTrekkingBridgeContext context;

    private int beamsPlaced;
    private int useFailures;
    private int crossAttempts;
    private boolean crossed;
    private boolean solved;
    private boolean outfitPieceLooted;
    private String detail = "waiting";

    TempleTrekkingPlankBridgeHandler(TempleTrekkingBridgeContext context) {
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
        // Require the bridge object itself. A lone Undead Lumberjack (or, worse, follower chat)
        // must never pull the state machine out of the vine event.
        return true;
    }

    boolean hasLumberjack() {
        return Microbot.getRs2NpcCache().query()
                .withNameContains("Undead Lumberjack")
                .nearest() != null;
    }

    @Override
    public void enter() {
        reset();
        log.info("[TempleTrekking] Entered plank/beam bridge event");
        detail = "gathering planks";
    }

    @Override
    public void reset() {
        beamsPlaced = 0;
        useFailures = 0;
        crossAttempts = 0;
        crossed = false;
        solved = false;
        outfitPieceLooted = false;
        detail = "waiting";
    }

    @Override
    public boolean isSolved() {
        return solved;
    }

    @Override
    public String describe() {
        return "Bridge - Undead Lumberjacks / planks (" + detail + ")";
    }

    @Override
    public void noteMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String lower = message.toLowerCase();
        if (lower.contains("place a beam down") || lower.contains("tap the plank into place")) {
            int stage = stageFromScene();
            if (stage > beamsPlaced) {
                beamsPlaced = stage;
            }
            detail = "beams placed " + beamsPlaced + "/" + REQUIRED_PLANKS;
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

        // Follower chatter and the "Attempt to fix the bridge?" confirmation both block clicks.
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
        if (stage > beamsPlaced) {
            beamsPlaced = stage;
        }
        if (beamsPlaced >= REQUIRED_PLANKS || stage >= REQUIRED_PLANKS) {
            cross(bridge);
            return;
        }

        if (loot()) {
            detail = "looting drops";
            return;
        }

        boolean farmLumberjacks = context.config() != null && context.config().farmLumberjacks();
        int planks = Rs2Inventory.count(ItemID.WOODPLANK);
        int needed = REQUIRED_PLANKS - beamsPlaced;

        // Farming stops as soon as one outfit piece has been picked up this encounter: only one
        // piece drops per lumberjack event, so extra kills would not improve the drop.
        if (farmLumberjacks && !outfitPieceLooted && attackLumberjack()) {
            detail = "farming lumberjack outfit";
            return;
        }
        if (planks < needed && attackLumberjack()) {
            return;
        }
        if (planks < needed) {
            detail = "waiting for planks";
            return;
        }

        usePlank(stage);
    }

    /** Loot planks first. Outfit pieces are only picked up when farming is explicitly enabled. */
    private boolean loot() {
        if (Rs2GroundItem.take(ItemID.WOODPLANK)) {
            context.markAction();
            return true;
        }
        boolean farmLumberjacks = context.config() != null && context.config().farmLumberjacks();
        if (!farmLumberjacks || outfitPieceLooted) {
            return false;
        }
        for (String drop : OUTFIT_DROPS) {
            if (Rs2GroundItem.take(drop, 12)) {
                outfitPieceLooted = true;
                detail = "outfit piece picked up";
                log.info("[TempleTrekking] Picked up lumberjack outfit piece: {}", drop);
                context.markAction();
                return true;
            }
        }
        return false;
    }

    private boolean attackLumberjack() {
        Rs2NpcModel lumberjack = Microbot.getRs2NpcCache().query()
                .withNameContains("Undead Lumberjack")
                .nearest();
        if (lumberjack != null && context.canAct() && lumberjack.click("Attack")) {
            detail = "attacking Undead Lumberjack";
            context.markAction();
            return true;
        }
        return false;
    }

    private void usePlank(int stage) {
        Rs2TileObjectModel target = bridgeForStage(stage);
        if (target == null || !context.canAct()) {
            return;
        }
        if (Rs2Inventory.isItemSelected()) {
            Rs2Inventory.deselect();
        }
        if (Rs2Inventory.useItemOnObject(ItemID.WOODPLANK, target.getId())) {
            useFailures = 0;
            detail = "placing plank " + (beamsPlaced + 1) + "/" + REQUIRED_PLANKS;
            context.markAction();
            log.info("[TempleTrekking] Used Plank on beam bridge stage {}", stage);
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
            context.warn("Plank bridge fixed but Cross did not resolve");
            return;
        }
        Rs2TileObjectModel fixed = bridgeForStage(REQUIRED_PLANKS);
        if (fixed == null) {
            fixed = bridge;
        }
        if (context.canAct()) {
            boolean clicked = fixed.click("Cross");
            if (!clicked) {
                clicked = context.interactObject(new int[]{BEAM_BRIDGES[2]}, "Cross");
            }
            if (clicked) {
                crossAttempts++;
                detail = "crossing (" + crossAttempts + ")";
                context.markAction();
                log.info("[TempleTrekking] Clicked Cross on fixed plank bridge (attempt {})",
                        crossAttempts);
            }
        }
    }

    private void continueTrek() {
        if (context.canAct() && context.interactObject(CONTINUE_PATHS, "Continue-trek")) {
            solved = true;
            detail = "cleared";
            context.markAction();
            log.info("[TempleTrekking] Plank bridge event cleared");
        }
    }

    private int stageFromScene() {
        Rs2TileObjectModel bridge = nearestBridge();
        if (bridge == null) {
            return beamsPlaced;
        }
        int id = bridge.getId();
        if (id == BROKEN_BRIDGE) {
            return 0;
        }
        for (int i = 0; i < BEAM_BRIDGES.length; i++) {
            if (id == BEAM_BRIDGES[i]) {
                return i + 1;
            }
        }
        return beamsPlaced;
    }

    private Rs2TileObjectModel bridgeForStage(int stage) {
        if (stage <= 0) {
            return nearestObject(BROKEN_BRIDGE);
        }
        if (stage >= REQUIRED_PLANKS) {
            return nearestObject(BEAM_BRIDGES[2]);
        }
        return nearestObject(BEAM_BRIDGES[stage - 1]);
    }

    private Rs2TileObjectModel nearestBridge() {
        return Microbot.getRs2TileObjectCache().query().withIds(BRIDGE_OBJECTS).nearest();
    }

    private Rs2TileObjectModel nearestObject(int id) {
        return Microbot.getRs2TileObjectCache().query().withId(id).nearest();
    }
}
