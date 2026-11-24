package net.runelite.client.plugins.microbot.autobankstander.processors;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Inspects and drives OSRS "make" interfaces.
 *
 * <p>Fletching and several other production skills use the SKILLMULTI widget
 * group 270 instead of the older 219 option dialogue. The Rs2Dialogue helpers
 * only inspect group 219/270 names in a few fixed places, which misses common
 * cases such as the single-product headless-arrow "Create" interface and
 * "Headless arrow" versus a configured "Headless arrows". This helper logs the
 * actual visible widget text/actions and clicks the exact product or quantity
 * option that is offered.</p>
 */
@Slf4j
public final class MakeDialogueHelper {

    private static final int[][] LOG_CONTAINERS = {
            {270, 0}, {270, 1}, {270, 5}, {270, 13}, {270, 14}, {270, 16},
            {219, 1}, {229, 1}, {231, 6}
    };

    private static final String[] QUANTITY_LABELS = {
            "Make All", "All", "Create", "Make 10", "Make 5", "Make 1",
            "10 sets", "5 sets", "1 set", "10", "5", "1"
    };

    private MakeDialogueHelper() {
    }

    /** True when a make/product/quantity chat-box interface is visible. */
    public static boolean isOpen() {
        try {
            return Rs2Dialogue.hasCombinationDialogue()
                    || Rs2Dialogue.hasSelectAnOption()
                    || Rs2Widget.isProductionWidgetOpen()
                    || Rs2Widget.isWidgetVisible(270, 1)
                    || Rs2Widget.isWidgetVisible(270, 5)
                    || Rs2Widget.isWidgetVisible(270, 13)
                    || Rs2Widget.isWidgetVisible(270, 14)
                    || Rs2Widget.isWidgetVisible(270, 16)
                    || Rs2Widget.isWidgetVisible(219, 1)
                    || Rs2Widget.isWidgetVisible(229, 1)
                    || Rs2Widget.isWidgetVisible(231, 6);
        } catch (Exception e) {
            log.debug("Could not determine make dialogue state: {}", e.getMessage());
            return false;
        }
    }

    /** Logs all relevant dialogue widgets. Safe to call repeatedly on failure. */
    public static void logState(String context) {
        log.info("{} | {}", context, describe());
    }

    public static String describe() {
        StringBuilder out = new StringBuilder();
        try {
            out.append("inDialogue=").append(Rs2Dialogue.isInDialogue())
                    .append(", select=").append(Rs2Dialogue.hasSelectAnOption())
                    .append(", combination=").append(Rs2Dialogue.hasCombinationDialogue())
                    .append(", production=").append(Rs2Widget.isProductionWidgetOpen())
                    .append(", question='").append(clean(Rs2Dialogue.getQuestion())).append('\'')
                    .append(", dialogueText='").append(clean(Rs2Dialogue.getDialogueText())).append('\'');
        } catch (Exception e) {
            out.append("dialogueStateError=").append(e.getMessage());
        }

        for (int[] ids : LOG_CONTAINERS) {
            appendContainer(out, ids[0] + ":" + ids[1], ids[0], ids[1]);
        }
        return out.toString();
    }

    /** True when one of the supplied product names is actually offered. */
    public static boolean hasProductOption(String... productNames) {
        return findProductWidget(productNames) != null;
    }

    /** Clicks the exact configured product option, never a generic fallback. */
    public static boolean clickProduct(String... productNames) {
        log.info("Looking for exact product option {} | {}", Arrays.toString(productNames), describe());
        Match match = findProductWidget(productNames);
        if (match == null) {
            logState("No exact product option was offered");
            return false;
        }
        log.info("Clicking product option '{}' on widget {}", match.label, describeWidget(match.widget));
        return click(match.widget);
    }

    /** True when a quantity/create confirmation is visible. */
    public static boolean hasQuantityOption() {
        return findQuantityWidget() != null;
    }

    /** Clicks Make All / All / Create or the closest available quantity option. */
    public static boolean clickQuantity() {
        Match match = findQuantityWidget();
        if (match == null) {
            return false;
        }
        log.info("Clicking quantity option '{}' on widget {}", match.label, describeWidget(match.widget));
        return click(match.widget);
    }

    /** Confirms the current make interface without product-count verification. */
    public static boolean clickQuantityOrSpace(String context) {
        return clickQuantityOrSpace(context, -1, -1);
    }

    /**
     * Confirms the current make interface. Single-product fletching interfaces
     * (headless arrows, finished arrows, darts, bolts) do not always expose a
     * named option, so this also tries the known SKILLMULTI confirm widget
     * (270:14), the '1' hotkey used by the working arrow scripts, and Space.
     */
    public static boolean clickQuantityOrSpace(String context, int productId, int productCountBefore) {
        logState(context + " | Confirming make/create interface");

        if (clickQuantity()) {
            log.info("{} | Clicked a named quantity/create option", context);
            if (confirmed(context, productId, productCountBefore)) {
                return true;
            }
        }

        // 270:14 is the Make/Create confirm used by cannonballs, wines, staves
        // and single-product fletching. Other scripts wait for this widget and
        // press '1'; clicking it directly is the equivalent mouse action.
        if (isWidgetVisible(270, 14)) {
            log.info("{} | Clicking SKILLMULTI create widget 270:14 ({})",
                    context, describeWidget(Rs2Widget.getWidget(270, 14)));
            Rs2Widget.clickWidget(270, 14);
            if (confirmed(context, productId, productCountBefore)) {
                return true;
            }
        }

        try {
            if (Rs2Dialogue.clickOption("Make All")) {
                log.info("{} | Clicked Make All via Rs2Dialogue", context);
                if (confirmed(context, productId, productCountBefore)) {
                    return true;
                }
            }
            if (Rs2Dialogue.clickOption("All")) {
                log.info("{} | Clicked All via Rs2Dialogue", context);
                if (confirmed(context, productId, productCountBefore)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("{} | Rs2Dialogue quantity click failed: {}", context, e.getMessage());
        }

        if (isWidgetVisible(270, 13) || isWidgetVisible(270, 14)) {
            log.info("{} | Pressing '1' to confirm the SKILLMULTI make interface", context);
            Rs2Keyboard.keyPress('1');
            if (confirmed(context, productId, productCountBefore)) {
                return true;
            }
        }

        if (Rs2Widget.isProductionWidgetOpen()) {
            log.info("{} | Pressing Space to confirm Create in the production interface", context);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            if (confirmed(context, productId, productCountBefore)) {
                return true;
            }
        }

        logState(context + " | No quantity/create confirmation succeeded");
        return false;
    }

    private static boolean confirmed(String context, int productId, int productCountBefore) {
        boolean ok = sleepUntil(() -> !Rs2Widget.isProductionWidgetOpen()
                || (productId > 0 && Rs2Inventory.itemQuantity(productId) > productCountBefore), 900);
        if (ok) {
            log.info("{} | Make/create confirmed (productionOpen={}, productId={}, before={}, now={})",
                    context,
                    Rs2Widget.isProductionWidgetOpen(),
                    productId,
                    productCountBefore,
                    productId > 0 ? Rs2Inventory.itemQuantity(productId) : -1);
        }
        return ok;
    }

    private static boolean isWidgetVisible(int group, int child) {
        try {
            return Rs2Widget.isWidgetVisible(group, child);
        } catch (Exception e) {
            return false;
        }
    }

    private static Match findProductWidget(String... productNames) {
        if (productNames == null || productNames.length == 0) {
            return null;
        }
        for (Widget widget : collectWidgets()) {
            if (!isVisible(widget)) {
                continue;
            }
            List<String> labels = labels(widget);
            for (String product : productNames) {
                String wanted = normalizeProduct(product);
                if (wanted.isEmpty()) {
                    continue;
                }
                for (String label : labels) {
                    String normalized = normalizeProduct(label);
                    if (normalized.equals(wanted)
                            || (wanted.length() >= 4 && normalized.contains(wanted))) {
                        return new Match(widget, label);
                    }
                }
            }
        }
        return null;
    }

    private static Match findQuantityWidget() {
        List<Widget> widgets = collectWidgets();
        // Exact labels first so "All" cannot accidentally match "Small".
        for (String wanted : QUANTITY_LABELS) {
            for (Widget widget : widgets) {
                if (!isVisible(widget)) {
                    continue;
                }
                for (String label : labels(widget)) {
                    if (matchesQuantityLabel(label, wanted)) {
                        return new Match(widget, label);
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesQuantityLabel(String label, String wanted) {
        String cleanLabel = clean(label).toLowerCase();
        String cleanWanted = clean(wanted).toLowerCase();
        if (cleanLabel.equals(cleanWanted)) {
            return true;
        }
        // Multi-word labels such as "Make All" may contain colour markup or
        // additional text; do not apply this loose check to short labels.
        return cleanWanted.contains(" ") && cleanLabel.contains(cleanWanted);
    }

    private static List<Widget> collectWidgets() {
        List<Widget> widgets = new ArrayList<>();
        for (int[] ids : LOG_CONTAINERS) {
            addContainer(widgets, ids[0], ids[1]);
        }
        try {
            for (Widget widget : Rs2Dialogue.getDialogueOptions()) {
                addWidgetAndDescendants(widgets, widget, 0);
            }
            for (Widget widget : Rs2Dialogue.getCombinationOptions()) {
                addWidgetAndDescendants(widgets, widget, 0);
            }
        } catch (Exception e) {
            log.debug("Could not collect Rs2Dialogue option widgets: {}", e.getMessage());
        }
        return widgets;
    }

    private static void addContainer(List<Widget> widgets, int group, int child) {
        try {
            if (!Rs2Widget.isWidgetVisible(group, child)) {
                return;
            }
            Widget container = Rs2Widget.getWidget(group, child);
            addWidgetAndDescendants(widgets, container, 0);
        } catch (Exception e) {
            log.debug("Could not read widget {}:{} - {}", group, child, e.getMessage());
        }
    }

    private static void addWidgetAndDescendants(Collection<Widget> widgets, Widget widget, int depth) {
        if (widget == null || depth > 3) {
            return;
        }
        addWidget(widgets, widget);
        Set<Widget> children = new LinkedHashSet<>();
        children.addAll(nullToEmpty(widget.getChildren()));
        children.addAll(nullToEmpty(widget.getNestedChildren()));
        children.addAll(nullToEmpty(widget.getDynamicChildren()));
        children.addAll(nullToEmpty(widget.getStaticChildren()));
        for (Widget child : children) {
            addWidgetAndDescendants(widgets, child, depth + 1);
        }
    }

    private static List<Widget> nullToEmpty(Widget[] values) {
        return values == null ? java.util.Collections.emptyList() : Arrays.asList(values);
    }

    private static void addWidget(Collection<Widget> widgets, Widget widget) {
        if (widget != null && !widgets.contains(widget)) {
            widgets.add(widget);
        }
    }

    private static List<String> labels(Widget widget) {
        List<String> labels = new ArrayList<>();
        addLabel(labels, widget.getName());
        addLabel(labels, widget.getText());
        try {
            String[] actions = widget.getActions();
            if (actions != null) {
                for (String action : actions) {
                    addLabel(labels, action);
                }
            }
        } catch (Exception e) {
            log.debug("Could not read actions for widget {}: {}", widget.getId(), e.getMessage());
        }
        return labels;
    }

    private static void addLabel(List<String> labels, String value) {
        String clean = clean(value);
        if (!clean.isEmpty() && !labels.contains(clean)) {
            labels.add(clean);
        }
    }

    private static void appendContainer(StringBuilder out, String label, int group, int child) {
        try {
            Widget widget = Rs2Widget.getWidget(group, child);
            if (widget == null) {
                out.append(", ").append(label).append("=null");
                return;
            }
            out.append(", ").append(label).append('=').append(describeWidget(widget));
            List<String> childLabels = new ArrayList<>();
            for (Widget descendant : collectDescendants(widget)) {
                for (String value : labels(descendant)) {
                    if (!childLabels.contains(value)) {
                        childLabels.add(value);
                    }
                }
            }
            if (!childLabels.isEmpty()) {
                int limit = Math.min(childLabels.size(), 12);
                out.append(" children=").append(childLabels.subList(0, limit));
                if (childLabels.size() > limit) {
                    out.append("(+").append(childLabels.size() - limit).append(" more)");
                }
            }
        } catch (Exception e) {
            out.append(", ").append(label).append("=error(").append(e.getMessage()).append(')');
        }
    }

    private static Set<Widget> collectDescendants(Widget widget) {
        Set<Widget> result = new LinkedHashSet<>();
        addWidgetAndDescendants(result, widget, 0);
        result.remove(widget);
        return result;
    }

    private static String describeWidget(Widget widget) {
        if (widget == null) {
            return "null";
        }
        try {
            String[] actions = widget.getActions();
            return "{id=" + widget.getId()
                    + ",hidden=" + widget.isHidden()
                    + ",name='" + clean(widget.getName()) + '\''
                    + ",text='" + clean(widget.getText()) + '\''
                    + ",actions=" + Arrays.toString(cleanLabels(actions))
                    + '}';
        } catch (Exception e) {
            return "{id=" + widget.getId() + ",error=" + e.getMessage() + '}';
        }
    }

    private static String[] cleanLabels(String[] values) {
        if (values == null) {
            return new String[0];
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = clean(values[i]);
        }
        return cleaned;
    }

    private static boolean isVisible(Widget widget) {
        try {
            return widget != null && !widget.isHidden();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean click(Widget widget) {
        if (widget == null) {
            return false;
        }
        try {
            if (Rs2Widget.clickWidget(widget)) {
                return true;
            }
            Widget parent = widget.getParent();
            return parent != null && Rs2Widget.clickWidget(parent);
        } catch (Exception e) {
            log.info("Could not click widget {}: {}", widget.getId(), e.getMessage());
            return false;
        }
    }

    private static String normalizeProduct(String value) {
        String normalized = clean(value).toLowerCase();
        if (normalized.length() > 3 && normalized.endsWith("s") && !normalized.endsWith("ss")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.trim();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final class Match {
        private final Widget widget;
        private final String label;

        private Match(Widget widget, String label) {
            this.widget = widget;
            this.label = label;
        }
    }
}
