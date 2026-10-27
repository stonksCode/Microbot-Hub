package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class LizardmanShamanOverlay extends OverlayPanel
{
	private final LizardmanShamanPlugin plugin;

	@Inject
	LizardmanShamanOverlay(LizardmanShamanPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			final LizardmanShamanConfig config = plugin.getConfig();
			final LizardmanShamanScript script = plugin.getScript();
			if (config == null || script == null || !config.showOverlay())
			{
				return null;
			}

			panelComponent.setPreferredSize(new Dimension(220, 0));
			panelComponent.getChildren().add(TitleComponent.builder()
					.text("Lizardman Shaman v" + LizardmanShamanPlugin.version)
					.color(Color.GREEN)
					.build());
			panelComponent.getChildren().add(LineComponent.builder().build());
			panelComponent.getChildren().add(LineComponent.builder()
					.left("State:")
					.right(script.getState().name())
					.build());
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Area:")
					.right(script.isTemple() ? "Temple" : "Surface")
					.build());
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Shamans:")
					.right(Integer.toString(script.getShamanCount()))
					.build());
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Spawns:")
					.right(Integer.toString(script.getSpawnCount()))
					.build());
			final LizardmanShamanSafeSet shown = script.getTargetSet() != null
					? script.getTargetSet()
					: script.getCurrentSet();
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Spot:")
					.right(shown == null ? "None" : shown.getLabel())
					.build());
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Next:")
					.right(script.getDetail())
					.build());
		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}
}
