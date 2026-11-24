package net.runelite.client.plugins.microbot.delveprayerhelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes a detailed, human-readable log of a Delve fight to a file so a bad run can be
 * captured and shared for review.
 *
 * <p>Events are handed in from the client thread (RuneLite events) and from the script thread
 * (prayer decisions). Formatting is cheap and all disk I/O happens on a dedicated writer thread,
 * so logging never stalls the prayer loop.</p>
 *
 * <p>Log files live in {@code ~/.runelite/logs/delve-prayer-helper/fight-<timestamp>.log}.</p>
 */
@Slf4j
@Singleton
public class DelveFightLogger
{
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private static final String LOG_DIRECTORY = "delve-prayer-helper";
	private static final int CYCLES_PER_TICK = 30;
	private static final int MAX_SEEN_PROJECTILES = 5000;
	private static final int MAX_QUEUED_LINES = 20000;
	private static final int LOG_RADIUS_TILES = 25;
	private static final String POISON = "\u0000";

	private final DelvePrayerHelperConfig config;

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(MAX_QUEUED_LINES);
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicLong lineCount = new AtomicLong();
	private final Set<String> seenProjectiles = ConcurrentHashMap.newKeySet();

	private volatile Thread writerThread;
	private volatile Path logFile;

	@Inject
	public DelveFightLogger(DelvePrayerHelperConfig config)
	{
		this.config = config;
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle
	// ---------------------------------------------------------------------------------------------

	public synchronized void start()
	{
		if (!config.fightLog() || running.get())
		{
			return;
		}

		try
		{
			Path directory = Paths.get(System.getProperty("user.home"), ".runelite", "logs", LOG_DIRECTORY);
			Files.createDirectories(directory);

			Path file = directory.resolve("fight-" + LocalDateTime.now().format(FILE_STAMP) + ".log");
			logFile = file;

			// A plugin re-enable reuses this singleton, so make sure nothing from the previous
			// session leaks into the new file.
			queue.clear();
			seenProjectiles.clear();
			lineCount.set(0);

			running.set(true);

			// Write the session header synchronously, before the writer thread exists. An empty
			// fight-*.log left behind by a crashed client is useless to share; this guarantees the
			// file always identifies the session and the exact settings it ran with.
			List<String> header = new ArrayList<>();
			header.add(prefix("SESSION") + "Delve Prayer Helper fight log started (v"
				+ DelvePrayerHelperPlugin.version + ")");
			header.add(prefix("SESSION") + "file=" + file);
			header.add(prefix("SESSION") + "settings: fightLog=" + config.fightLog()
				+ " verboseTicks=" + config.verboseTickLog()
				+ " dodgeRock=" + config.dodgeRockThrow() + "@" + config.rockDodgeTicks() + "t"
				+ " avoidAcid=" + config.avoidAcid()
				+ " prayerRestore=" + config.enablePrayerRestoration()
				+ " prayerPotAt=" + config.prayerPotAtPoints()
				+ " eating=" + config.enableEating()
				+ " eatAt=" + config.eatAtPercent() + "%"
				+ " landingMargin=" + config.landingMarginCycles() + "c"
				+ " followShield=" + config.followEarthenShield()
				+ " autoTotems=" + config.autoSelectTotems()
				+ " offensivePrayer=" + config.offensivePrayer() + "/" + config.offensivePrayerType()
				+ " recastThrall=" + config.recastThrall() + "/" + config.thrallType()
				+ " recastDeathCharge=" + config.recastDeathCharge());
			Files.write(file, header, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			lineCount.addAndGet(header.size());

			writerThread = new Thread(() -> writeLoop(file), "DelveFightLogWriter");
			writerThread.setDaemon(true);
			writerThread.start();
		}
		catch (Exception ex)
		{
			running.set(false);
			logFile = null;
			log.warn("Could not start the Delve fight log", ex);
		}
	}

	public synchronized void stop()
	{
		if (!running.get())
		{
			return;
		}

		log("SESSION", "fight log stopping, lines=" + lineCount.get());
		running.set(false);
		queue.offer(POISON);

		Thread thread = writerThread;
		if (thread != null)
		{
			try
			{
				thread.join(1500);
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
			}
		}

		writerThread = null;
	}

	public boolean isEnabled()
	{
		return running.get();
	}

	public String getLogFileName()
	{
		Path file = logFile;
		if (file != null)
		{
			return file.getFileName().toString();
		}
		return config.fightLog() ? "starting..." : "off";
	}

	public String getLogFilePath()
	{
		Path file = logFile;
		return file == null ? "" : file.toString();
	}

	// ---------------------------------------------------------------------------------------------
	// Logging
	// ---------------------------------------------------------------------------------------------

	/** Writes a line to the fight log. Does nothing when logging is disabled. */
	public void log(String category, String message)
	{
		if (!ensureRunning())
		{
			return;
		}

		String line = prefix(category) + message;
		if (!queue.offer(line))
		{
			// The writer thread is behind. Dropping a line is better than blocking the prayer loop.
			return;
		}
	}

	/** Writes to the fight log and, when debug logging is enabled, to the client log as well. */
	public void logDebug(String category, String message)
	{
		log(category, message);
		if (config.debugLogging())
		{
			log.info("Delve [{}] {}", category, message);
		}
	}

	public void logFightStart(String bossName, int bossId)
	{
		log("FIGHT", "=== fight start: " + bossName + " (id " + bossId + ") ===");
	}

	public void logFightEnd(long durationMs)
	{
		log("FIGHT", "=== fight end after " + (durationMs / 1000) + "s ===");
	}

	public void logTick(String message)
	{
		log("TICK", message);
	}

	/**
	 * Logs a projectile once. {@code ProjectileMoved} fires every cycle for the same projectile, so
	 * the identity of the attack is deduplicated by its id and cycle window.
	 *
	 * @param projectile the projectile that moved
	 * @param protection the prayer it maps to, or null when the id is not mapped
	 */
	public void logProjectile(Projectile projectile, Rs2PrayerEnum protection)
	{
		if (projectile == null || !ensureRunning())
		{
			return;
		}

		String key = projectileKey(projectile);
		if (!seenProjectiles.add(key))
		{
			return;
		}
		if (seenProjectiles.size() > MAX_SEEN_PROJECTILES)
		{
			seenProjectiles.clear();
			seenProjectiles.add(key);
		}

		Client client = Microbot.getClient();
		int currentCycle = client == null ? 0 : client.getGameCycle();
		int cyclesLeft = projectile.getEndCycle() - currentCycle;
		String style = protection == null ? "UNKNOWN" : protection.name();

		log("PROJECTILE", String.format(Locale.ROOT,
			"id=%d style=%s in=%.1ft cycles=%d start=%d end=%d src=%s dst=%s height=%d->%d",
			projectile.getId(), style, cyclesLeft / (double) CYCLES_PER_TICK, cyclesLeft,
			projectile.getStartCycle(), projectile.getEndCycle(),
			fmt(projectile.getSourcePoint()), fmt(projectile.getTargetPoint()),
			projectile.getStartHeight(), projectile.getEndHeight()));
	}

	public void logNpc(NPC npc, boolean spawned)
	{
		if (npc == null || !ensureRunning())
		{
			return;
		}

		WorldPoint location = actorLocation(npc);
		if (!nearby(location))
		{
			return;
		}

		log("NPC", (spawned ? "spawn" : "despawn")
			+ " id=" + npc.getId()
			+ " name=" + npc.getName()
			+ " at=" + fmt(location));
	}

	public void logAnimation(Actor actor)
	{
		if (actor == null || !ensureRunning())
		{
			return;
		}

		Client client = Microbot.getClient();
		boolean isPlayer = client != null && actor == client.getLocalPlayer();
		WorldPoint location = actorLocation(actor);
		if (!isPlayer && !nearby(location))
		{
			return;
		}

		String name = isPlayer ? "player" : actor.getName();
		int id = actor instanceof NPC ? ((NPC) actor).getId() : -1;
		log("ANIMATION", name + " npcId=" + id + " anim=" + actor.getAnimation() + " at=" + fmt(location));
	}

	public void logHitsplat(HitsplatApplied event)
	{
		if (event == null || !ensureRunning())
		{
			return;
		}

		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		if (actor == null || hitsplat == null)
		{
			return;
		}

		Client client = Microbot.getClient();
		boolean isPlayer = client != null && actor == client.getLocalPlayer();
		WorldPoint location = actorLocation(actor);
		if (!isPlayer && !nearby(location))
		{
			return;
		}

		log("HITSPLAT", (isPlayer ? "player" : actor.getName())
			+ " amount=" + hitsplat.getAmount()
			+ " type=" + hitsplat.getHitsplatType()
			+ " prot=" + Rs2Prayer.getActiveProtectionPrayer()
			+ " hp=" + String.format(Locale.ROOT, "%.1f%%", Rs2Player.getHealthPercentage())
			+ " at=" + fmt(location));
	}

	public void logGraphicsObject(GraphicsObjectCreated event)
	{
		if (event == null || !ensureRunning())
		{
			return;
		}

		GraphicsObject graphics = event.getGraphicsObject();
		if (graphics == null)
		{
			return;
		}

		WorldPoint location = project(graphics.getLocation());
		if (!nearby(location))
		{
			return;
		}

		log("GRAPHICS", "id=" + graphics.getId()
			+ " at=" + fmt(location)
			+ " level=" + graphics.getLevel()
			+ " start=" + graphics.getStartCycle()
			+ " frame=" + graphics.getAnimationFrame());
	}

	public void logChat(ChatMessage event)
	{
		if (event == null || !ensureRunning())
		{
			return;
		}

		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
		{
			return;
		}

		log("CHAT", event.getMessage());
	}

	// ---------------------------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------------------------

	private boolean ensureRunning()
	{
		// Never stop the writer here: log() is called from the client thread and stop() joins the
		// writer thread. Toggling the config off simply stops producing lines until the plugin is
		// restarted or logging is re-enabled.
		if (!config.fightLog())
		{
			return false;
		}
		if (!running.get())
		{
			start();
		}
		return running.get();
	}

	private String prefix(String category)
	{
		Client client = Microbot.getClient();
		int tick = client == null ? -1 : client.getTickCount();
		int cycle = client == null ? -1 : client.getGameCycle();
		return "[" + LocalTime.now().format(LINE_STAMP) + "] [t" + tick + " c" + cycle + "] [" + category + "] ";
	}

	private void writeLoop(Path file)
	{
		try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE, StandardOpenOption.APPEND))
		{
			while (true)
			{
				String line = queue.poll(250, TimeUnit.MILLISECONDS);
				if (line == null)
				{
					if (!running.get())
					{
						break;
					}
					continue;
				}
				if (POISON.equals(line))
				{
					break;
				}
				writeLine(writer, line);
			}

			String remaining;
			while ((remaining = queue.poll()) != null)
			{
				if (!POISON.equals(remaining))
				{
					writeLine(writer, remaining);
				}
			}
			writer.flush();
		}
		catch (Exception ex)
		{
			log.warn("Delve fight log writer stopped", ex);
		}
	}

	private void writeLine(BufferedWriter writer, String line) throws Exception
	{
		writer.write(line);
		writer.newLine();
		// Flush every line: the writer is off the client thread, and a crash must never leave the
		// shared fight log truncated in the buffer.
		writer.flush();
		lineCount.incrementAndGet();
	}

	private boolean nearby(WorldPoint point)
	{
		if (point == null)
		{
			return true;
		}
		// Delve is instanced: NPC/actor/graphics points are instance world space (WorldPoint.fromLocal).
		// Compare against the cached player tile, not Rs2Player.getWorldLocation() (template), or
		// everything is ~7700 tiles away.
		WorldPoint player = DelveMovement.getRawPlayerLocation();
		return player == null || player.distanceTo(point) <= LOG_RADIUS_TILES;
	}

	/**
	 * Actor tile in instance world space. {@code actor.getWorldLocation()} returns template space
	 * inside an instance, which is thousands of tiles away from the instance-world player tile used by
	 * {@link #nearby(WorldPoint)} and would filter out every log line.
	 */
	private static WorldPoint actorLocation(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}
		try
		{
			return DelveMovement.fromLocal(Microbot.getClient(), actor.getLocalLocation());
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static WorldPoint project(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return null;
		}
		try
		{
			// Instance world space, matching NPC/actor locations inside the instance.
			return WorldPoint.fromLocal(Microbot.getClient(), localPoint);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static String projectileKey(Projectile projectile)
	{
		return projectile.getId() + "|" + projectile.getStartCycle() + "|" + projectile.getEndCycle()
			+ "|" + projectile.getSourcePoint() + "|" + projectile.getTargetPoint();
	}

	private static String fmt(WorldPoint point)
	{
		return point == null ? "-" : "(" + point.getX() + "," + point.getY() + "," + point.getPlane() + ")";
	}
}
