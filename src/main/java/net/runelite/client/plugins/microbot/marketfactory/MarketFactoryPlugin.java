package net.runelite.client.plugins.microbot.marketfactory;

import com.google.inject.Provides;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.marketfactory.enums.RiskLevel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Market Factory",
	description = "Fully automatic GE market scanner that flips or processes items with profitability checks, risk controls and break handling.",
	tags = {"ge", "flipping", "processing", "factory", "money making"},
	authors = {"Microbot"},
	version = MarketFactoryPlugin.version,
	minClientVersion = "2.0.13",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class MarketFactoryPlugin extends Plugin
{
	static final String version = "0.2.0";

	@Inject
	private MarketFactoryConfig config;

	@Provides
	MarketFactoryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MarketFactoryConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MarketFactoryOverlay overlay;

	private MarketFactoryScript script;
	private String lastRescanButton;
	private String lastExportButton;

	@Override
	protected void startUp() throws AWTException
	{
		if (overlay != null && config.showOverlay())
		{
			overlayManager.add(overlay);
		}
		script = new MarketFactoryScript(this, config);
		script.start();
		lastRescanButton = config.rescanNow().getId();
		lastExportButton = config.exportCsv().getId();
		if (config.autoStart())
		{
			script.requestScan();
		}
		log.info("Market Factory started");
	}

	@Override
	protected void shutDown()
	{
		if (overlay != null)
		{
			overlayManager.remove(overlay);
		}
		if (script != null)
		{
			script.shutdown();
			script = null;
		}
		log.info("Market Factory stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup() == null || !event.getGroup().equals(MarketFactoryConfig.GROUP))
		{
			return;
		}
		log.info("ConfigChanged group={} key={}", event.getGroup(), event.getKey());

		if (script != null && "rescanNow".equals(event.getKey()))
		{
			String id = config.rescanNow().getId();
			if (id != null && !id.equals(lastRescanButton))
			{
				lastRescanButton = id;
				log.info("Rescan button pressed, calling forceRescan()");
				script.forceRescan();
			}
			return;
		}

		if (script != null && "exportCsv".equals(event.getKey()))
		{
			String id = config.exportCsv().getId();
			if (id != null && !id.equals(lastExportButton))
			{
				lastExportButton = id;
				exportScanCsv();
			}
			return;
		}

		if (overlayManager != null && overlay != null)
		{
			if (config.showOverlay())
			{
				overlayManager.add(overlay);
			}
			else
			{
				overlayManager.remove(overlay);
			}
		}

		if (script != null)
		{
			script.onConfigUpdated();
		}
	}

	public MarketFactoryConfig getConfig()
	{
		return config;
	}

	public MarketFactoryScript getScript()
	{
		return script;
	}

	/** Export the latest scan report to a CSV file inside the plugin resources directory. */
	public void exportScanCsv()
	{
		if (script == null)
		{
			return;
		}
		ScanReport report = script.getLastReport();
		if (report == null)
		{
			Microbot.log("Market Factory: no scan data to export yet");
			return;
		}

		File dir = new File(System.getProperty("user.home"), "Microbot" + File.separator + "MarketFactory");
		dir.mkdirs();
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File file = new File(dir, "market-scan-" + timestamp + ".csv");

		try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8))
		{
			writer.println("type,label,itemId,quantity,capital,revenue,profit,roiPercent,fillHours,gpPerHour,score,confidence,stability,maxHoldMinutes,notes");
			writeCandidates(writer, "FLIP", report.getFlips());
			writeCandidates(writer, "PROCESS", report.getProcesses());
			Microbot.log("Market Factory: exported " + file.getAbsolutePath());
		}
		catch (IOException ex)
		{
			log.error("Failed to export scan CSV", ex);
			Microbot.log("Market Factory: CSV export failed - " + ex.getMessage());
		}
	}

	private void writeCandidates(PrintWriter writer, String type, java.util.List<Candidate> candidates)
	{
		for (Candidate candidate : candidates)
		{
			String notes = candidate.getNotes().isEmpty() ? "" : String.join(" | ", candidate.getNotes()).replace('"', '\'');
			writer.printf(Locale.ROOT,
				"%s,\"%s\",%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%d,\"%s\"%n",
				type,
				escape(candidate.getLabel()),
				candidate.getItemId(),
				candidate.getQuantity(),
				candidate.getCapital(),
				candidate.getRevenue(),
				candidate.getProfit(),
				candidate.getRoiPercent(),
				candidate.getFillHours(),
				candidate.getGpPerHour(),
				candidate.getScore(),
				candidate.getConfidence(),
				candidate.getStability(),
				candidate.getMaxHoldMinutes(),
				notes);
		}
	}

	private static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("\"", "\"\"");
	}
}
