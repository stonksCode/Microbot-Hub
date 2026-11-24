package net.runelite.client.plugins.microbot.tormenteddemons;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "<html>[<font color=#FF4500>FYRE</font>] " + "Tormented Demons",
        description = "Automates Tormented Demon kills with fire bomb dodging, punish and special attack phases, gear switching, restocking, and looting",
        tags = {"tormented", "flicker", "weapon", "switch", "microbot"},
        authors = { "fyrestarter11", "zerozero" },
        version = TormentedDemonPlugin.version,
        minClientVersion = "2.6.27",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TormentedDemonPlugin extends Plugin {

    public static final String version = "2.0.11";

    private static final int CHANGE_ATTACK_STYLE_ANIMATION = 11387;
    private static final int MELEE_ATTACK_ANIMATION = 11392;
    private static final int MAGIC_ATTACK_ANIMATION = 11388;
    private static final int RANGE_ATTACK_ANIMATION = 11389;

    private static final int TORMENTED_VENGEANCE_SPECIAL = 2856;
    private static final int FIREBOMB_BLAST_RADIUS = 1;
    private static final int FIREBOMB_SEARCH_RADIUS = 6;
    private static final int SCENE_SIZE = 104;
    private static final long DODGE_TICK_MS = 60L;
    private static final long DODGE_RECLICK_MS = 150L;
    // The tile a bomb lands on is dangerous from the moment it is launched, so it is blocked for
    // the whole flight. The fire itself then burns for this long after impact.
    private static final long FIREBOMB_BURN_MS = 1800L;
    private static final long FIREBOMB_MAX_FLIGHT_MS = 6000L;
    // Projectile cycles are 20 ms client cycles.
    private static final long PROJECTILE_CYCLE_MS = 20L;
    private static final long PRAYER_RETRY_MS = 400L;
    // Delay before a protection prayer is switched off after the last demon stops targeting the
    // player. Covers the gap between an attack being launched and interaction being updated.
    private static final long PROTECTION_RELEASE_GRACE_MS = 2_000L;
    private static final long STYLE_CHANGE_WINDOW_MS = 5000L;
    private static final long STYLE_CHANGE_TRANSITION_MS = 6000L;
    private static final int TRANSITION_STANDOFF_MIN = 4;
    private static final int TRANSITION_STANDOFF_PREFERRED = 5;

    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledExecutorService prayerExecutor;

    private final Map<Integer, DemonTracker> demonTrackers = new ConcurrentHashMap<>();
    private final Map<WorldPoint, FireBomb> fireBombs = new ConcurrentHashMap<>();
    private final Object dodgeLoopLock = new Object();
    private ScheduledFuture<?> dodgeLoopFuture;
    private volatile WorldPoint dodgeTarget;
    private volatile long lastDodgeClickAt;
    private volatile long dodgeArmedAt;
    private volatile Rs2PrayerEnum desiredProtectionPrayer;
    private volatile Rs2PrayerEnum lastConfirmedProtectionPrayer;
    private volatile long lastPrayerInvokeAt;
    private volatile String lastPrayerReason = "";
    private volatile boolean prayerInvokePending;
    private volatile boolean demonTargetingPlayer;
    private volatile long lastDemonTargetingPlayerAt;
    private volatile boolean styleChangeTransitionActive;
    private volatile long styleChangeTransitionAt;
    private volatile NPC styleChangeTransitionNpc;
    private volatile int lastTransitionHullDistance = Integer.MAX_VALUE;
    private volatile long lastTransitionDistanceCheckAt;
    private volatile boolean transitionKiteRequested;
    private volatile String lastCopyGearButtonId;
    private volatile String handledCopyGearButtonId;
    private volatile String lastResetStatsButtonId;
    private volatile String handledResetStatsButtonId;

    @Inject
    private ConfigManager configManager;

    @Inject
    private TormentedDemonConfig config;

    @Provides
    TormentedDemonConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TormentedDemonConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TormentedDemonOverlay tormentedDemonOverlay;

    @Inject
    private TormentedDemonScript tormentedDemonScript;

    @Override
    protected void startUp() throws AWTException {
        log.info("[TormentedDemon] Tormented Demons v{} starting", version);
        logToConsole("Tormented Demons v" + version + " loaded");
        clearDodgeState();
        clearPrayerState();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        prayerExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TormentedDemonPrayer");
            thread.setDaemon(true);
            return thread;
        });
        if (overlayManager != null) {
            overlayManager.add(tormentedDemonOverlay);
        }
        tormentedDemonScript.run(config);
    }

    @Override
    protected void shutDown() {
        clearDodgeState();
        clearPrayerState();
        tormentedDemonScript.shutdown();
        overlayManager.remove(tormentedDemonOverlay);
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
        if (prayerExecutor != null && !prayerExecutor.isShutdown()) {
            prayerExecutor.shutdown();
        }
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("tormenteddemon")) {
            return;
        }

        if (event.getKey().equals("dodgeFirebombs") && !config.dodgeFirebombs()) {
            clearDodgeState();
        }

        if (event.getKey().equals("autoPrayerSwitch") && !config.enableDefensivePrayer()) {
            clearPrayerState();
        }

        if (event.getKey().equals("copyGear")) {
            copyCurrentGear("config changed event");
        }

        if (event.getKey().equals("resetStats")) {
            resetStatistics("config changed event");
        }
    }

    /**
     * Writes to the client log and the Microbot console without letting a logging failure abort
     * the surrounding action.
     */
    private void logToConsole(String message) {
        log.info(message);
        try {
            Microbot.log(message);
        } catch (Exception ignored) {
        }
    }

    /**
     * Reads the currently worn equipment, builds a comma separated item name list and copies it to
     * the system clipboard. Triggered by the "Copy Current Gear" config button.
     */
    private void copyCurrentGear(String trigger) {
        String buttonId = null;
        try {
            ConfigButton button = config.copyGear();
            buttonId = button == null ? null : button.getId();
        } catch (Exception ignored) {
            // config value unavailable yet
        }

        if (buttonId != null) {
            lastCopyGearButtonId = buttonId;
            if (buttonId.equals(handledCopyGearButtonId)) {
                return;
            }
            handledCopyGearButtonId = buttonId;
        }

        logToConsole("[TormentedDemon] Copy Current Gear triggered (" + trigger + ")");

        Runnable copyTask = () -> {
            String gear = readWornGearString();
            logToConsole("[TormentedDemon] Current gear: " + (gear.isEmpty() ? "<none>" : gear));

            if (gear.isEmpty()) {
                logToConsole("[TormentedDemon] No equipped items found. Log in and wear gear, then try again.");
                return;
            }

            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(gear), null);
                logToConsole("[TormentedDemon] Gear copied to clipboard: " + gear);
            } catch (Exception e) {
                log.warn("[TormentedDemon] Failed to copy gear to the clipboard", e);
                logToConsole("[TormentedDemon] Failed to copy gear to the clipboard: " + e.getMessage());
            }
        };

        try {
            if (Microbot.getClient() != null && Microbot.getClientThread() != null) {
                Microbot.getClientThread().invoke(copyTask);
            } else {
                copyTask.run();
            }
        } catch (Exception e) {
            log.warn("[TormentedDemon] Failed to schedule the gear copy", e);
            logToConsole("[TormentedDemon] Failed to read current gear: " + e.getMessage());
        }
    }

    /**
     * Builds the current equipment string from the worn equipment container, falling back to the
     * cached Rs2Equipment list when the container is not available.
     */
    private String readWornGearString() {
        StringJoiner gear = new StringJoiner(",");

        try {
            ItemContainer worn = Microbot.getClient().getItemContainer(InventoryID.WORN);
            if (worn != null && worn.getItems() != null) {
                for (Item item : worn.getItems()) {
                    if (item == null || item.getId() <= 0) {
                        continue;
                    }
                    ItemComposition composition = Microbot.getClient().getItemDefinition(item.getId());
                    if (composition == null) {
                        continue;
                    }
                    String name = composition.getName();
                    if (name != null && !name.trim().isEmpty()) {
                        gear.add(name.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[TormentedDemon] Could not read the worn equipment container", e);
        }

        if (gear.length() == 0) {
            try {
                Rs2Equipment.items().forEach(item -> {
                    if (item == null) {
                        return;
                    }
                    String name = item.getName();
                    if (name != null && !name.trim().isEmpty()) {
                        gear.add(name.trim());
                    }
                });
            } catch (Exception e) {
                log.debug("[TormentedDemon] Could not read the cached equipment list", e);
            }
        }

        return gear.toString();
    }

    /**
     * Safety net for the config button: if the click event is missed for any reason the stored
     * button id changes and we still detect it here.
     */
    private void pollCopyGearButton() {
        try {
            ConfigButton button = config.copyGear();
            String buttonId = button == null ? null : button.getId();
            if (buttonId == null) {
                return;
            }
            if (lastCopyGearButtonId == null) {
                lastCopyGearButtonId = buttonId;
                return;
            }
            if (!buttonId.equals(lastCopyGearButtonId)) {
                copyCurrentGear("button poll");
            }
        } catch (Exception e) {
            log.debug("[TormentedDemon] Could not poll the copy gear button", e);
        }
    }

    /**
     * Resets the overlay counters. Triggered by the "Reset Statistics" config button, either from
     * the config change event or from {@link #pollResetStatsButton()}.
     */
    private void resetStatistics(String trigger) {
        String buttonId = null;
        try {
            ConfigButton button = config.resetStats();
            buttonId = button == null ? null : button.getId();
        } catch (Exception ignored) {
            // config value unavailable yet
        }

        if (buttonId != null) {
            lastResetStatsButtonId = buttonId;
            if (buttonId.equals(handledResetStatsButtonId)) {
                return;
            }
            handledResetStatsButtonId = buttonId;
        }

        TormentedDemonScript.resetStatistics();
        logToConsole("[TormentedDemon] Overlay statistics reset (" + trigger + ")");
    }

    private void pollResetStatsButton() {
        try {
            ConfigButton button = config.resetStats();
            String buttonId = button == null ? null : button.getId();
            if (buttonId == null) {
                return;
            }
            if (lastResetStatsButtonId == null) {
                lastResetStatsButtonId = buttonId;
                return;
            }
            if (!buttonId.equals(lastResetStatsButtonId)) {
                resetStatistics("button poll");
            }
        } catch (Exception e) {
            log.debug("[TormentedDemon] Could not poll the reset statistics button", e);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        trackFireBomb(event.getProjectile(), event.getPosition());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        pollCopyGearButton();
        pollResetStatsButton();
        pruneExpiredFireBombs();
        reportFireBombImpacts();
        if (config.enableDefensivePrayer() || config.debugPrayer()) {
            updateDemonTracking();
        }
        if (config.enableDefensivePrayer()) {
            // Protection is only kept on while something is actually attacking the player.
            // Without this, the last desired prayer was re-enabled every tick during looting and
            // travel, draining points with nothing to protect against.
            long sinceTargeting = System.currentTimeMillis() - lastDemonTargetingPlayerAt;
            if (demonTargetingPlayer || sinceTargeting <= PROTECTION_RELEASE_GRACE_MS) {
                verifyProtectionPrayer();
            } else {
                releaseProtectionPrayer();
            }
        }
        // Firebombs are tracked even when dodging is disabled so the punish window still opens.
        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            trackFireBomb(projectile, null);
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject graphicsObject = event.getGraphicsObject();
        if (graphicsObject == null || graphicsObject.getId() != TORMENTED_VENGEANCE_SPECIAL) {
            return;
        }

        WorldPoint graphicsWorldPoint = toWorldPoint(graphicsObject.getLocation());
        if (graphicsWorldPoint == null) {
            return;
        }

        FireBomb tracked = fireBombs.get(graphicsWorldPoint);
        if (tracked != null) {
            // The graphic marks the actual impact. Correct any flight-time estimate and make sure
            // the blast stays blocked for the full burn time even if the projectile forecast was
            // optimistic.
            debugDodge("Special gfx at " + graphicsWorldPoint + " confirms tracked bomb; impact="
                    + (tracked.impactReported ? "reported" : "now"));
            markFireBombImpact(tracked);
            return;
        }

        debugDodge("Special gfx at " + graphicsWorldPoint + " was not predicted by a projectile");
        addFireBomb(graphicsWorldPoint, System.currentTimeMillis(),
                "graphics " + TORMENTED_VENGEANCE_SPECIAL);
    }

    private void trackFireBomb(Projectile projectile, LocalPoint targetLocal) {
        if (projectile == null || projectile.getTargetActor() != null) {
            return;
        }

        Actor source = projectile.getSourceActor();
        if (source instanceof Player) {
            return;
        }
        if (source instanceof NPC && !isTormentedDemon((NPC) source)) {
            return;
        }

        // getTargetPoint() is the exact tile the projectile lands on. It is known at launch, so
        // the landing tile can be blocked for the entire flight instead of waiting until the
        // projectile is next to the player (which left no time to step off the blast).
        WorldPoint target = projectile.getTargetPoint();
        if (target == null) {
            target = toWorldPoint(targetLocal);
        }
        if (target == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long impactAt = now + projectileImpactDelayMs(projectile);
        addFireBomb(target, impactAt,
                "projectile " + projectile.getId() + " source=" + actorName(source));
    }

    private long projectileImpactDelayMs(Projectile projectile) {
        int remainingCycles = projectile.getRemainingCycles();
        if (remainingCycles <= 0) {
            return 0L;
        }
        return Math.min(FIREBOMB_MAX_FLIGHT_MS, remainingCycles * PROJECTILE_CYCLE_MS);
    }

    private void addFireBomb(WorldPoint target, long impactAt, String source) {
        long now = System.currentTimeMillis();
        if (target == null || impactAt + FIREBOMB_BURN_MS <= now) {
            return;
        }

        FireBomb existing = fireBombs.get(target);
        if (existing != null && existing.expireAt > now) {
            // Already tracked (the projectile is also reported once per game tick while it is in
            // flight). Keep the original impact estimate instead of drifting it every tick.
            return;
        }

        boolean firstOfVolley = fireBombs.isEmpty();
        FireBomb bomb = new FireBomb(target, impactAt, impactAt + FIREBOMB_BURN_MS);
        fireBombs.put(target, bomb);
        if (firstOfVolley) {
            // Timestamp the first bomb of this volley so the debug log can show exactly how long
            // it takes from detection to the first movement click.
            dodgeArmedAt = now;
        }
        if (config.dodgeFirebombs()) {
            tormentedDemonScript.dodgeUntil = Math.max(tormentedDemonScript.dodgeUntil,
                    bomb.expireAt + 200L);
            extendBombWalkLock(bomb.expireAt);
            ensureDodgeLoop();
        }
        debugDodge("Tracking firebomb at " + target + " via " + source + " impact in "
                + Math.max(0L, impactAt - now) + "ms; active=" + fireBombs.size());
    }

    /**
     * Reports a landed bomb to the phase script. The punish window and the defenseless timer are
     * measured from the impact, not from the launch, so both stay in sync with the actual fire.
     */
    private void markFireBombImpact(FireBomb bomb) {
        long now = System.currentTimeMillis();
        if (!bomb.impactReported) {
            bomb.impactAt = now;
            bomb.impactReported = true;
            if (tormentedDemonScript != null) {
                tormentedDemonScript.onFirebombTracked();
            }
        }
        bomb.expireAt = Math.max(bomb.expireAt, now + FIREBOMB_BURN_MS);
        // The burn lasts longer than the projectile forecast assumed, so keep combat from
        // auto-walking toward the demon until the fire is actually gone.
        extendBombWalkLock(bomb.expireAt);
    }

    /**
     * Prevents the combat script from starting a walk toward the demon while a blast is live.
     * Attacking from the current tile stays allowed; only the click-induced auto-walk, which
     * does not avoid bomb tiles, is held back.
     */
    private void extendBombWalkLock(long until) {
        if (tormentedDemonScript != null && config.dodgeFirebombs()) {
            tormentedDemonScript.bombWalkLockUntil = Math.max(
                    tormentedDemonScript.bombWalkLockUntil, until);
        }
    }

    private void reportFireBombImpacts() {
        long now = System.currentTimeMillis();
        for (FireBomb bomb : fireBombs.values()) {
            if (!bomb.impactReported && now >= bomb.impactAt) {
                markFireBombImpact(bomb);
            }
        }
    }

    private void pruneExpiredFireBombs() {
        long now = System.currentTimeMillis();
        fireBombs.entrySet().removeIf(entry -> entry.getValue().expireAt <= now);
    }

    private void ensureDodgeLoop() {
        if (!config.dodgeFirebombs() || scheduledExecutorService == null || scheduledExecutorService.isShutdown()) {
            return;
        }

        synchronized (dodgeLoopLock) {
            if (dodgeLoopFuture != null && !dodgeLoopFuture.isDone() && !dodgeLoopFuture.isCancelled()) {
                return;
            }
            try {
                // Start with no initial delay: the first click has to happen before the demon's
                // bind ends, so waiting a full tick here is already too late.
                dodgeLoopFuture = scheduledExecutorService.scheduleWithFixedDelay(this::dodgeTickSafely,
                        0L, DODGE_TICK_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                dodgeLoopFuture = null;
            }
        }
    }

    private void stopDodgeLoop() {
        synchronized (dodgeLoopLock) {
            if (dodgeLoopFuture != null) {
                dodgeLoopFuture.cancel(false);
                dodgeLoopFuture = null;
            }
        }
    }

    private void dodgeTickSafely() {
        try {
            dodgeTick();
        } catch (Exception e) {
            log.warn("Firebomb dodge tick failed", e);
        }
    }

    private void dodgeTick() {
        if (!config.dodgeFirebombs()) {
            clearDodgeState();
            return;
        }

        long now = System.currentTimeMillis();
        pruneExpiredFireBombs();
        reportFireBombImpacts();
        if (fireBombs.isEmpty()) {
            stopDodgeLoop();
            dodgeTarget = null;
            tormentedDemonScript.dodgeUntil = 0;
            tormentedDemonScript.bombWalkLockUntil = 0;
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        Set<WorldPoint> blastTiles = collectBlastTiles(now, false);
        if (!blastTiles.contains(playerLocation)) {
            // The player is outside every blast, including the landing tiles of bombs still in
            // the air. Stop steering immediately and give the combat script control back; the
            // dodge loop keeps ticking until the fires burn out so it can react to the next bomb.
            dodgeTarget = null;
            if (tormentedDemonScript != null) {
                tormentedDemonScript.dodgeUntil = 0;
            }
            return;
        }

        if (now - lastDodgeClickAt < DODGE_RECLICK_MS) {
            return;
        }

        Set<WorldPoint> npcTiles = getNpcOccupiedTiles(playerLocation, FIREBOMB_SEARCH_RADIUS + 2);

        // Keep hammering the step already chosen while the player is still inside the blast. The
        // demon binds the player in place for a moment and movement clicks made during the bind
        // are ignored, so a single click is not enough: one of the repeated clicks has to land
        // right as the bind ends. A started walk is never restarted, so the path cannot detour.
        WorldPoint target = dodgeTarget;
        if (target != null && !target.equals(playerLocation)
                && !blastTiles.contains(target) && !npcTiles.contains(target)) {
            if (Rs2Player.isMoving()) {
                return;
            }
            lastDodgeClickAt = now;
            boolean clicked = clickDodgeTile(target);
            debugDodge("Re-click " + target + " from " + playerLocation + " clicked=" + clicked
                    + " bombs=" + describeBombs(now) + " after " + (now - dodgeArmedAt) + "ms");
            return;
        }

        WorldPoint step = chooseDodgeStep(playerLocation, blastTiles, npcTiles);
        if (step == null) {
            lastDodgeClickAt = now;
            debugDodge("No bomb-free step from " + playerLocation + "; bombs="
                    + describeBombs(now) + " npcTiles=" + npcTiles.size());
            return;
        }

        dodgeTarget = step;
        boolean clicked = clickDodgeTile(step);
        lastDodgeClickAt = now;
        debugDodge("Dodge step " + step + " from " + playerLocation + " clicked=" + clicked
                + " bombs=" + describeBombs(now) + " npcTiles=" + npcTiles.size()
                + " after " + (now - dodgeArmedAt) + "ms");
    }

    /**
     * Every tile covered by a bomb blast. Bombs still in the air are included: their landing tile
     * is already dangerous because the player must not be standing there when they explode.
     *
     * @param onlyLanded when true, bombs that have not impacted yet are ignored. This is used as
     *                   the last-resort escape, where crossing fire that has already landed is
     *                   preferable to standing in it, but walking into a landing zone never is.
     */
    private Set<WorldPoint> collectBlastTiles(long now, boolean onlyLanded) {
        Set<WorldPoint> tiles = new HashSet<>();
        for (FireBomb bomb : fireBombs.values()) {
            if (onlyLanded && now < bomb.impactAt) {
                continue;
            }
            WorldPoint center = bomb.tile;
            for (int dx = -FIREBOMB_BLAST_RADIUS; dx <= FIREBOMB_BLAST_RADIUS; dx++) {
                for (int dy = -FIREBOMB_BLAST_RADIUS; dy <= FIREBOMB_BLAST_RADIUS; dy++) {
                    tiles.add(new WorldPoint(center.getX() + dx, center.getY() + dy,
                            center.getPlane()));
                }
            }
        }
        return tiles;
    }

    private String describeBombs(long now) {
        StringJoiner joiner = new StringJoiner(",");
        for (FireBomb bomb : fireBombs.values()) {
            joiner.add(bomb.tile + "@" + Math.max(0L, bomb.impactAt - now) + "ms");
        }
        return joiner.toString();
    }

    private boolean clickDodgeTile(WorldPoint safeTile) {
        try {
            WorldView worldView = Microbot.getClient().getTopLevelWorldView();
            LocalPoint localPoint = worldView == null ? null : LocalPoint.fromWorld(worldView, safeTile);
            if (localPoint == null || !Rs2Camera.isTileOnScreen(localPoint)) {
                return Rs2Walker.walkMiniMap(safeTile);
            }
            net.runelite.api.Point canvasPoint = Perspective.localToCanvas(Microbot.getClient(),
                    localPoint, worldView.getPlane());
            if (canvasPoint == null) {
                return Rs2Walker.walkMiniMap(safeTile);
            }
            NewMenuEntry entry = new NewMenuEntry()
                    .param0(canvasPoint.getX())
                    .param1(canvasPoint.getY())
                    .type(MenuAction.WALK)
                    .identifier(0)
                    .itemId(0)
                    .option("Walk here");
            Microbot.doInvoke(entry, new Rectangle(canvasPoint.getX(), canvasPoint.getY(),
                    Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
            return true;
        } catch (RuntimeException e) {
            debugDodge("Canvas dodge click failed: " + e.getMessage());
            return Rs2Walker.walkMiniMap(safeTile);
        }
    }

    private Set<WorldPoint> getNpcOccupiedTiles(WorldPoint center, int radius) {
        Set<WorldPoint> occupied = new HashSet<>();
        try {
            for (var npc : Microbot.getRs2NpcCache().query().within(center, radius).toList()) {
                if (npc == null || npc.isDead()) {
                    continue;
                }
                var area = npc.getWorldArea();
                if (area != null) {
                    occupied.addAll(area.toWorldPointList());
                }
            }
        } catch (RuntimeException e) {
            debugDodge("NPC occupancy check failed: " + e.getMessage());
        }
        return occupied;
    }

    /**
     * Picks the first step of a walk that never enters a bomb blast. The walk is a breadth-first
     * search over the live collision map with every blast tile and NPC tile removed, so the chosen
     * step is always safe and there is a bomb-free route from it to the final tile. Only the first
     * step is clicked, which guarantees the player never walks *through* a blast on the way to a
     * farther safe tile.
     */
    private WorldPoint chooseDodgeStep(WorldPoint playerLocation, Set<WorldPoint> blastTiles,
                                       Set<WorldPoint> npcTiles) {
        DodgePath path = bombAwarePaths(playerLocation, blastTiles, npcTiles, null);
        WorldPoint destination = selectDodgeDestination(path, blastTiles);
        if (destination == null) {
            // Overlapping bombs can cover every tile next to the player. Escaping then has to
            // cross fire that has already landed, but the destination is still outside every
            // blast and incoming landing zones are still avoided.
            Set<WorldPoint> landedBlasts = collectBlastTiles(System.currentTimeMillis(), true);
            path = bombAwarePaths(playerLocation, blastTiles, npcTiles, landedBlasts);
            destination = selectDodgeDestination(path, blastTiles);
            if (destination == null) {
                return null;
            }
            debugDodge("Only escape from " + playerLocation + " crosses a burning tile; target "
                    + destination);
        }

        WorldPoint step = firstStep(playerLocation, destination, path);
        if (step == null) {
            debugDodge("No path from " + playerLocation + " to safe tile " + destination);
        }
        return step;
    }

    /**
     * Nearest tile that is outside every blast. Distance to the demon is deliberately ignored:
     * surviving the bombs comes first, and the player can resume attacking from wherever the
     * nearest safe tile is.
     */
    private WorldPoint selectDodgeDestination(DodgePath path, Set<WorldPoint> blastTiles) {
        WorldPoint best = null;
        int bestSteps = Integer.MAX_VALUE;
        int bestClearance = -1;

        for (Map.Entry<WorldPoint, Integer> entry : path.distances.entrySet()) {
            WorldPoint candidate = entry.getKey();
            int steps = entry.getValue();
            if (steps < 1 || steps > FIREBOMB_SEARCH_RADIUS || blastTiles.contains(candidate)) {
                continue;
            }

            int clearance = clearance(candidate, blastTiles);
            if (steps < bestSteps
                    || (steps == bestSteps && clearance > bestClearance)
                    || (steps == bestSteps && clearance == bestClearance
                        && (best == null || candidate.getX() < best.getX()
                            || (candidate.getX() == best.getX() && candidate.getY() < best.getY())))) {
                best = candidate;
                bestSteps = steps;
                bestClearance = clearance;
            }
        }

        return best;
    }

    /** Chebyshev distance from a tile to the closest blast tile. */
    private int clearance(WorldPoint tile, Set<WorldPoint> blastTiles) {
        int best = Integer.MAX_VALUE;
        for (WorldPoint blast : blastTiles) {
            best = Math.min(best, blast.distanceTo(tile));
        }
        return best;
    }

    private WorldPoint firstStep(WorldPoint start, WorldPoint destination, DodgePath path) {
        WorldPoint current = destination;
        for (int guard = 0; guard < FIREBOMB_SEARCH_RADIUS + 2; guard++) {
            WorldPoint parent = path.parents.get(current);
            if (parent == null) {
                return null;
            }
            if (parent.equals(start)) {
                return current;
            }
            current = parent;
        }
        return null;
    }

    private DodgePath bombAwarePaths(WorldPoint start, Set<WorldPoint> blocked,
                                     Set<WorldPoint> npcTiles, Set<WorldPoint> passableBlasts) {
        Client client = Microbot.getClient();
        if (client == null) {
            return DodgePath.empty();
        }
        if (client.isClientThread()) {
            return bombAwarePathsInternal(start, blocked, npcTiles, passableBlasts);
        }
        DodgePath path = Microbot.getClientThread().invoke(
                () -> bombAwarePathsInternal(start, blocked, npcTiles, passableBlasts));
        return path == null ? DodgePath.empty() : path;
    }

    private DodgePath bombAwarePathsInternal(WorldPoint start, Set<WorldPoint> blocked,
                                             Set<WorldPoint> npcTiles,
                                             Set<WorldPoint> passableBlasts) {
        Map<WorldPoint, Integer> distances = new HashMap<>();
        Map<WorldPoint, WorldPoint> parents = new HashMap<>();

        WorldView worldView = Microbot.getClient().getTopLevelWorldView();
        if (worldView == null) {
            return DodgePath.empty();
        }
        CollisionData[] collisionMaps = worldView.getCollisionMaps();
        int plane = worldView.getPlane();
        if (collisionMaps == null || plane < 0 || plane >= collisionMaps.length
                || collisionMaps[plane] == null) {
            return DodgePath.empty();
        }
        int[][] flags = collisionMaps[plane].getFlags();

        ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
        distances.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            WorldPoint point = queue.poll();
            int distance = distances.get(point);
            if (distance >= FIREBOMB_SEARCH_RADIUS) {
                continue;
            }

            LocalPoint local = toScenePoint(worldView, point);
            if (local == null) {
                continue;
            }
            int sceneX = local.getSceneX();
            int sceneY = local.getSceneY();
            if (!withinScene(sceneX, sceneY)) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    WorldPoint neighbor = new WorldPoint(point.getX() + dx, point.getY() + dy,
                            point.getPlane());
                    if (distances.containsKey(neighbor)) {
                        continue;
                    }
                    if (npcTiles.contains(neighbor)) {
                        continue;
                    }
                    if (blocked.contains(neighbor)
                            && (passableBlasts == null || !passableBlasts.contains(neighbor))) {
                        continue;
                    }
                    if (!canStep(flags, sceneX, sceneY, dx, dy)) {
                        continue;
                    }
                    distances.put(neighbor, distance + 1);
                    parents.put(neighbor, point);
                    queue.add(neighbor);
                }
            }
        }

        return new DodgePath(distances, parents);
    }

    private boolean canStep(int[][] flags, int sceneX, int sceneY, int dx, int dy) {
        if (!withinScene(sceneX + dx, sceneY + dy)) {
            return false;
        }
        int destFlags = flags[sceneX + dx][sceneY + dy];
        if ((destFlags & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0) {
            return false;
        }

        int sourceFlags = flags[sceneX][sceneY];
        int stepX = Integer.compare(dx, 0);
        int stepY = Integer.compare(dy, 0);
        if (stepX != 0) {
            int flag = stepX > 0
                    ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
                    : CollisionDataFlag.BLOCK_MOVEMENT_WEST;
            if ((sourceFlags & flag) != 0) {
                return false;
            }
        }
        if (stepY != 0) {
            int flag = stepY > 0
                    ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH
                    : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
            if ((sourceFlags & flag) != 0) {
                return false;
            }
        }

        if (stepX != 0 && stepY != 0) {
            // A diagonal is only walkable when both orthogonal tiles are open.
            int sideXFlags = flags[sceneX + stepX][sceneY];
            int sideYFlags = flags[sceneX][sceneY + stepY];
            int sideXBlock = stepY > 0
                    ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH
                    : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
            int sideYBlock = stepX > 0
                    ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
                    : CollisionDataFlag.BLOCK_MOVEMENT_WEST;
            if ((sideXFlags & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | sideXBlock)) != 0) {
                return false;
            }
            if ((sideYFlags & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | sideYBlock)) != 0) {
                return false;
            }
        }

        return true;
    }

    private LocalPoint toScenePoint(WorldView worldView, WorldPoint point) {
        if (worldView == null || point == null || point.getPlane() != worldView.getPlane()) {
            return null;
        }
        if (worldView.getScene().isInstance()) {
            WorldPoint instancePoint = WorldPoint.toLocalInstance(worldView, point)
                    .stream().findFirst().orElse(null);
            return instancePoint == null ? null : LocalPoint.fromWorld(worldView, instancePoint);
        }
        return LocalPoint.fromWorld(worldView, point);
    }

    private boolean withinScene(int sceneX, int sceneY) {
        return sceneX >= 0 && sceneY >= 0 && sceneX < SCENE_SIZE && sceneY < SCENE_SIZE;
    }

    private WorldPoint toWorldPoint(LocalPoint localPoint) {
        if (localPoint == null) {
            return null;
        }
        WorldView worldView = Microbot.getClient().getTopLevelWorldView();
        if (worldView == null) {
            return null;
        }
        return worldView.getScene().isInstance()
                ? WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint)
                : WorldPoint.fromLocal(Microbot.getClient(), localPoint);
    }

    private boolean isTormentedDemon(NPC npc) {
        String name = npc.getName();
        return name != null && name.toLowerCase(Locale.ROOT).contains("tormented demon");
    }

    private String actorName(Actor actor) {
        if (actor == null) {
            return "unknown";
        }
        return actor.getName() == null ? actor.getClass().getSimpleName() : actor.getName();
    }

    private void debugDodge(String message) {
        if (config.debugDodge()) {
            Microbot.log("[TD dodge] " + message);
        }
    }

    private void clearDodgeState() {
        fireBombs.clear();
        dodgeTarget = null;
        lastDodgeClickAt = 0L;
        dodgeArmedAt = 0L;
        stopDodgeLoop();
        if (tormentedDemonScript != null) {
            tormentedDemonScript.dodgeUntil = 0L;
            tormentedDemonScript.bombWalkLockUntil = 0L;
        }
    }

    /** A firebomb landing zone and the timing of its explosion. */
    private static final class FireBomb {
        private final WorldPoint tile;
        private volatile long impactAt;
        private volatile long expireAt;
        private volatile boolean impactReported;

        private FireBomb(WorldPoint tile, long impactAt, long expireAt) {
            this.tile = tile;
            this.impactAt = impactAt;
            this.expireAt = expireAt;
        }
    }

    /** BFS distances and parent links for a walk around the active bombs. */
    private static final class DodgePath {
        private final Map<WorldPoint, Integer> distances;
        private final Map<WorldPoint, WorldPoint> parents;

        private DodgePath(Map<WorldPoint, Integer> distances,
                          Map<WorldPoint, WorldPoint> parents) {
            this.distances = distances;
            this.parents = parents;
        }

        private static DodgePath empty() {
            return new DodgePath(Collections.emptyMap(), Collections.emptyMap());
        }
    }


    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!config.enableDefensivePrayer() && !config.debugPrayer()) {
            return;
        }
        if (!(event.getActor() instanceof NPC)) {
            return;
        }

        NPC npc = (NPC) event.getActor();
        if (!isTormentedDemon(npc)) {
            return;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null || !isDemonFightingPlayer(npc, localPlayer)) {
            return;
        }

        DemonTracker tracker = getDemonTracker(npc);
        int animationId = npc.getAnimation();
        tracker.lastObservedAnimation = animationId;
        processDemonAnimation(npc, tracker, animationId, "event");
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event != null && event.getHitsplat() != null && tormentedDemonScript != null) {
            // Used to close the punish window with the first real hit; player animations also fire
            // for walking, which happens during the firebomb dodge.
            tormentedDemonScript.onTargetHitsplat(event.getActor(), event.getHitsplat());
        }

        if (!config.debugPrayer()) {
            return;
        }

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null || event.getActor() != localPlayer || event.getHitsplat() == null) {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();
        String targetInfo = "none";
        Actor target = localPlayer.getInteracting();
        if (target instanceof NPC && isTormentedDemon((NPC) target)) {
            NPC npc = (NPC) target;
            DemonTracker tracker = demonTrackers.get(npc.getIndex());
            targetInfo = "npc=" + npc.getIndex()
                    + " last=" + (tracker == null ? DemonAttackStyle.UNKNOWN : tracker.lastAttackStyle)
                    + " anim=" + npc.getAnimation();
        }

        debugPrayer("hitsplat amount=" + hitsplat.getAmount()
                + " type=" + hitsplat.getHitsplatType()
                + " protection=" + prayerName(Rs2Prayer.getActiveProtectionPrayer())
                + " desired=" + prayerName(desiredProtectionPrayer)
                + " target=" + targetInfo);
    }

    private void updateDemonTracking() {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            return;
        }
        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        long now = System.currentTimeMillis();
        demonTargetingPlayer = false;
        try {
            for (var npcModel : Microbot.getRs2NpcCache().query().within(playerLocation, 15).toList()) {
                NPC npc = npcModel == null ? null : npcModel.getNpc();
                if (npc == null || npc.isDead() || !isTormentedDemon(npc)
                        || !isDemonFightingPlayer(npc, localPlayer)) {
                    continue;
                }
                if (npc.getInteracting() == localPlayer) {
                    demonTargetingPlayer = true;
                    lastDemonTargetingPlayerAt = now;
                }

                DemonTracker tracker = getDemonTracker(npc);
                WorldPoint npcLocation = npc.getWorldLocation();
                if (npcLocation != null) {
                    int distance = npcLocation.distanceTo(playerLocation);
                    boolean npcMoved = tracker.lastNpcLocation != null
                            && !tracker.lastNpcLocation.equals(npcLocation);
                    int previousDistance = tracker.lastNpcLocation == null
                            ? distance
                            : tracker.lastNpcLocation.distanceTo(playerLocation);

                    if (npcMoved && distance < previousDistance && tracker.styleChangePending
                            && now - tracker.lastStyleChangeAt <= STYLE_CHANGE_WINDOW_MS
                            && !tracker.approachDetected) {
                        tracker.approachDetected = true;
                        debugPrayer("approach npc=" + npc.getIndex()
                                + " distance=" + previousDistance + "->" + distance
                                + " predicted=MELEE");
                        if (config.enableDefensivePrayer()) {
                            requestProtectionPrayer(Rs2PrayerEnum.PROTECT_MELEE,
                                    "approach after style change");
                        }
                    }

                    tracker.lastNpcLocation = npcLocation;
                }

                int animationId = npc.getAnimation();
                if (animationId != tracker.lastObservedAnimation) {
                    tracker.lastObservedAnimation = animationId;
                    processDemonAnimation(npc, tracker, animationId, "tick");
                }
            }
        } catch (RuntimeException e) {
            debugPrayer("tracking failed: " + e.getMessage());
        }
    }

    private void processDemonAnimation(NPC npc, DemonTracker tracker, int animationId, String source) {
        long now = System.currentTimeMillis();
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        debugPrayer("anim npc=" + npc.getIndex() + " id=" + animationId + " via=" + source
                + " last=" + tracker.lastAttackStyle + " lastNonMelee=" + tracker.lastNonMeleeStyle
                + " active=" + prayerName(Rs2Prayer.getActiveProtectionPrayer())
                + " desired=" + prayerName(desiredProtectionPrayer)
                + " distance=" + distanceToPlayer(npc, localPlayer)
                + " pending=" + tracker.styleChangePending);

        DemonAttackStyle attackStyle = attackStyleForAnimation(animationId);
        if (attackStyle != null) {
            tracker.lastAttackStyle = attackStyle;
            if (attackStyle != DemonAttackStyle.MELEE) {
                tracker.lastNonMeleeStyle = attackStyle;
            }
            tracker.styleChangePending = false;

            if (config.enableDefensivePrayer()) {
                requestProtectionPrayer(protectionFor(attackStyle),
                        "attack animation " + animationId + " " + attackStyle);
            }
            return;
        }

        if (animationId == CHANGE_ATTACK_STYLE_ANIMATION) {
            tracker.lastStyleChangeAt = now;
            tracker.styleChangePending = true;
            tracker.approachDetected = false;

            if (config.enableDefensivePrayer()) {
                Rs2PrayerEnum predicted = predictNextProtection(tracker);
                requestProtectionPrayer(predicted,
                        "style change after " + tracker.lastAttackStyle
                                + " predicted " + prayerName(predicted));
            }
        }
    }

    private Rs2PrayerEnum predictNextProtection(DemonTracker tracker) {
        switch (tracker.lastAttackStyle) {
            case MAGIC:
                return Rs2PrayerEnum.PROTECT_RANGE;
            case RANGE:
                return Rs2PrayerEnum.PROTECT_MAGIC;
            case MELEE:
                return tracker.lastNonMeleeStyle == DemonAttackStyle.MAGIC
                        ? Rs2PrayerEnum.PROTECT_RANGE
                        : Rs2PrayerEnum.PROTECT_MAGIC;
            default:
                return Rs2PrayerEnum.PROTECT_MELEE;
        }
    }

    private void requestProtectionPrayer(Rs2PrayerEnum prayer, String reason) {
        if (prayer == null) {
            return;
        }

        Rs2PrayerEnum previousDesired = desiredProtectionPrayer;
        desiredProtectionPrayer = prayer;
        lastPrayerReason = reason;
        if (previousDesired != prayer) {
            lastPrayerInvokeAt = 0L;
        }

        if (Rs2Prayer.isOutOfPrayer()) {
            debugPrayer("out of prayer; cannot activate " + prayerName(prayer) + " reason=" + reason);
            return;
        }

        Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
        if (active == prayer) {
            debugPrayer("hold " + prayerName(prayer) + " reason=" + reason);
            return;
        }

        if (prayerInvokePending) {
            debugPrayer("pending; desired=" + prayerName(prayer) + " reason=" + reason);
            return;
        }

        invokeProtectionPrayer(prayer);
        debugPrayer("request " + prayerName(active) + " -> " + prayerName(prayer)
                + " reason=" + reason);
    }

    private void invokeProtectionPrayer(Rs2PrayerEnum prayer) {
        if (prayer == null || prayerInvokePending
                || prayerExecutor == null || prayerExecutor.isShutdown()) {
            return;
        }

        prayerInvokePending = true;
        lastPrayerInvokeAt = System.currentTimeMillis();
        try {
            prayerExecutor.execute(() -> {
                try {
                    Rs2PrayerEnum target = desiredProtectionPrayer;
                    if (target == null || Rs2Prayer.isOutOfPrayer()
                            || Rs2Prayer.getActiveProtectionPrayer() == target) {
                        return;
                    }

                    NewMenuEntry menuEntry = new NewMenuEntry()
                            .param0(-1)
                            .param1(target.getIndex())
                            .opcode(MenuAction.CC_OP.getId())
                            .identifier(1)
                            .itemId(-1)
                            .option("Activate");
                    Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
                } catch (RuntimeException e) {
                    debugPrayer("prayer invoke failed: " + e.getMessage());
                } finally {
                    prayerInvokePending = false;
                }
            });
        } catch (RejectedExecutionException e) {
            prayerInvokePending = false;
            debugPrayer("prayer invoke rejected");
        }
    }

    private void verifyProtectionPrayer() {
        Rs2PrayerEnum desired = desiredProtectionPrayer;
        if (desired == null) {
            return;
        }

        Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
        if (active == desired) {
            if (lastConfirmedProtectionPrayer != desired) {
                lastConfirmedProtectionPrayer = desired;
                debugPrayer("confirmed " + prayerName(desired) + " reason=" + lastPrayerReason);
            }
            return;
        }

        lastConfirmedProtectionPrayer = active;
        if (prayerInvokePending || Rs2Prayer.isOutOfPrayer()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPrayerInvokeAt < PRAYER_RETRY_MS) {
            return;
        }

        invokeProtectionPrayer(desired);
        debugPrayer("retry " + prayerName(active) + " -> " + prayerName(desired)
                + " reason=" + lastPrayerReason);
    }

    /**
     * Cancels the protection prayer when no Tormented Demon is targeting the player, and forgets
     * the desired prayer so {@link #verifyProtectionPrayer()} cannot turn it back on. Demon attack
     * animations still request protection immediately, so reactive switching is unaffected.
     */
    private void releaseProtectionPrayer() {
        boolean hadDesired = desiredProtectionPrayer != null;
        desiredProtectionPrayer = null;
        lastPrayerReason = "";
        lastConfirmedProtectionPrayer = null;
        lastPrayerInvokeAt = 0L;
        // Checked even without a desired prayer: an activation already queued on the prayer
        // executor can land after the desired prayer was cleared.
        if (Rs2Prayer.getActiveProtectionPrayer() != null) {
            invokeDeactivateProtectionPrayer();
            if (hadDesired) {
                debugPrayer("released protection prayer; nothing is targeting the player");
            }
        }
    }

    private void invokeDeactivateProtectionPrayer() {
        if (prayerInvokePending || prayerExecutor == null || prayerExecutor.isShutdown()) {
            return;
        }
        prayerInvokePending = true;
        lastPrayerInvokeAt = System.currentTimeMillis();
        try {
            prayerExecutor.execute(() -> {
                try {
                    Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
                    if (active == null) {
                        return;
                    }
                    NewMenuEntry menuEntry = new NewMenuEntry()
                            .param0(-1)
                            .param1(active.getIndex())
                            .opcode(MenuAction.CC_OP.getId())
                            .identifier(1)
                            .itemId(-1)
                            .option("Activate");
                    Microbot.doInvoke(menuEntry, Rs2UiHelper.getDefaultRectangle());
                } catch (RuntimeException e) {
                    debugPrayer("prayer deactivate failed: " + e.getMessage());
                } finally {
                    prayerInvokePending = false;
                }
            });
        } catch (RejectedExecutionException e) {
            prayerInvokePending = false;
            debugPrayer("prayer deactivate rejected");
        }
    }

    private DemonTracker getDemonTracker(NPC npc) {
        return demonTrackers.computeIfAbsent(npc.getIndex(), index -> new DemonTracker());
    }

    private boolean isDemonFightingPlayer(NPC npc, Player player) {
        return npc.getInteracting() == player || player.getInteracting() == npc;
    }

    private int distanceToPlayer(NPC npc, Player player) {
        if (npc == null || player == null) {
            return -1;
        }
        WorldPoint npcLocation = npc.getWorldLocation();
        WorldPoint playerLocation = player.getWorldLocation();
        return npcLocation == null || playerLocation == null ? -1 : npcLocation.distanceTo(playerLocation);
    }

    private DemonAttackStyle attackStyleForAnimation(int animationId) {
        if (animationId == MELEE_ATTACK_ANIMATION) {
            return DemonAttackStyle.MELEE;
        }
        if (animationId == MAGIC_ATTACK_ANIMATION) {
            return DemonAttackStyle.MAGIC;
        }
        if (animationId == RANGE_ATTACK_ANIMATION) {
            return DemonAttackStyle.RANGE;
        }
        return null;
    }

    private Rs2PrayerEnum protectionFor(DemonAttackStyle style) {
        switch (style) {
            case MELEE:
                return Rs2PrayerEnum.PROTECT_MELEE;
            case MAGIC:
                return Rs2PrayerEnum.PROTECT_MAGIC;
            case RANGE:
                return Rs2PrayerEnum.PROTECT_RANGE;
            default:
                return null;
        }
    }

    private String prayerName(Rs2PrayerEnum prayer) {
        return prayer == null ? "NONE" : prayer.name().replace("PROTECT_", "");
    }

    private void debugPrayer(String message) {
        if (config.debugPrayer()) {
            Microbot.log("[TD prayer] " + message);
        }
    }

    private void clearPrayerState() {
        demonTrackers.clear();
        desiredProtectionPrayer = null;
        lastPrayerInvokeAt = 0L;
        lastPrayerReason = "";
        lastConfirmedProtectionPrayer = null;
        prayerInvokePending = false;
    }

    private enum DemonAttackStyle {
        UNKNOWN,
        MELEE,
        MAGIC,
        RANGE
    }

    private static final class DemonTracker {
        private DemonAttackStyle lastAttackStyle = DemonAttackStyle.UNKNOWN;
        private DemonAttackStyle lastNonMeleeStyle = DemonAttackStyle.UNKNOWN;
        private WorldPoint lastNpcLocation;
        private int lastObservedAnimation = -1;
        private long lastStyleChangeAt;
        private boolean styleChangePending;
        private boolean approachDetected;
    }

}

