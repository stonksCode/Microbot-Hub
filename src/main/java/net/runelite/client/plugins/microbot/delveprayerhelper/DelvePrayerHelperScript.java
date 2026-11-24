package net.runelite.client.plugins.microbot.delveprayerhelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperProjectile;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperState;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DelvePrayerHelperScript extends Script
{
    /** Delay after the last tracked projectile lands before the protection prayer is dropped. */
    private static final long PROTECTION_DROP_DELAY_MS = 1000;

    /**
     * How long the boss may be missing from the NPC cache before the fight is treated as over.
     * The boss changes id (shielded / burrowed) and can drop out of the cache for a moment. Ending the
     * fight on the first miss wiped every tracked projectile and turned all prayers off in the middle
     * of an attack sequence.
     */
    private static final long BOSS_LOST_GRACE_MS = 3000;

    /** Script loop period. Short, so the next switch goes out right after a hit lands (1 tick = 600ms). */
    private static final long LOOP_PERIOD_MS = 20;

    /** Client cycles per game tick. */
    private static final int CYCLES_PER_TICK = 30;

    /**
     * An attack is "imminent" this many ticks before it lands. While one is imminent the loop does
     * nothing but protection prayers: no movement, eating, potions, offensive prayer or recasts.
     * The user's priority is explicit - a prayer switch must never queue behind anything else.
     */
    private static final int IMMINENT_TICKS = 2;
    private static final int IMMINENT_CYCLES = IMMINENT_TICKS * CYCLES_PER_TICK;

    /**
     * {@code Script.run()} performs client-thread round trips (run energy) and writes config on every
     * call. Calling it from the 20ms prayer loop would stall a switch, so it only runs in quiet
     * moments and at this interval.
     */
    private static final long SUPER_RUN_INTERVAL_MS = 3000;

    /**
     * A protection click is sent with {@link Client#menuAction} straight on the client thread, so it can
     * no longer be dropped by the mouse gesture arbiter. The prayer still only shows up as active once the
     * game processes the click, so the same prayer must not be clicked twice in that window (a second
     * click would switch it straight back off). Fight logs show confirmation can take just over one game
     * tick, so the retry waits two ticks; if the prayer is still not active after that the click really
     * was lost and it is sent again.
     */
    private static final int CLICK_RETRY_TICKS = 2;

    /** Minimum ticks between two cast attempts of the same spell, so its status can update before re-checking. */
    private static final int SPELL_RETRY_TICKS = 5;

    /**
     * Quick (menu-click, no tab switch) cast attempts that may fail to take effect before falling back to
     * the slower "open the magic tab and click" cast.
     */
    private static final int QUICK_CAST_ATTEMPTS = 2;

    /** The slower fallback cast blocks the loop for a while, so it only runs after this much quiet time. */
    private static final long FALLBACK_CAST_QUIET_MS = 2000;

    private static final int[] BOSS_NPC_IDS = {
            NpcID.DOM_BOSS,
            NpcID.DOM_BOSS_SHIELDED,
            NpcID.DOM_BOSS_BURROWED
    };

    private final DelvePrayerHelperConfig config;
    private final DelveFightLogger fightLogger;
    private final DelveHazardTracker hazardTracker;
    private final DelveMovement movement;

    /**
     * Attacks that have not landed yet. Written from the client thread ({@code ProjectileMoved}) and read
     * from the script thread, so it must be concurrent. This is a set of projectiles rather than a map
     * keyed by landing cycle, so two attacks landing on the same cycle can no longer overwrite each other.
     */
    private final Set<Projectile> incomingProjectiles = ConcurrentHashMap.newKeySet();

    private volatile DelvePrayerHelperState state = DelvePrayerHelperState.IDLE;

    /** True while the script is scheduled. Client-tick caching is skipped once it is shut down. */
    private volatile boolean active = false;

    /**
     * Snapshot of the boss, refreshed on the client thread once per game tick. The script thread must
     * never touch {@link Rs2NpcModel} directly: every accessor may hop to the client thread.
     */
    private volatile BossSnapshot bossSnapshot = null;

    /** Snapshot of the attack the protection prayer is currently aimed at. */
    private volatile ProjectileSnapshot nextProjectile = null;
    private volatile Rs2PrayerEnum wantedProtection = null;
    private volatile boolean protectionPending = false;
    private volatile boolean attackImminent = false;
    /** Set on the client thread when prayer points ran out; drunk on the script thread. */
    private volatile boolean emergencyPrayerDrink = false;

    private int lastClientScanTick = -100;
    private long lastSuperRunTime = 0;

    /** Written on the client thread (prayer step) and read on the script thread (recast quiet check). */
    private volatile long lastProjectileTime = 0;
    private long bossLastSeen = 0;
    private long fightStartTime = 0;
    private boolean protectionDropped = false;
    /** True once the boss has been seen alive so prayers are only cleaned up for a fight we took part in. */
    private volatile boolean inFight = false;

    // Click bookkeeping (game ticks), so clicks are sent once and never block the loop.
    private Rs2PrayerEnum lastClickedPrayer = null;
    private int lastClickTick = -100;
    private int lastDropClickTick = -100;
    private int lastOffensiveClickTick = -100;
    private Rs2PrayerEnum awaitingConfirmation = null;
    /** The protection prayer this plugin last turned on. Only that prayer is ever turned off by us. */
    private Rs2PrayerEnum managedProtection = null;
    /** The protection prayer last observed active after one of our clicks. Used to detect external clears. */
    private Rs2PrayerEnum lastConfirmedProtection = null;
    private int lastConfirmedTick = -100;

    // Recast bookkeeping.
    private int lastRecastCheckTick = -100;
    private int lastThrallCastTick = -100;
    private int lastDeathChargeCastTick = -100;
    private int thrallCastAttempts = 0;
    private int deathChargeCastAttempts = 0;

    // Hazard / survival bookkeeping.
    private int lastConsumableTick = -100;
    private int lastPrayerPotTick = -100;
    private int lastEatTick = -100;
    private int lastGateLogTick = -100;
    private int lastOutOfPrayerLogTick = -100;
    private int lastOffensiveSkipLogTick = -100;
    private int lastRecastSkipLogTick = -100;
    private int lastLoopFailureTick = -100;
    private int lastTickLogTick = -100;
    private boolean rockFuseLogged = false;
    private boolean onAcidLogged = false;
    private int lastAcidCount = 0;

    // Volatile earth / earthen shield bookkeeping.
    private int lastTotemClickTick = -100;
    private WorldPoint lastTotemTargetTile = null;
    /** Totems this script clicked. Two of them gone means the pair is complete and no more may be attacked. */
    private final Set<WorldPoint> attackedTotems = new HashSet<>();
    private boolean shockwaveLogged = false;
    private int lastDestroyedNodeCount = 0;
    private WorldPoint lastLoggedShieldTile = null;

    @Inject
    public DelvePrayerHelperScript(DelvePrayerHelperConfig config,
                                   DelveFightLogger fightLogger,
                                   DelveHazardTracker hazardTracker)
    {
        this.config = config;
        this.fightLogger = fightLogger;
        this.hazardTracker = hazardTracker;
        this.movement = new DelveMovement(config, hazardTracker);
    }

    public boolean run()
    {
        active = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                tick();
            }
            catch (Exception ex)
            {
                log.warn("Exception in Delve prayer loop", ex);
            }
        }, 0, LOOP_PERIOD_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Called from the client thread on every {@code ClientTick} (~20ms).
     *
     * <p>This is where every client read happens. The script thread only consumes the snapshots,
     * so a prayer switch never waits for a client-thread round trip.</p>
     */
    void onClientTick()
    {
        if (!active)
        {
            return;
        }

        final Client client = Microbot.getClient();
        if (client == null)
        {
            return;
        }

        // NPC / tile-object caches are only valid while logged in. Clearing the snapshots here keeps
        // the script thread from acting on stale data during a logout or world hop.
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            bossSnapshot = null;
            nextProjectile = null;
            wantedProtection = null;
            protectionPending = false;
            attackImminent = false;
            return;
        }

        try
        {
            // Player tile: cheap, and the logger/overlay read it too.
            DelveMovement.updateRawPlayerLocation(DelveMovement.readRawPlayerLocation(client));

            final int currentTick = client.getTickCount();
            if (currentTick != lastClientScanTick)
            {
                lastClientScanTick = currentTick;

                // Boss animation and acid pools only change on a game tick.
                bossSnapshot = scanBoss();
                BossSnapshot boss = bossSnapshot;
                hazardTracker.updateRockFuse(boss == null ? -1 : boss.animation, currentTick);
                hazardTracker.scanAcidTiles();
                hazardTracker.scanShockwave(boss == null ? -1 : boss.animation);
                logHazardState(DelveMovement.getRawPlayerLocation());
                logShockwaveState();
                updateFightState(client, boss, System.currentTimeMillis());
            }

            // The prayer step runs on every client tick (~20ms), not once per game tick: a projectile
            // lands at a specific cycle, so waiting for the next game tick would delay the switch that
            // has to happen the instant the previous attack hits.
            if (inFight)
            {
                runPrayerStep(client, currentTick);
            }
            else
            {
                nextProjectile = null;
                wantedProtection = null;
                protectionPending = false;
                attackImminent = false;
            }
        }
        catch (Exception ex)
        {
            log.warn("Delve client tick failed", ex);
        }
    }

    /** Called from the client thread for every movement of a Doom attack projectile. */
    void trackProjectile(Projectile projectile)
    {
        incomingProjectiles.add(projectile);
    }

    private BossSnapshot scanBoss()
    {
        Rs2NpcModel boss = Microbot.getRs2NpcCache().query()
                .withIds(BOSS_NPC_IDS)
                .nearest();
        if (boss == null)
        {
            return null;
        }
        return new BossSnapshot(boss.getId(), boss.getName(),
                DelveMovement.fromLocal(Microbot.getClient(), boss.getLocalLocation()),
                boss.getAnimation(), !boss.isDead());
    }

    /**
     * Starts/ends the fight and keeps the prayer cleanup scoped to fights this plugin took part in.
     * Runs on the client thread.
     */
    private void updateFightState(Client client, BossSnapshot boss, long now)
    {
        boolean bossAlive = boss != null && boss.alive;
        boolean bossMissingBriefly = !bossAlive && inFight && boss == null
                && now - bossLastSeen <= BOSS_LOST_GRACE_MS;

        if (bossAlive)
        {
            if (!inFight)
            {
                inFight = true;
                fightStartTime = now;
                fightLogger.logFightStart(boss.name, boss.id);
            }
            bossLastSeen = now;
            return;
        }

        if (bossMissingBriefly)
        {
            return;
        }

        // Not in the Delve encounter (or it is over): never interfere with the player's prayers elsewhere.
        if (inFight)
        {
            disableManagedPrayers(client);
            fightLogger.logFightEnd(now - fightStartTime);
        }
        endFight();
    }

    /**
     * The entire protection-prayer decision. Runs on the client thread so reads are direct and the
     * click is queued immediately; nothing else is allowed in front of it.
     */
    private void runPrayerStep(Client client, int currentTick)
    {
        final int currentCycle = client.getGameCycle();
        final long now = System.currentTimeMillis();

        removeLandedProjectiles(currentCycle);

        Projectile next = soonestProjectile();
        Rs2PrayerEnum wanted = next == null ? null : protectionFor(next.getId());

        boolean pending = false;
        if (next != null)
        {
            protectionDropped = false;
            lastProjectileTime = now;
            pending = ensureProtection(next, client, currentCycle, currentTick);
        }
        else
        {
            // Start the idle timer on the first tick instead of treating "never" as an hour ago,
            // so enabling the plugin mid-fight does not drop a prayer before projectiles register.
            if (lastProjectileTime == 0)
            {
                lastProjectileTime = now;
            }

            // Keep the prayer up through a Rock Throw fuse: the shatter clears it anyway, and dropping
            // it early only makes the re-pray after the shatter slower.
            if (!protectionDropped && now - lastProjectileTime > PROTECTION_DROP_DELAY_MS
                    && !hazardTracker.isRockFuseActive())
            {
                dropProtectionPrayer(client, currentTick);
                protectionDropped = true;
            }
        }

        nextProjectile = next == null ? null : new ProjectileSnapshot(next.getId(), next.getEndCycle(), wanted);
        wantedProtection = wanted;
        protectionPending = pending;
        attackImminent = next != null && next.getEndCycle() - currentCycle <= IMMINENT_CYCLES;
    }

    private static final class BossSnapshot
    {
        final int id;
        final String name;
        final WorldPoint location;
        final int animation;
        final boolean alive;

        BossSnapshot(int id, String name, WorldPoint location, int animation, boolean alive)
        {
            this.id = id;
            this.name = name;
            this.location = location;
            this.animation = animation;
            this.alive = alive;
        }
    }

    private static final class ProjectileSnapshot
    {
        final int id;
        final int endCycle;
        final Rs2PrayerEnum protection;

        ProjectileSnapshot(int id, int endCycle, Rs2PrayerEnum protection)
        {
            this.id = id;
            this.endCycle = endCycle;
            this.protection = protection;
        }
    }

    void tick()
    {
        if (!active)
        {
            return;
        }

        // Cheap stop conditions with no client-thread round trips. super.run() is deliberately not
        // called here: it reads run energy on the client thread and writes config, which would stall
        // a switch. It runs from the quiet branch below instead.
        if (ScriptPauseController.isPaused()
                || !Microbot.isLoggedIn()
                || Microbot.pauseAllScripts.get()
                || Thread.currentThread().isInterrupted())
        {
            return;
        }

        final Client client = Microbot.getClient();
        if (client == null)
        {
            return;
        }

        final int currentTick = client.getTickCount();
        final int currentCycle = client.getGameCycle();
        final long now = System.currentTimeMillis();

        if (!inFight)
        {
            setState(DelvePrayerHelperState.IDLE);
            if (now - lastSuperRunTime >= SUPER_RUN_INTERVAL_MS)
            {
                lastSuperRunTime = now;
                super.run();
            }
            return;
        }

        final BossSnapshot boss = bossSnapshot;
        final WorldPoint playerLocation = DelveMovement.getRawPlayerLocation();
        final ProjectileSnapshot next = nextProjectile;
        final boolean pending = protectionPending;
        final boolean imminent = attackImminent;

        // Emergency prayer restore when points ran out. This is the only consumable allowed while an
        // attack is imminent, because without points no protection prayer can be activated at all.
        if (emergencyPrayerDrink)
        {
            emergencyPrayerDrink = false;
            if (config.enablePrayerRestoration())
            {
                try
                {
                    drinkPrayerPotion(currentTick, true);
                }
                catch (Exception ex)
                {
                    logLoopFailure("emergency prayer potion", currentTick, ex);
                }
            }
        }

        DelvePrayerHelperState movementState = null;
        DelvePrayerHelperState consumableState = null;

        // Everything that is not a protection prayer is skipped while a switch is unconfirmed or an
        // attack is about to land. This is the user's explicit priority: prayer first, always.
        if (!pending && !imminent)
        {
            try
            {
                movementState = handleSurvival(boss, currentTick, playerLocation);
            }
            catch (Exception ex)
            {
                logLoopFailure("survival", currentTick, ex);
            }

            try
            {
                updateOffensivePrayer(boss, currentTick);
            }
            catch (Exception ex)
            {
                logLoopFailure("offensive prayer", currentTick, ex);
            }

            try
            {
                consumableState = handleConsumables(next, currentCycle, currentTick);
            }
            catch (Exception ex)
            {
                logLoopFailure("consumables", currentTick, ex);
            }

            try
            {
                updateRecasts(next, currentCycle, currentTick, now);
            }
            catch (Exception ex)
            {
                logLoopFailure("recasts", currentTick, ex);
            }

            if (now - lastSuperRunTime >= SUPER_RUN_INTERVAL_MS)
            {
                lastSuperRunTime = now;
                super.run();
            }
        }

        Rs2PrayerEnum wanted = wantedProtection;
        if (wanted != null)
        {
            setState(stateFor(wanted));
        }
        else if (movementState != null)
        {
            setState(movementState);
        }
        else if (consumableState != null)
        {
            setState(consumableState);
        }
        else
        {
            setState(DelvePrayerHelperState.IDLE);
        }

        if (config.verboseTickLog() && currentTick != lastTickLogTick)
        {
            lastTickLogTick = currentTick;
            try
            {
                logTick(client, currentTick, currentCycle, boss, next, wanted, pending, playerLocation);
            }
            catch (Exception ex)
            {
                logLoopFailure("tick log", currentTick, ex);
            }
        }
    }

    /**
     * Logs a failure in one subsystem without letting it abort the rest of the loop. Rate limited to
     * one line per game tick so a repeating error cannot flood the fight log.
     */
    private void logLoopFailure(String what, int currentTick, Exception ex)
    {
        log.warn("Delve {} failed", what, ex);
        if (currentTick == lastLoopFailureTick)
        {
            return;
        }
        lastLoopFailureTick = currentTick;
        fightLogger.log("ERROR", what + " failed: " + ex);
    }

    /**
     * An attack has landed once the game cycle is past its end cycle (plus the configured margin).
     * Landed attacks are dropped here, which is what moves the prayer on to the next attack in line.
     */
    private void removeLandedProjectiles(int currentCycle)
    {
        final int margin = config.landingMarginCycles();
        Iterator<Projectile> it = incomingProjectiles.iterator();
        while (it.hasNext())
        {
            Projectile projectile = it.next();
            if (projectile.getEndCycle() + margin >= currentCycle)
            {
                continue;
            }
            Rs2PrayerEnum protection = protectionFor(projectile.getId());
            if (protection != null)
            {
                boolean active = Rs2Prayer.isPrayerActive(protection);
                if (active)
                {
                    fightLogger.logDebug("LANDED", "hit landed, " + protection + " was active");
                }
                else
                {
                    fightLogger.logDebug("LANDED", "hit landed UNPROTECTED (" + protection + " was not active)");
                }
            }
            it.remove();
        }
    }

    private Projectile soonestProjectile()
    {
        Projectile soonest = null;
        for (Projectile projectile : incomingProjectiles)
        {
            if (soonest == null || projectile.getEndCycle() < soonest.getEndCycle())
            {
                soonest = projectile;
            }
        }
        return soonest;
    }

    /**
     * Makes sure the protection prayer for this attack is on. Never blocks: the click goes out once and the
     * loop keeps running, so the next attack in a fast sequence is handled on time.
     *
     * @return true when a switch is still pending (the wanted prayer is not confirmed active yet)
     */
    private boolean ensureProtection(Projectile projectile, Client client, int currentCycle, int currentTick)
    {
        Rs2PrayerEnum wanted = protectionFor(projectile.getId());
        if (wanted == null)
        {
            return false;
        }

        // A protection drop that was clicked a moment ago may not have shown up yet, so an "active" prayer
        // right after a drop cannot be trusted: click anyway (the drop is processed first, ours turns it back on).
        boolean dropMayBePending = currentTick - lastDropClickTick <= 1;
        boolean active = Rs2Prayer.isPrayerActive(wanted) && !dropMayBePending;

        if (active)
        {
            if (awaitingConfirmation == wanted)
            {
                fightLogger.logDebug("PRAYER", wanted + " confirmed active, hit in "
                        + (projectile.getEndCycle() - currentCycle) + " cycles");
                awaitingConfirmation = null;
                lastConfirmedProtection = wanted;
                lastConfirmedTick = currentTick;
            }
            return false;
        }

        if (wanted != lastClickedPrayer)
        {
            // The previous confirmation belongs to a different prayer and says nothing about this one.
            lastConfirmedProtection = null;
        }

        // Rock Throw shatters overheads and clears the protection prayer without us clicking anything.
        // The normal click gate would then hold the re-pray for up to two ticks and the first shrapnel
        // hit would land unprotected. Detect the external clear and send the click immediately.
        boolean externalClear = lastConfirmedProtection == wanted
                && !Rs2Prayer.isPrayerActive(wanted)
                && lastClickTick <= lastConfirmedTick
                && currentTick > lastConfirmedTick;

        if (externalClear)
        {
            fightLogger.logDebug("PRAYER", "EXTERNAL CLEAR: " + wanted + " was active and is now off "
                    + "(rock shatter or drained); resetting click gate");
            lastClickedPrayer = null;
            awaitingConfirmation = null;
            lastConfirmedProtection = null;
            managedProtection = wanted;
        }

        if (Rs2Prayer.isOutOfPrayer())
        {
            if (currentTick != lastOutOfPrayerLogTick)
            {
                lastOutOfPrayerLogTick = currentTick;
                fightLogger.logDebug("PRAYER", "out of prayer points, cannot activate " + wanted);
            }
            // Cannot drink here: this runs on the client thread and a potion click must not block it.
            // The script thread handles the emergency drink, but only when restoration is enabled.
            emergencyPrayerDrink = true;
            return true;
        }

        if (wanted == lastClickedPrayer && currentTick - lastClickTick < CLICK_RETRY_TICKS)
        {
            if (currentTick != lastGateLogTick)
            {
                lastGateLogTick = currentTick;
                fightLogger.logDebug("PRAYER", "holding retry for " + wanted + " (clicked "
                        + (currentTick - lastClickTick) + "t ago, hit in "
                        + (projectile.getEndCycle() - currentCycle) + " cycles)");
            }
            return true;
        }

        // Never toggle off a prayer that is already active. The gate above is only about a click that
        // has not been confirmed yet.
        if (Rs2Prayer.isPrayerActive(wanted))
        {
            return false;
        }

        fightLogger.logDebug("PRAYER", "clicking " + wanted + " (direct), hit in "
                + (projectile.getEndCycle() - currentCycle) + " cycles (tick " + currentTick + ")");
        // Direct action on the client thread: no input lock, no mouse gesture, no delay.
        // Do not use toggle(prayer, true): it waits for the server to confirm the prayer (a full tick
        // or more), which stalled the loop and made back-to-back switches miss.
        if (!clickPrayerDirect(client, wanted, true))
        {
            fightLogger.logDebug("PRAYER", "direct click rejected for " + wanted
                    + " (widget missing or not logged in); falling back to a normal click");
            clickPrayer(wanted, true);
        }
        lastClickedPrayer = wanted;
        lastClickTick = currentTick;
        awaitingConfirmation = wanted;
        managedProtection = wanted;
        return true;
    }

    private void dropProtectionPrayer(Client client, int currentTick)
    {
        Rs2PrayerEnum protectionPrayer = Rs2Prayer.getActiveProtectionPrayer();
        if (protectionPrayer == null)
        {
            managedProtection = null;
            return;
        }

        // Only turn off the prayer this plugin turned on. If the player switched manually, leave theirs alone.
        if (managedProtection == null || protectionPrayer != managedProtection)
        {
            return;
        }

        fightLogger.logDebug("PRAYER", "dropping " + protectionPrayer + " (no attacks in flight)");
        if (!clickPrayerDirect(client, protectionPrayer, false))
        {
            clickPrayer(protectionPrayer, false);
        }
        lastDropClickTick = currentTick;
        lastClickedPrayer = null;
        awaitingConfirmation = null;
        lastConfirmedProtection = null;
        managedProtection = null;
    }

    /**
     * Sends a prayer click straight into the game's action handler on the client thread.
     *
     * <p>This deliberately bypasses the Microbot mouse gesture pipeline. {@code Microbot.doInvokeQuick}
     * ends up in {@code InputLoop.run}, which silently aborts a gesture while real input is detected or
     * while another Microbot gesture (for example an eat or a potion) holds the input lock. Those aborts
     * were the lost prayer clicks seen in the fight logs: the click was reported as sent but never reached
     * the game. {@link Client#menuAction} applies the action immediately, with no lock and no delay.</p>
     *
     * <p>Must only be called on the client thread.</p>
     *
     * @return true when the action was handed to the game
     */
    private boolean clickPrayerDirect(Client client, Rs2PrayerEnum prayer, boolean on)
    {
        if (client == null || !client.isClientThread())
        {
            return false;
        }
        // The widget must exist for the action lookup to resolve; clicking a missing widget is a no-op
        // that still looks like a sent click.
        if (client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null
                || client.getWidget(prayer.getIndex()) == null)
        {
            return false;
        }
        client.menuAction(-1, prayer.getIndex(), MenuAction.CC_OP, 1, -1,
                on ? "Activate" : "Deactivate", prayer.getName());
        return true;
    }

    /**
     * Sends a single prayer click without blocking and without a widget lookup.
     *
     * <p>{@link Rs2Prayer#toggle(Rs2PrayerEnum)} is void and performs two client-thread round trips
     * ({@code Rs2Widget.isHidden} and {@code Rs2Widget.getWidget}). From the script thread each hop can
     * block for up to a game tick, which is exactly what made back-to-back switches miss. This builds
     * the same menu entry but always uses the fallback rectangle, so it never touches a widget.</p>
     *
     * <p>Only for non-critical clicks from the script thread (offensive prayer, fight-end cleanup).
     * Protection switches use {@link #clickPrayerDirect} instead.</p>
     */
    private static void clickPrayer(Rs2PrayerEnum prayer, boolean on)
    {
        NewMenuEntry menuEntry = new NewMenuEntry()
                .param0(-1)
                .param1(prayer.getIndex())
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .option(on ? "Activate" : "Deactivate");
        Microbot.doInvokeQuick(menuEntry, Rs2UiHelper.getDefaultRectangle());
    }

    /**
     * True while a protection switch is unconfirmed or an attack is about to land. Nothing that can hold
     * the input lock, stall the script thread or move the player may start in this window.
     */
    private boolean prayerCritical()
    {
        return protectionPending || attackImminent;
    }

    // ---------------------------------------------------------------------------------------------
    // Survival
    // ---------------------------------------------------------------------------------------------

    private DelvePrayerHelperState handleSurvival(BossSnapshot boss, int currentTick, WorldPoint playerLocation)
    {
        // Walking blocks the script thread while the path is built, so it must never start in front of
        // a protection switch.
        if (prayerCritical())
        {
            return null;
        }

        // Rock Throw: the rock lands on the tile the player stood on when it explodes. Only move during
        // the last couple of ticks, otherwise the player can walk back onto the marked tile.
        //
        // The boss also plays the rock-throw animation (12407) to hurl the earthen shield at the start
        // of the slam, so the fuse is ignored while the slam is active. Dodging then would pull the
        // player off the shield right before the slam lands.
        if (config.dodgeRockThrow()
                && !hazardTracker.isShockwaveActive()
                && hazardTracker.isRockFuseActive()
                && hazardTracker.getTicksUntilRockImpact() <= config.rockDodgeTicks())
        {
            WorldPoint bossTile = boss == null ? null : boss.location;
            WorldPoint safe = movement.findDodgeTile(playerLocation, bossTile);
            if (safe != null && movement.walkTo(playerLocation, safe))
            {
                fightLogger.logDebug("MOVE", "rock dodge to " + fmt(safe) + " (impact in "
                        + hazardTracker.getTicksUntilRockImpact() + "t)");
            }
            return DelvePrayerHelperState.DODGING_ROCK;
        }

        // Volatile earth / earthen shield slam. Handled even when the boss is briefly missing from
        // the NPC cache, and it outranks acid: standing in acid is survivable, standing outside the
        // shield during the slam is not. Either option alone is enough to run this phase, so turning
        // off shield-following does not also silently disable totem destruction.
        if (hazardTracker.isShockwaveActive()
                && (config.followEarthenShield() || config.autoSelectTotems()))
        {
            return handleShockwave(playerLocation, currentTick);
        }

        if (boss == null)
        {
            return null;
        }

        if (config.avoidAcid() && hazardTracker.isAcidTile(playerLocation))
        {
            WorldPoint safe = movement.findSafeTile(playerLocation, boss.location, config.preferredDistance());
            if (safe != null && movement.walkTo(playerLocation, safe))
            {
                fightLogger.logDebug("MOVE", "acid escape to " + fmt(safe));
            }
            return DelvePrayerHelperState.AVOIDING_ACID;
        }

        return null;
    }

    /**
     * The volatile earth / earthen shield slam.
     *
     * <p>While the shield NPC exists it is the only safe spot for the slam, and it moves one tile
     * per tick, so the player follows its current tile. Before it spawns, the two volatile earth
     * totems the player destroys decide its path: the first destroyed is the destination, the
     * second is where the shield spawns. Waiting on the destination makes the shield roll over the
     * player instead of away from them.</p>
     */
    private DelvePrayerHelperState handleShockwave(WorldPoint playerLocation, int currentTick)
    {
        // Re-checked here as well as in tick(): walking blocks the script thread and must never
        // start in front of a protection switch.
        if (prayerCritical())
        {
            return null;
        }

        WorldPoint shieldTile = hazardTracker.getEarthenShieldTile();
        if (shieldTile != null)
        {
            if (!config.followEarthenShield())
            {
                return null;
            }
            if (movement.walkTo(playerLocation, shieldTile))
            {
                fightLogger.logDebug("MOVE", "following earthen shield to " + fmt(shieldTile));
            }
            return DelvePrayerHelperState.TAKING_SHIELD;
        }

        if (hazardTracker.getDestroyedNodes().size() < 2)
        {
            if (config.autoSelectTotems())
            {
                attackVolatileEarth(playerLocation, currentTick);
            }
            return DelvePrayerHelperState.DESTROYING_EARTH;
        }

        if (!config.followEarthenShield())
        {
            return null;
        }

        WorldPoint destination = hazardTracker.getShieldDestination();
        if (destination != null && movement.walkTo(playerLocation, destination))
        {
            fightLogger.logDebug("MOVE", "waiting for the earthen shield at " + fmt(destination));
        }
        return DelvePrayerHelperState.TAKING_SHIELD;
    }

    /**
     * Destroys the volatile earth totems that give the shield the longest path: the two farthest
     * apart. The first one destroyed becomes the shield's destination, the second its spawn point.
     *
     * <p>The totem closest to the player is destroyed first, so the second one (the shield's spawn)
     * is the far one. The player has to travel there to destroy it anyway, so the shield appears next
     * to them and can be followed from the first tick instead of being chased across the arena.</p>
     */
    private void attackVolatileEarth(WorldPoint playerLocation, int currentTick)
    {
        // Belt and braces: the caller already stops at two destroyed earths / a live shield, but a
        // stale tracker must never make this click a third totem while the shield is rolling.
        if (hazardTracker.getEarthenShieldTile() != null || hazardTracker.getDestroyedNodes().size() >= 2)
        {
            return;
        }

        List<Rs2NpcModel> nodes = Microbot.getRs2NpcCache().query()
                .withId(NpcID.DOM_SHOCKWAVE_PATH_NODE)
                .where(npc -> !npc.isDead())
                .toList();

        Set<WorldPoint> liveTiles = new HashSet<>();
        for (Rs2NpcModel node : nodes)
        {
            WorldPoint location = DelveMovement.fromLocal(Microbot.getClient(), node.getLocalLocation());
            if (location != null)
            {
                liveTiles.add(location);
            }
        }

        // Count only totems this script actually clicked that are now gone. The shield can roll over
        // the remaining earths on its own, so "totems that disappeared" is not a reliable kill count.
        int destroyedByPlayer = 0;
        for (WorldPoint tile : attackedTotems)
        {
            if (!liveTiles.contains(tile))
            {
                destroyedByPlayer++;
            }
        }
        if (destroyedByPlayer >= 2)
        {
            return;
        }

        if (currentTick - lastTotemClickTick < CLICK_RETRY_TICKS || nodes.isEmpty())
        {
            return;
        }

        Rs2NpcModel target = chooseVolatileEarth(nodes, playerLocation);
        if (target == null)
        {
            return;
        }

        WorldPoint targetTile = DelveMovement.fromLocal(Microbot.getClient(), target.getLocalLocation());
        // Do not hammer the same totem every two ticks while the previous attack is still running.
        // A different target is always allowed immediately (the pair has to go down fast).
        boolean sameTarget = targetTile != null && targetTile.equals(lastTotemTargetTile);
        if (sameTarget && currentTick - lastTotemClickTick < 4)
        {
            return;
        }

        lastTotemClickTick = currentTick;
        lastTotemTargetTile = targetTile;
        if (targetTile != null)
        {
            attackedTotems.add(targetTile);
        }
        // Tell the tracker which totem this click is for, so its death can be told apart from a
        // totem the rolling shield destroys later in the phase.
        hazardTracker.markTargetedNode(targetTile);
        boolean clicked = target.click("Attack");
        fightLogger.logDebug("TOTEM", "attacking volatile earth at " + fmt(targetTile)
                + " -> " + (clicked ? "clicked" : "failed"));
    }

    /**
     * Picks the totem that maximises the shield's path. With nothing destroyed yet this is the
     * endpoint of the farthest-apart pair that is closest to the player; with one totem down it is
     * the remaining totem farthest from it.
     */
    private Rs2NpcModel chooseVolatileEarth(List<Rs2NpcModel> nodes, WorldPoint playerLocation)
    {
        List<Rs2NpcModel> models = new ArrayList<>();
        List<WorldPoint> locations = new ArrayList<>();
        for (Rs2NpcModel node : nodes)
        {
            WorldPoint location = DelveMovement.fromLocal(Microbot.getClient(), node.getLocalLocation());
            if (location != null)
            {
                models.add(node);
                locations.add(location);
            }
        }
        if (models.isEmpty())
        {
            return null;
        }

        List<WorldPoint> destroyed = hazardTracker.getDestroyedNodes();
        if (destroyed.isEmpty())
        {
            Rs2NpcModel best = null;
            int bestPairDistance = -1;
            int bestPlayerDistance = Integer.MAX_VALUE;
            for (int i = 0; i < locations.size(); i++)
            {
                for (int j = i + 1; j < locations.size(); j++)
                {
                    int pairDistance = locations.get(i).distanceTo(locations.get(j));
                    for (int endpoint = 0; endpoint < 2; endpoint++)
                    {
                        int playerDistance = playerLocation == null
                                ? 0
                                : locations.get(endpoint == 0 ? i : j).distanceTo(playerLocation);
                        // Prefer a longer pair; for the same pair prefer the endpoint nearer to the
                        // player, so the shield spawns where the player ends up after the second kill.
                        if (pairDistance > bestPairDistance
                                || (pairDistance == bestPairDistance && playerDistance < bestPlayerDistance))
                        {
                            bestPairDistance = pairDistance;
                            bestPlayerDistance = playerDistance;
                            best = models.get(endpoint == 0 ? i : j);
                        }
                    }
                }
            }
            return best;
        }

        WorldPoint destination = destroyed.get(0);
        Rs2NpcModel best = null;
        int bestDistance = -1;
        for (int i = 0; i < locations.size(); i++)
        {
            WorldPoint location = locations.get(i);
            if (location.equals(destination))
            {
                continue;
            }
            int distance = location.distanceTo(destination);
            if (distance > bestDistance)
            {
                bestDistance = distance;
                best = models.get(i);
            }
        }
        return best;
    }

    private DelvePrayerHelperState handleConsumables(ProjectileSnapshot next, int currentCycle, int currentTick)
    {
        if (currentTick == lastConsumableTick)
        {
            return null;
        }
        // Eating and drinking block the script thread for a moment, so never do it while an attack is
        // about to land or the rock is about to explode.
        if (next != null && next.endCycle - currentCycle < 2 * CYCLES_PER_TICK)
        {
            return null;
        }
        if (hazardTracker.isRockFuseActive() && hazardTracker.getTicksUntilRockImpact() <= 1)
        {
            return null;
        }
        lastConsumableTick = currentTick;

        if (eatFood(currentTick))
        {
            return DelvePrayerHelperState.EATING;
        }
        if (drinkPrayerPotion(currentTick, false))
        {
            return DelvePrayerHelperState.DRINKING_PRAYER;
        }
        return null;
    }

    private boolean eatFood(int currentTick)
    {
        // Master switch: when eating is disabled a food click can never race a prayer switch.
        if (!config.enableEating())
        {
            return false;
        }
        // Re-checked here as well as in tick(): eating holds the input lock for hundreds of milliseconds.
        if (prayerCritical())
        {
            return false;
        }
        if (config.eatAtPercent() <= 0 || currentTick == lastEatTick)
        {
            return false;
        }

        double health = healthPercentage(Microbot.getClient());
        if (health > config.eatAtPercent())
        {
            return false;
        }

        lastEatTick = currentTick;
        boolean ate = Rs2Player.eatAt(config.eatAtPercent());
        fightLogger.logDebug("CONSUMABLE", String.format(Locale.ROOT, "eat at %.1f%% hp -> %s",
                health, ate ? "ate" : "no food / failed"));
        return ate;
    }

    private boolean drinkPrayerPotion(int currentTick, boolean force)
    {
        // Master switch, checked even for the emergency drink: disabling restoration must guarantee
        // that a potion is never drunk while a prayer switch is in flight.
        if (!config.enablePrayerRestoration())
        {
            return false;
        }
        // The emergency drink (force) is the only exception: without prayer points no protection prayer
        // can be activated at all, so it has to be allowed even mid-switch.
        if (!force && prayerCritical())
        {
            return false;
        }
        if (currentTick == lastPrayerPotTick)
        {
            return false;
        }

        // force means the protection prayer could not be activated because prayer points ran out.
        // That emergency drink happens even when the automatic threshold is disabled (0).
        int points = Rs2Prayer.getPrayerPoints();
        if (!force && (config.prayerPotAtPoints() <= 0 || points > config.prayerPotAtPoints()))
        {
            return false;
        }

        lastPrayerPotTick = currentTick;
        boolean drank = force
                ? Rs2Player.drinkPrayerPotion()
                : Rs2Player.drinkPrayerPotionAt(config.prayerPotAtPoints());
        fightLogger.logDebug("CONSUMABLE", (force ? "emergency " : "") + "prayer pot at " + points
                + " points -> " + (drank ? "drunk" : "no potion / failed"));
        return drank;
    }

    /** Health as a percentage, read straight off the client with no client-thread round trip. */
    private static double healthPercentage(Client client)
    {
        if (client == null)
        {
            return 0.0;
        }
        int real = client.getRealSkillLevel(Skill.HITPOINTS);
        return real <= 0 ? 0.0 : client.getBoostedSkillLevel(Skill.HITPOINTS) * 100.0 / real;
    }

    /** Prayer as a percentage, read straight off the client with no client-thread round trip. */
    private static int prayerPercentage(Client client)
    {
        if (client == null)
        {
            return 0;
        }
        int real = client.getRealSkillLevel(Skill.PRAYER);
        return real <= 0 ? 0 : (int) (client.getBoostedSkillLevel(Skill.PRAYER) * 100.0 / real);
    }

    private void logHazardState(WorldPoint playerLocation)
    {
        int rockTicks = hazardTracker.getTicksUntilRockImpact();
        if (hazardTracker.isRockFuseActive())
        {
            if (!rockFuseLogged)
            {
                rockFuseLogged = true;
                fightLogger.logDebug("HAZARD", "rock throw detected, impact in " + rockTicks + "t"
                        + (hazardTracker.isShockwaveActive() ? " (slam: dodge suppressed)" : ""));
            }
        }
        else if (rockFuseLogged)
        {
            rockFuseLogged = false;
            fightLogger.logDebug("HAZARD", "rock fuse expired");
        }

        int acidCount = hazardTracker.getAcidTileCount();
        if (acidCount != lastAcidCount)
        {
            lastAcidCount = acidCount;
            fightLogger.logDebug("HAZARD", "acid tiles now " + acidCount);
        }

        boolean onAcid = hazardTracker.isAcidTile(playerLocation);
        if (onAcid != onAcidLogged)
        {
            onAcidLogged = onAcid;
            fightLogger.logDebug("HAZARD", "player " + (onAcid ? "is standing on acid" : "left acid"));
        }
    }

    /** Logs the volatile earth / earthen shield state. Runs on the client thread. */
    private void logShockwaveState()
    {
        boolean active = hazardTracker.isShockwaveActive();
        if (active && !shockwaveLogged)
        {
            shockwaveLogged = true;
            fightLogger.logDebug("TOTEM", "volatile earth phase started, totems="
                    + hazardTracker.getNodeTiles().size() + " at " + fmtTiles(hazardTracker.getNodeTiles()));
        }
        else if (!active && shockwaveLogged)
        {
            shockwaveLogged = false;
            fightLogger.logDebug("TOTEM", "volatile earth phase ended");
        }

        int destroyed = hazardTracker.getDestroyedNodes().size();
        if (destroyed > lastDestroyedNodeCount)
        {
            lastDestroyedNodeCount = destroyed;
            WorldPoint last = hazardTracker.getDestroyedNodes().get(destroyed - 1);
            fightLogger.logDebug("TOTEM", "volatile earth " + destroyed + " destroyed at " + fmt(last)
                    + (destroyed == 1 ? " (shield destination)" : destroyed == 2 ? " (shield spawn)" : ""));
        }
        else if (destroyed < lastDestroyedNodeCount)
        {
            // A new slam started and cleared the pair.
            lastDestroyedNodeCount = destroyed;
        }

        WorldPoint shield = hazardTracker.getEarthenShieldTile();
        if (shield != null && !shield.equals(lastLoggedShieldTile))
        {
            lastLoggedShieldTile = shield;
            fightLogger.logDebug("TOTEM", "earthen shield at " + fmt(shield));
        }
        else if (shield == null)
        {
            lastLoggedShieldTile = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Offensive prayer
    // ---------------------------------------------------------------------------------------------

    /**
     * The offensive prayer from the config dropdown. If the chosen prayer cannot be used (not unlocked
     * or level too low) the best available ranged prayer is used instead.
     */
    private Rs2PrayerEnum resolveOffensivePrayer()
    {
        Rs2PrayerEnum chosen = null;
        switch (config.offensivePrayerType())
        {
            case RIGOUR:
                chosen = Rs2Prayer.isRigourUnlocked() ? Rs2PrayerEnum.RIGOUR : null;
                break;
            case DEADEYE:
                chosen = Rs2Prayer.isDeadeyeUnlocked() ? Rs2PrayerEnum.DEAD_EYE : null;
                break;
            case EAGLE_EYE:
                chosen = hasPrayerLevel(Rs2PrayerEnum.EAGLE_EYE) ? Rs2PrayerEnum.EAGLE_EYE : null;
                break;
            case HAWK_EYE:
                chosen = hasPrayerLevel(Rs2PrayerEnum.HAWK_EYE) ? Rs2PrayerEnum.HAWK_EYE : null;
                break;
            case SHARP_EYE:
                chosen = hasPrayerLevel(Rs2PrayerEnum.SHARP_EYE) ? Rs2PrayerEnum.SHARP_EYE : null;
                break;
            default:
                break;
        }
        return chosen != null ? chosen : Rs2Prayer.getBestRangePrayer();
    }

    private static boolean hasPrayerLevel(Rs2PrayerEnum prayer)
    {
        return Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= prayer.getLevel();
    }

    /**
     * Eagle Eye and Deadeye share one prayer slot (same click), so either one being on counts as "on".
     * Otherwise picking Eagle Eye with Deadeye unlocked would be clicked on and off forever.
     */
    private static boolean isOffensiveActive(Rs2PrayerEnum prayer)
    {
        if (prayer == Rs2PrayerEnum.EAGLE_EYE || prayer == Rs2PrayerEnum.DEAD_EYE)
        {
            return Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE) || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.DEAD_EYE);
        }
        return Rs2Prayer.isPrayerActive(prayer);
    }

    private static boolean isAnyOffensivePrayerActive()
    {
        return Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.DEAD_EYE)
                || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR);
    }

    private static Rs2PrayerEnum getActiveOffensivePrayer()
    {
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR))
        {
            return Rs2PrayerEnum.RIGOUR;
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.DEAD_EYE))
        {
            return Rs2PrayerEnum.DEAD_EYE;
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.EAGLE_EYE))
        {
            return Rs2PrayerEnum.EAGLE_EYE;
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.HAWK_EYE))
        {
            return Rs2PrayerEnum.HAWK_EYE;
        }
        if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.SHARP_EYE))
        {
            return Rs2PrayerEnum.SHARP_EYE;
        }
        return null;
    }

    private void updateOffensivePrayer(BossSnapshot boss, int currentTick)
    {
        if (!config.offensivePrayer())
        {
            return;
        }
        // Re-checked here as well as in tick(): the flags can flip while this tick is running.
        if (prayerCritical())
        {
            logOffensiveSkip(currentTick, "protection switch in flight");
            return;
        }

        Rs2PrayerEnum offensivePrayer = resolveOffensivePrayer();
        if (offensivePrayer == null)
        {
            return;
        }

        if (Rs2Prayer.isOutOfPrayer())
        {
            logOffensiveSkip(currentTick, "out of prayer points");
            return;
        }

        boolean shielded = boss != null && boss.id == NpcID.DOM_BOSS_SHIELDED;
        boolean shouldBeActive = !config.noOffensivePrayerInShieldPhase() || !shielded;

        if (shouldBeActive)
        {
            if (isOffensiveActive(offensivePrayer))
            {
                return;
            }
            if (currentTick - lastOffensiveClickTick < CLICK_RETRY_TICKS)
            {
                return;
            }
            fightLogger.logDebug("OFFENSIVE", "clicking " + offensivePrayer);
            clickPrayer(offensivePrayer, true);
            lastOffensiveClickTick = currentTick;
            return;
        }

        // Should be inactive (e.g. shield phase) - turn off whichever offensive prayer is active
        if (!isAnyOffensivePrayerActive())
        {
            return;
        }
        if (currentTick - lastOffensiveClickTick < CLICK_RETRY_TICKS)
        {
            return;
        }
        Rs2PrayerEnum active = getActiveOffensivePrayer();
        if (active != null)
        {
            fightLogger.logDebug("OFFENSIVE", "shield phase, turning off " + active);
            clickPrayer(active, false);
            lastOffensiveClickTick = currentTick;
        }
    }

    private void logOffensiveSkip(int currentTick, String reason)
    {
        if (currentTick == lastOffensiveSkipLogTick)
        {
            return;
        }
        lastOffensiveSkipLogTick = currentTick;
        fightLogger.logDebug("OFFENSIVE", "skip: " + reason);
    }

    // ---------------------------------------------------------------------------------------------
    // Recasting thralls / Death Charge
    // ---------------------------------------------------------------------------------------------

    /**
     * Recasts the selected thrall and Death Charge when they are available. Checked once per game tick.
     * Never runs while an attack is about to land or a protection switch is unconfirmed: the quick cast
     * is a single click like a prayer click, but it still should not sit in front of a prayer switch.
     *
     * @param next the soonest incoming attack, or null when none is in flight
     */
    private void updateRecasts(ProjectileSnapshot next, int currentCycle, int currentTick, long now)
    {
        if (!config.recastThrall() && !config.recastDeathCharge())
        {
            return;
        }
        if (prayerCritical())
        {
            logRecastSkip(currentTick, "protection switch in flight");
            return;
        }
        if (currentTick == lastRecastCheckTick)
        {
            return;
        }
        lastRecastCheckTick = currentTick;

        boolean attackSoon = next != null && next.endCycle - currentCycle < 2 * CYCLES_PER_TICK;
        if (attackSoon)
        {
            logRecastSkip(currentTick, "attack in " + (next.endCycle - currentCycle) + " cycles");
            return;
        }
        // The slow fallback cast blocks this loop, so it needs a real gap between attacks.
        boolean quiet = next == null && now - lastProjectileTime > FALLBACK_CAST_QUIET_MS;

        if (config.recastThrall())
        {
            recastThrall(currentTick, quiet);
        }
        if (config.recastDeathCharge())
        {
            recastDeathCharge(currentTick, quiet);
        }
    }

    private void recastThrall(int currentTick, boolean quiet)
    {
        if (Rs2Thrall.isActive())
        {
            thrallCastAttempts = 0; // active or on cooldown: the last cast worked, or nothing to do
            return;
        }
        if (currentTick - lastThrallCastTick < SPELL_RETRY_TICKS)
        {
            return;
        }
        Rs2Thrall thrall = config.thrallType().getThrall();
        if (!Rs2Thrall.canCast(thrall))
        {
            logRecastSkip(currentTick, "cannot cast " + thrall.getName()
                    + " (needs Arceuus spellbook, Book of the Dead and runes)");
            return;
        }
        if (castSpell(thrall, thrallCastAttempts, quiet, currentTick))
        {
            thrallCastAttempts++;
            lastThrallCastTick = currentTick;
        }
    }

    private void recastDeathCharge(int currentTick, boolean quiet)
    {
        if (isDeathChargeActiveOrCoolingDown())
        {
            deathChargeCastAttempts = 0;
            return;
        }
        if (currentTick - lastDeathChargeCastTick < SPELL_RETRY_TICKS)
        {
            return;
        }
        if (!Rs2Spells.DEATH_CHARGE.hasRequirements() || !Rs2Magic.hasRequiredRunes(Rs2Spells.DEATH_CHARGE))
        {
            logRecastSkip(currentTick, "cannot cast Death Charge (needs Arceuus spellbook, level and runes)");
            return;
        }
        if (castSpell(Rs2Spells.DEATH_CHARGE, deathChargeCastAttempts, quiet, currentTick))
        {
            deathChargeCastAttempts++;
            lastDeathChargeCastTick = currentTick;
        }
    }

    private static boolean isDeathChargeActiveOrCoolingDown()
    {
        return Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE) != 0
                || Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_COOLDOWN) != 0;
    }

    private void logRecastSkip(int currentTick, String reason)
    {
        if (currentTick == lastRecastSkipLogTick)
        {
            return;
        }
        lastRecastSkipLogTick = currentTick;
        fightLogger.logDebug("RECAST", "skip: " + reason);
    }

    /**
     * Casts a self-cast spell. The first attempts are a single menu click with no tab switch (the same
     * way prayers are clicked), so they cannot stall the loop. If that did not take effect after
     * {@link #QUICK_CAST_ATTEMPTS} tries, fall back to Microbot's normal cast (opens the magic tab, slower)
     * but only in a quiet moment.
     *
     * @return true if a cast was sent
     */
    private boolean castSpell(Spell spell, int priorAttempts, boolean quiet, int currentTick)
    {
        if (priorAttempts < QUICK_CAST_ATTEMPTS && quickCast(spell))
        {
            return true;
        }
        if (!quiet)
        {
            // The quick click did not take effect yet and the slow fallback would stall the loop.
            // Wait for the gap after an attack instead of casting in front of a prayer switch.
            logRecastSkip(currentTick, "waiting for a quiet moment to cast "
                    + spell.getMagicAction().getName() + " with the magic tab");
            return false;
        }
        fightLogger.logDebug("RECAST", "casting " + spell.getMagicAction().getName() + " with the magic tab (fallback)");
        return Rs2Magic.cast(spell);
    }

    private boolean quickCast(Spell spell)
    {
        MagicAction action = spell.getMagicAction();
        int widgetId = action.getWidgetId();
        boolean selfCast = action.getActions() != null
                && Arrays.stream(action.getActions()).anyMatch(a -> a != null && a.equalsIgnoreCase("cast"));
        if (widgetId == -1 || !selfCast)
        {
            return false;
        }
        fightLogger.logDebug("RECAST", "quick casting " + action.getName());
        NewMenuEntry menuEntry = new NewMenuEntry()
                .option("Cast")
                .param0(-1)
                .param1(widgetId)
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .target(action.getName());
        // Use quick invoke like prayer clicks (no unnecessary mouse movement/pathing).
        Microbot.doInvokeQuick(menuEntry, Rs2UiHelper.getDefaultRectangle());
        return true;
    }

    // ---------------------------------------------------------------------------------------------

    private static Rs2PrayerEnum protectionFor(int projectileId)
    {
        return DelvePrayerHelperProjectile.protectionFor(projectileId);
    }

    private static DelvePrayerHelperState stateFor(Rs2PrayerEnum protection)
    {
        switch (protection)
        {
            case PROTECT_MELEE:
                return DelvePrayerHelperState.PRAY_MELEE;
            case PROTECT_MAGIC:
                return DelvePrayerHelperState.PRAY_MAGE;
            case PROTECT_RANGE:
                return DelvePrayerHelperState.PRAY_RANGE;
            default:
                return DelvePrayerHelperState.IDLE;
        }
    }

    private void logTick(Client client, int currentTick, int currentCycle, BossSnapshot boss,
                         ProjectileSnapshot next, Rs2PrayerEnum wanted, boolean protectionPending,
                         WorldPoint player)
    {
        fightLogger.logTick(String.format(Locale.ROOT,
                "tick=%d cycle=%d hp=%.1f%% prayer=%dpts/%d%% prot=%s want=%s pending=%s off=%s"
                        + " player=%s boss=%s anim=%d next=%s in=%.1ft projectiles=%d rock=%dt acid=%s"
                        + " shield=%s totems=%d state=%s",
                currentTick, currentCycle,
                healthPercentage(client),
                Rs2Prayer.getPrayerPoints(), prayerPercentage(client),
                Rs2Prayer.getActiveProtectionPrayer(), wanted, protectionPending,
                getActiveOffensivePrayer(),
                fmt(player),
                boss == null ? "-" : fmt(boss.location),
                boss == null ? -1 : boss.animation,
                next == null ? "-" : next.protection,
                next == null ? 0.0 : (next.endCycle - currentCycle) / (double) CYCLES_PER_TICK,
                incomingProjectiles.size(),
                hazardTracker.getTicksUntilRockImpact(),
                hazardTracker.isAcidTile(player),
                fmt(hazardTracker.getEarthenShieldTile()),
                hazardTracker.getNodeTiles().size(),
                state));
    }

    private void resetTracking()
    {
        incomingProjectiles.clear();
        bossSnapshot = null;
        nextProjectile = null;
        wantedProtection = null;
        protectionPending = false;
        attackImminent = false;
        emergencyPrayerDrink = false;
        protectionDropped = false;
        lastProjectileTime = 0;
        bossLastSeen = 0;
        fightStartTime = 0;
        inFight = false;
        lastClickedPrayer = null;
        lastClickTick = -100;
        lastDropClickTick = -100;
        lastOffensiveClickTick = -100;
        awaitingConfirmation = null;
        managedProtection = null;
        lastConfirmedProtection = null;
        lastConfirmedTick = -100;
        lastRecastCheckTick = -100;
        lastThrallCastTick = -100;
        lastDeathChargeCastTick = -100;
        thrallCastAttempts = 0;
        deathChargeCastAttempts = 0;
        lastConsumableTick = -100;
        lastPrayerPotTick = -100;
        lastEatTick = -100;
        lastGateLogTick = -100;
        lastOutOfPrayerLogTick = -100;
        lastOffensiveSkipLogTick = -100;
        lastRecastSkipLogTick = -100;
        lastLoopFailureTick = -100;
        lastTickLogTick = -100;
        rockFuseLogged = false;
        onAcidLogged = false;
        lastAcidCount = 0;
        lastTotemClickTick = -100;
        lastTotemTargetTile = null;
        attackedTotems.clear();
        shockwaveLogged = false;
        lastDestroyedNodeCount = 0;
        lastLoggedShieldTile = null;
        hazardTracker.reset();
        movement.reset();
    }

    private void disableManagedPrayers(Client client)
    {
        Rs2PrayerEnum protection = Rs2Prayer.getActiveProtectionPrayer();
        if (protection != null && protection == managedProtection)
        {
            fightLogger.logDebug("PRAYER", "fight over, turning off " + protection);
            if (!clickPrayerDirect(client, protection, false))
            {
                clickPrayer(protection, false);
            }
        }
        Rs2PrayerEnum offensive = getActiveOffensivePrayer();
        if (offensive != null)
        {
            fightLogger.logDebug("PRAYER", "fight over, turning off " + offensive);
            clickPrayer(offensive, false);
        }
        managedProtection = null;
    }

    private void endFight()
    {
        resetTracking();
        setState(DelvePrayerHelperState.IDLE);
    }

    private void setState(DelvePrayerHelperState newState)
    {
        state = newState;
        Microbot.status = newState.toString();
    }

    public boolean isOffensivePrayerOn()
    {
        Rs2PrayerEnum offensivePrayer = resolveOffensivePrayer();
        return offensivePrayer != null && isOffensiveActive(offensivePrayer);
    }

    public boolean isRockFuseActive()
    {
        return hazardTracker.isRockFuseActive();
    }

    public int getTicksUntilRockImpact()
    {
        return hazardTracker.getTicksUntilRockImpact();
    }

    public boolean isOnAcid()
    {
        return hazardTracker.isAcidTile(DelveMovement.getRawPlayerLocation());
    }

    public WorldPoint getEarthenShieldTile()
    {
        return hazardTracker.getEarthenShieldTile();
    }

    public int getVolatileEarthCount()
    {
        return hazardTracker.getNodeTiles().size();
    }

    public int getPrayerPoints()
    {
        return Rs2Prayer.getPrayerPoints();
    }

    public String getFightLogName()
    {
        return fightLogger.getLogFileName();
    }

    @Override
    public void shutdown()
    {
        active = false;
        fightLogger.log("SESSION", "script shutdown");
        if (inFight)
        {
            disableManagedPrayers(Microbot.getClient());
        }
        resetTracking();
        setState(DelvePrayerHelperState.IDLE);
        super.shutdown();
    }

    private static String fmt(WorldPoint point)
    {
        return point == null ? "-" : "(" + point.getX() + "," + point.getY() + "," + point.getPlane() + ")";
    }

    /** Compact list of tiles for one log line, e.g. {@code [(1,2,0), (3,4,0)]}. */
    private static String fmtTiles(List<WorldPoint> points)
    {
        if (points == null || points.isEmpty())
        {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++)
        {
            if (i > 0)
            {
                builder.append(", ");
            }
            builder.append(fmt(points.get(i)));
        }
        return builder.append(']').toString();
    }
}
