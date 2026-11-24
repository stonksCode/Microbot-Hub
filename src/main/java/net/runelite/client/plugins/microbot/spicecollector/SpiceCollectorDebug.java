package net.runelite.client.plugins.microbot.spicecollector;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Optional, opt-in trace for the Spice Collector.
 *
 * <p>When enabled in the configuration every interesting state change, dialogue check, click and
 * feed attempt is both written to the RuneLite log and to a timestamped file under
 * {@code ~/.runelite/spice-collector/}. The file makes it easy to attach a complete run to a bug
 * report.</p>
 */
@Slf4j
public final class SpiceCollectorDebug {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile boolean enabled = false;
    private static BufferedWriter writer;
    private static String filePath = "";

    private SpiceCollectorDebug() {
    }

    /**
     * Enables or disables the trace. Calling this with {@code true} while a trace is already
     * open is a no-op, so config refreshes do not produce multiple files.
     */
    public static synchronized void configure(boolean debugEnabled) {
        if (!debugEnabled) {
            close();
            return;
        }
        if (enabled && writer != null) {
            return;
        }

        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".runelite", "spice-collector");
            Files.createDirectories(dir);
            Path file = dir.resolve("debug-" + LocalDateTime.now().format(FILE_TIME) + ".log");
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            filePath = file.toString();
            enabled = true;
            trace("Debug trace opened: %s", filePath);
            log.info("[SpiceCollector] Debug trace: {}", filePath);
        } catch (IOException | RuntimeException ex) {
            enabled = false;
            writer = null;
            filePath = "";
            log.warn("[SpiceCollector] Could not open debug trace: {}", ex.getMessage());
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void trace(String message) {
        if (!enabled) {
            return;
        }
        String line = LocalDateTime.now().format(LINE_TIME) + " " + message;
        log.info("[SpiceCollector][TRACE] {}", line);
        write(line);
    }

    public static void trace(String format, Object... args) {
        if (!enabled) {
            return;
        }
        trace(String.format(format, args));
    }

    public static synchronized void close() {
        enabled = false;
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ex) {
                log.warn("[SpiceCollector] Could not close debug trace: {}", ex.getMessage());
            }
        }
        writer = null;
        filePath = "";
    }

    private static synchronized void write(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            log.warn("[SpiceCollector] Could not write debug trace: {}", ex.getMessage());
        }
    }
}
