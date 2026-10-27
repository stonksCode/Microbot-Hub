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
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
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
        name = PluginDescriptor.zerozero + "Tormented Demons",
        description = "Automates restocking, prayer flicking, and gear switching during Tormented Demon",
        tags = {"tormented", "flicker", "weapon", "switch", "microbot"},
        version = TormentedDemonPlugin.version,
        minClientVersion = "2.6.27",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TormentedDemonPlugin extends Plugin {

    public static final String version = "1.3.0";

    private static final int CHANGE_ATTACK_STYLE_ANIMATION = 11387;
    private static final int MELEE_ATTACK_ANIMATION = 11392;
    private static final int MAGIC_ATTACK_ANIMATION = 11388;
    private static final int RANGE_ATTACK_ANIMATION = 11389;

    private static final int TORMENTED_VENGEANCE_SPECIAL = 2856;
    private static final int FIREBOMB_SEARCH_RADIUS = 5;
    private static final int FIREBOMB_MAX_PATH_STEPS = 3;
    private static final int FIREBOMB_TRACK_DISTANCE = 1;
    private static final int GFX_FALLBACK_DISTANCE = 3;
    private static final long DODGE_TICK_MS = 100L;
    private static final long DODGE_RECLICK_MS = 300L;
    private static final long FIREBOMB_LIFETIME_MS = 1600L;
    private static final long PRAYER_RETRY_MS = 400L;
    private static final long STYLE_CHANGE_WINDOW_MS = 5000L;
    private static final long STYLE_CHANGE_TRANSITION_MS = 6000L;
    private static final int TRANSITION_STANDOFF_MIN = 4;
    private static final int TRANSITION_STANDOFF_PREFERRED = 5;

    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledExecutorService prayerExecutor;

    private final Map<Integer, DemonTracker> demonTrackers = new ConcurrentHashMap<>();
    private final Map<WorldPoint, Long> fireBombExpiries = new ConcurrentHashMap<>();
    private final Object dodgeLoopLock = new Object();
    private ScheduledFuture<?> dodgeLoopFuture;
    private volatile WorldPoint fireBombOrigin;
    private volatile long lastDodgeClickAt;
    private volatile Rs2PrayerEnum desiredProtectionPrayer;
    private volatile Rs2PrayerEnum lastConfirmedProtectionPrayer;
    private volatile long lastPrayerInvokeAt;
    private volatile String lastPrayerReason = "";
    private volatile boolean prayerInvokePending;
    private volatile boolean styleChangeTransitionActive;
    private volatile long styleChangeTransitionAt;
    private volatile NPC styleChangeTransitionNpc;
    private volatile int lastTransitionHullDistance = Integer.MAX_VALUE;
    private volatile long lastTransitionDistanceCheckAt;
    private volatile boolean transitionKiteRequested;

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
            try {
                StringJoiner gearList = new StringJoiner(",");
                Rs2Equipment.all().forEach(item -> {
                    if (item == null || item.getId() == -1 || item.getId() == 6512) return;
                    String itemName = item.getName();
                    if (itemName != null && !itemName.isEmpty()) {
                        gearList.add(itemName);
                    }
                });

                String gearString = gearList.toString();
                if (gearString.isEmpty()) {
                    Microbot.log("No gear found to copy.");
                    return;
                }

                StringSelection selection = new StringSelection(gearString);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                Microbot.log("Current gear copied to clipboard: " + gearString);
            } catch (Exception e) {
                Microbot.log("Failed to copy gear to clipboard: " + e.getMessage());
            }
        }
    }



    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!config.dodgeFirebombs()) {
            return;
        }
        trackFireBomb(event.getProjectile(), event.getPosition());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (config.enableDefensivePrayer() || config.debugPrayer()) {
            updateDemonTracking();
        }
        if (config.enableDefensivePrayer()) {
            verifyProtectionPrayer();
        }
        if (!config.dodgeFirebombs()) {
            return;
        }
        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            trackFireBomb(projectile, projectile.getTarget());
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject graphicsObject = event.getGraphicsObject();
        if (graphicsObject == null || graphicsObject.getId() != TORMENTED_VENGEANCE_SPECIAL) {
            return;
        }
        if (!config.dodgeFirebombs()) {
            return;
        }

        WorldPoint graphicsWorldPoint = toWorldPoint(graphicsObject.getLocation());
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (graphicsWorldPoint == null || localPlayer == null) {
            return;
        }

        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (playerLocation == null || graphicsWorldPoint.distanceTo(playerLocation) > GFX_FALLBACK_DISTANCE) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean alreadyTracked = fireBombExpiries.containsKey(graphicsWorldPoint);
        debugDodge("Special gfx at " + graphicsWorldPoint + " player=" + playerLocation
                + " alreadyTracked=" + alreadyTracked);
        if (alreadyTracked) {
            return;
        }

        addFireBomb(graphicsWorldPoint, now + FIREBOMB_LIFETIME_MS,
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

        WorldPoint target = toWorldPoint(targetLocal);
        if (target == null) {
            target = projectile.getTargetPoint();
        }
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (target == null || localPlayer == null) {
            return;
        }

        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (playerLocation == null || target.distanceTo(playerLocation) > FIREBOMB_TRACK_DISTANCE) {
            return;
        }

        if (fireBombExpiries.containsKey(target)) {
            return;
        }

        addFireBomb(target, System.currentTimeMillis() + FIREBOMB_LIFETIME_MS,
                "projectile " + projectile.getId() + " source=" + actorName(source));
    }

    private void addFireBomb(WorldPoint target, long expiry, String source) {
        long now = System.currentTimeMillis();
        if (target == null || expiry <= now) {
            return;
        }

        Long currentExpiry = fireBombExpiries.get(target);
        if (currentExpiry != null && currentExpiry >= expiry) {
            return;
        }

        if (fireBombExpiries.isEmpty() || fireBombOrigin == null) {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            fireBombOrigin = localPlayer == null ? null : localPlayer.getWorldLocation();
        }

        fireBombExpiries.put(target, Math.max(expiry, currentExpiry == null ? 0L : currentExpiry));
        tormentedDemonScript.dodgeUntil = Math.max(tormentedDemonScript.dodgeUntil, expiry + 200L);
        debugDodge("Tracking firebomb at " + target + " via " + source + " in " + (expiry - now)
                + "ms; active=" + fireBombExpiries.size() + " origin=" + fireBombOrigin);
        ensureDodgeLoop();
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
                dodgeLoopFuture = scheduledExecutorService.scheduleWithFixedDelay(this::dodgeTickSafely,
                        DODGE_TICK_MS, DODGE_TICK_MS, TimeUnit.MILLISECONDS);
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
        fireBombExpiries.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (fireBombExpiries.isEmpty()) {
            stopDodgeLoop();
            fireBombOrigin = null;
            tormentedDemonScript.dodgeUntil = 0;
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        Set<WorldPoint> bombs = new HashSet<>(fireBombExpiries.keySet());
        boolean onBomb = bombs.contains(playerLocation);
        boolean nearOnlyKnownBomb = bombs.size() < 2 && bombs.stream()
                .anyMatch(bomb -> bomb.distanceTo(playerLocation) <= 1);
        boolean nearOrigin = bombs.size() < 2 && fireBombOrigin != null
                && fireBombOrigin.distanceTo(playerLocation) <= 1
                && bombs.stream().anyMatch(bomb -> bomb.distanceTo(fireBombOrigin) <= 1);
        if (!onBomb && !nearOnlyKnownBomb && !nearOrigin) {
            return;
        }

        if (now - lastDodgeClickAt < DODGE_RECLICK_MS) {
            return;
        }

        Set<WorldPoint> npcTiles = getNpcOccupiedTiles(playerLocation, FIREBOMB_SEARCH_RADIUS + 2);
        WorldPoint safeTile = chooseSafeTile(playerLocation, bombs, npcTiles);
        if (safeTile == null) {
            lastDodgeClickAt = now;
            debugDodge("No safe tile from " + playerLocation + "; bombs=" + bombs
                    + " npcTiles=" + npcTiles.size() + " origin=" + fireBombOrigin);
            return;
        }

        boolean clicked = clickDodgeTile(safeTile);
        lastDodgeClickAt = now;
        debugDodge("Dodge click " + safeTile + " from " + playerLocation
                + " clicked=" + clicked + " bombs=" + bombs + " npcTiles=" + npcTiles.size()
                + " origin=" + fireBombOrigin);
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

    private WorldPoint chooseSafeTile(WorldPoint playerLocation, Set<WorldPoint> bombs,
                                      Set<WorldPoint> npcTiles) {
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(playerLocation, FIREBOMB_SEARCH_RADIUS);
        if (reachable.isEmpty()) {
            reachable = Rs2Tile.getReachableTilesFromTileIgnoreCollision(playerLocation, FIREBOMB_SEARCH_RADIUS);
        }
        if (reachable.isEmpty()) {
            return null;
        }

        int requiredMargin = bombs.size() < 2 ? 2 : 1;
        WorldPoint safeTile = selectSafeTile(reachable, bombs, npcTiles, requiredMargin, FIREBOMB_MAX_PATH_STEPS);
        if (safeTile == null) {
            safeTile = selectSafeTile(reachable, bombs, npcTiles, requiredMargin, FIREBOMB_SEARCH_RADIUS);
        }
        return safeTile;
    }

    private WorldPoint selectSafeTile(Map<WorldPoint, Integer> reachable, Set<WorldPoint> bombs,
                                      Set<WorldPoint> npcTiles, int requiredMargin, int maxSteps) {
        WorldPoint best = null;
        int bestSteps = Integer.MAX_VALUE;
        int bestMargin = -1;

        for (Map.Entry<WorldPoint, Integer> entry : reachable.entrySet()) {
            WorldPoint candidate = entry.getKey();
            int steps = entry.getValue();
            if (steps < 1 || steps > maxSteps || bombs.contains(candidate) || npcTiles.contains(candidate)) {
                continue;
            }

            int margin = Integer.MAX_VALUE;
            for (WorldPoint bomb : bombs) {
                margin = Math.min(margin, bomb.distanceTo(candidate));
            }
            if (bombs.size() < 2 && fireBombOrigin != null) {
                margin = Math.min(margin, fireBombOrigin.distanceTo(candidate));
            }
            if (margin < requiredMargin) {
                continue;
            }

            if (steps < bestSteps || (steps == bestSteps && margin > bestMargin)) {
                best = candidate;
                bestSteps = steps;
                bestMargin = margin;
            }
        }

        return best;
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
        fireBombExpiries.clear();
        fireBombOrigin = null;
        lastDodgeClickAt = 0L;
        stopDodgeLoop();
        if (tormentedDemonScript != null) {
            tormentedDemonScript.dodgeUntil = 0L;
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
        try {
            for (var npcModel : Microbot.getRs2NpcCache().query().within(playerLocation, 15).toList()) {
                NPC npc = npcModel == null ? null : npcModel.getNpc();
                if (npc == null || npc.isDead() || !isTormentedDemon(npc)
                        || !isDemonFightingPlayer(npc, localPlayer)) {
                    continue;
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

