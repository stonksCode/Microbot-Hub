package net.runelite.client.plugins.microbot.templetrekking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * Redeems accumulated reward tokens through the Temple Trekking Rewards interface (274).
 *
 * The interface is opened from a token, one reward option is selected, then the Buy button is
 * pressed. Each token has to be redeemed separately, so once the configured threshold is reached
 * this handler redeems every accumulated token. Widget options are located by their visible text
 * first, with an index-based fallback against the fixed REWARDS/ICONS containers.
 */
@Slf4j
final class TempleTrekkingRewardHandler {

    interface Context {
        TempleTrekkingConfig config();

        boolean canAct();

        void markAction();

        void warn(String message);
    }

    // Temple Trekking and Burgh de Rott Ramble tokens, all three colours.
    private static final int[] TOKEN_IDS = {
            ItemID.TEMPLETREK_BLUE_TOKEN,
            ItemID.TEMPLETREK_YELLOW_TOKEN,
            ItemID.TEMPLETREK_RED_TOKEN,
            ItemID.TEMPLETREK_BLUE_TOKEN_VAR,
            ItemID.TEMPLETREK_YELLOW_TOKEN_VAR,
            ItemID.TEMPLETREK_RED_TOKEN_VAR
    };
    // The left-click option on a token is "Look-at"; older clients called it Claim-reward/Redeem.
    private static final String[] OPEN_ACTIONS = {"Look-at", "Claim-reward", "Redeem", "Exchange"};
    private static final int[] INTERFACE_WIDGETS = {
            InterfaceID.TrekRewards.UNIVERSE,
            InterfaceID.TrekRewards.FRAME,
            InterfaceID.TrekRewards.CONTENTS,
            InterfaceID.TrekRewards.REWARDS,
            InterfaceID.TrekRewards.ICONS,
            InterfaceID.TrekRewards.SIDE_PANEL,
            InterfaceID.TrekRewards.BUYBUTTON
    };
    private static final long BUY_RETRY_MS = 1200;
    private static final long BUY_TIMEOUT_MS = 10000;
    private static final int MAX_SELECT_FAILURES = 5;

    private enum Phase {OPEN, SELECT, BUY}

    private final Context context;

    private Phase phase = Phase.OPEN;
    private boolean done;
    private boolean redeeming;
    private int tokensAtBuy = -1;
    private long buyPhaseStartedAt;
    private long lastBuyAt;
    private int selectFailures;

    TempleTrekkingRewardHandler(Context context) {
        this.context = context;
    }

    /** Counts every colour of both Temple Trekking and Burgh de Rott reward tokens. */
    int tokenCount() {
        int total = 0;
        for (int id : TOKEN_IDS) {
            total += Rs2Inventory.count(id);
        }
        return total;
    }

    boolean isDone() {
        return done;
    }

    void enter() {
        phase = Phase.OPEN;
        done = false;
        redeeming = false;
        tokensAtBuy = -1;
        buyPhaseStartedAt = 0;
        lastBuyAt = 0;
        selectFailures = 0;
        log.info("[TempleTrekking] Reward redemption started ({} tokens held)", tokenCount());
    }

    void reset() {
        done = true;
        redeeming = false;
        phase = Phase.OPEN;
        tokensAtBuy = -1;
    }

    String describe() {
        TempleTrekkingConfig config = context.config();
        int target = config == null ? 0 : config.rewardThreshold();
        TempleTrekkingReward reward = config == null ? TempleTrekkingReward.XP_TOME : config.rewardType();
        return "Redeeming rewards (" + tokenCount() + "/" + target + " tokens, " + reward.label() + ")";
    }

    void step() {
        TempleTrekkingConfig config = context.config();
        if (config == null || !config.redeemRewards()) {
            done = true;
            return;
        }
        if (!redeeming && tokenCount() < config.rewardThreshold()) {
            closeInterfaceIfOpen();
            done = true;
            return;
        }
        redeeming = true;
        if (tokenCount() <= 0) {
            closeInterfaceIfOpen();
            // Clear the "You gain ..." dialogue before interacting with another tome.
            if (clearDialogue()) {
                return;
            }
            // XP tomes are not stackable; read them so a long XP-reward session cannot fill the
            // inventory.
            if (config.rewardType() == TempleTrekkingReward.XP_TOME && readXpTomes()) {
                return;
            }
            done = true;
            log.info("[TempleTrekking] All reward tokens redeemed");
            return;
        }

        if (clearDialogue()) {
            return;
        }
        if (Rs2Inventory.isItemSelected()) {
            Rs2Inventory.deselect();
        }
        if (!context.canAct()) {
            return;
        }

        switch (phase) {
            case OPEN:
                if (isInterfaceOpen()) {
                    phase = Phase.SELECT;
                    return;
                }
                if (openInterface()) {
                    context.markAction();
                    log.info("[TempleTrekking] Opened Temple Trekking Rewards interface");
                }
                return;

            case SELECT:
                if (!isInterfaceOpen()) {
                    phase = Phase.OPEN;
                    return;
                }
                Widget option = findRewardWidget(config.rewardType());
                if (option == null) {
                    if (++selectFailures >= MAX_SELECT_FAILURES) {
                        context.warn("Could not find '" + config.rewardType().label()
                                + "' in the reward interface; check the configured reward type");
                        closeInterfaceIfOpen();
                        done = true;
                    } else {
                        context.markAction();
                    }
                    return;
                }
                if (selectReward(option, config.rewardType())) {
                    phase = Phase.BUY;
                    tokensAtBuy = tokenCount();
                    buyPhaseStartedAt = System.currentTimeMillis();
                    lastBuyAt = 0;
                    selectFailures = 0;
                    context.markAction();
                }
                return;

            case BUY:
                if (!isInterfaceOpen()) {
                    phase = Phase.OPEN;
                    return;
                }
                if (tokensAtBuy >= 0 && tokenCount() < tokensAtBuy) {
                    log.info("[TempleTrekking] Redeemed one token ({} left)", tokenCount());
                    phase = Phase.OPEN;
                    tokensAtBuy = -1;
                    context.markAction();
                    return;
                }
                long now = System.currentTimeMillis();
                if (buyPhaseStartedAt > 0 && now - buyPhaseStartedAt > BUY_TIMEOUT_MS) {
                    context.warn("Reward redemption did not consume a token; closing the interface");
                    closeInterfaceIfOpen();
                    done = true;
                    return;
                }
                if (now - lastBuyAt >= BUY_RETRY_MS && clickBuy()) {
                    lastBuyAt = now;
                    context.markAction();
                }
                return;

            default:
                done = true;
        }
    }

    private boolean clearDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return false;
        }
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Yes", "Okay", "Continue", "Claim");
        } else if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
        } else {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        context.markAction();
        return true;
    }

    /** Reads one accumulated XP tome. Returns true when an action was taken this tick. */
    private boolean readXpTomes() {
        for (int tomeId : TempleTrekkingReward.XP_TOME.iconItemIds()) {
            if (Rs2Inventory.count(tomeId) <= 0) {
                continue;
            }
            if (Rs2Inventory.interact(tomeId, "Read")) {
                context.markAction();
                log.info("[TempleTrekking] Read XP tome (item {})", tomeId);
                return true;
            }
        }
        return false;
    }

    private boolean openInterface() {
        for (int id : TOKEN_IDS) {
            if (Rs2Inventory.count(id) <= 0) {
                continue;
            }
            for (String action : OPEN_ACTIONS) {
                if (Rs2Inventory.interact(id, action)) {
                    return true;
                }
            }
            // Left-click usually maps to the claim option.
            if (Rs2Inventory.interact(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterfaceOpen() {
        for (int id : INTERFACE_WIDGETS) {
            if (Rs2Widget.isWidgetVisible(id)) {
                return true;
            }
        }
        return false;
    }

    private void closeInterfaceIfOpen() {
        if (isInterfaceOpen()) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
    }

    /**
     * Invokes the exact {@code Claim} widget action recorded on the BUYBUTTON. A plain left-click
     * on the button is kept only as a fallback.
     */
    private boolean clickBuy() {
        Widget button = Rs2Widget.getWidget(InterfaceID.TrekRewards.BUYBUTTON);
        if (invokeWidgetAction("Claim", InterfaceID.TrekRewards.BUYBUTTON, -1, button)) {
            return true;
        }
        return Rs2Widget.clickWidget(InterfaceID.TrekRewards.BUYBUTTONTEXT);
    }

    /**
     * The recording showed that selecting a reward is a {@code CC_OP "Details"} action on the
     * ICONS child, not a generic click on the icon's bounds. Invoking the menu entry directly is
     * the only reliable way to select the option.
     */
    private boolean selectReward(Widget icon, TempleTrekkingReward reward) {
        log.info("[TempleTrekking] Selecting '{}' via Details (icon index {}, item {})",
                reward.label(), reward.iconIndex(), icon == null ? -1 : icon.getItemId());
        return invokeWidgetAction("Details", InterfaceID.TrekRewards.ICONS, reward.iconIndex(), icon);
    }

    /**
     * Sends a widget action using the same parameters the client generates for a right-click menu
     * selection: CC_OP, identifier 1, param0 = child index (or -1 for the button itself) and
     * param1 = the target widget id.
     */
    private boolean invokeWidgetAction(String option, int widgetId, int childIndex, Widget widget) {
        Rectangle bounds = widget == null ? null : widget.getBounds();
        if (bounds == null || !Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
            bounds = Rs2UiHelper.getDefaultRectangle();
        }
        NewMenuEntry entry = new NewMenuEntry()
                .option(option)
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(childIndex)
                .param1(widgetId)
                .forceLeftClick(false);
        Microbot.doInvoke(entry, bounds);
        return true;
    }

    /**
     * Returns the ICONS child at the recorded index for this reward. The index is authoritative;
     * item id and label matching are only used as diagnostics/fallbacks if the index is missing.
     */
    private Widget findRewardWidget(TempleTrekkingReward reward) {
        Widget icons = Rs2Widget.getWidget(InterfaceID.TrekRewards.ICONS);
        Widget[] iconChildren = icons == null ? null : icons.getChildren();
        if (iconChildren == null) {
            log.debug("[TempleTrekking] Reward ICONS container not available");
            return null;
        }

        int index = reward.iconIndex();
        if (index >= 0 && index < iconChildren.length) {
            Widget icon = iconChildren[index];
            if (isClickable(icon)) {
                if (!reward.matchesIconItem(icon.getItemId())) {
                    log.debug("[TempleTrekking] Reward icon {} has item {} (expected one of {})",
                            index, icon.getItemId(), java.util.Arrays.toString(reward.iconItemIds()));
                }
                return icon;
            }
        }

        // Fallback if the interface layout ever changes: match by item id, then by label text.
        for (Widget icon : iconChildren) {
            if (isClickable(icon) && reward.matchesIconItem(icon.getItemId())) {
                return icon;
            }
        }
        Widget labels = Rs2Widget.getWidget(InterfaceID.TrekRewards.TEXT);
        Widget[] labelChildren = labels == null ? null : labels.getChildren();
        if (labelChildren != null) {
            for (int i = 0; i < labelChildren.length && i < iconChildren.length; i++) {
                if (labelChildren[i] == null || isHidden(labelChildren[i])) {
                    continue;
                }
                if (!reward.matches(widgetText(labelChildren[i]))) {
                    continue;
                }
                Widget icon = iconChildren[i];
                if (isClickable(icon)) {
                    return icon;
                }
            }
        }
        log.debug("[TempleTrekking] Could not locate reward icon {}; children={}", index, iconChildren.length);
        return null;
    }

    private boolean isClickable(Widget widget) {
        if (widget == null || isHidden(widget)) {
            return false;
        }
        Rectangle bounds = widget.getBounds();
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    /**
     * Widget.isHidden() asserts that it is called on the client thread. Scripts run on their own
     * thread, so delegate to Rs2Widget.isHidden(int), which schedules the check correctly.
     */
    private boolean isHidden(Widget widget) {
        return widget == null || Rs2Widget.isHidden(widget.getId());
    }

    private String widgetText(Widget widget) {
        StringBuilder text = new StringBuilder();
        append(text, widget.getName());
        append(text, widget.getText());
        String[] actions = widget.getActions();
        if (actions != null) {
            for (String action : actions) {
                append(text, action);
            }
        }
        return text.toString();
    }

    private void append(StringBuilder target, String value) {
        if (value != null && !value.isEmpty()) {
            if (target.length() > 0) {
                target.append(' ');
            }
            target.append(value);
        }
    }
}
