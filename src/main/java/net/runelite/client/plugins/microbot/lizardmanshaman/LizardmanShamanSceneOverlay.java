package net.runelite.client.plugins.microbot.lizardmanshaman;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;

public class LizardmanShamanSceneOverlay extends Overlay
{
	private static final int MAX_RENDER_DISTANCE = 24;

	private final LizardmanShamanPlugin plugin;

	@Inject
	LizardmanShamanSceneOverlay(LizardmanShamanPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			final LizardmanShamanConfig config = plugin.getConfig();
			final LizardmanShamanScript script = plugin.getScript();
			if (config == null || script == null
					|| (!config.showDangerTiles() && !config.showSafeSpots() && !config.showDodgeTile()))
			{
				return null;
			}

			final WorldPoint playerTile = Rs2Player.getWorldLocation();
			if (playerTile == null)
			{
				return null;
			}

			final Client client = Microbot.getClient();
			if (client == null)
			{
				return null;
			}

			if (config.showDangerTiles())
			{
				for (LizardmanShamanDangerArea area : script.getVisibleDangers())
				{
					if (area.getAnchor().distanceTo(playerTile) > MAX_RENDER_DISTANCE)
					{
						continue;
					}

					final Color fill = area.getType() == LizardmanShamanDangerArea.Type.JUMP
							? config.dangerColor()
							: config.dangerColor().darker();
					final Color outline = new Color(
							fill.getRed(), fill.getGreen(), fill.getBlue(),
							Math.min(255, fill.getAlpha() + 80));

					for (WorldPoint tile : area.getTiles())
					{
						drawTile(graphics, client, tile, fill, outline);
					}
				}
			}

			if (config.showSafeSpots())
			{
				drawSafeSpots(graphics, client, config, script, playerTile);
			}

			if (config.showDodgeTile() && script.getSpotTarget() != null)
			{
				final Color fill = config.dodgeColor();
				final Color outline = new Color(
						fill.getRed(), fill.getGreen(), fill.getBlue(),
						Math.min(255, fill.getAlpha() + 80));
				drawTile(graphics, client, script.getSpotTarget(), fill, outline);
			}
		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(getClass().getSimpleName(), ex);
		}
		return null;
	}

	private void drawSafeSpots(Graphics2D graphics, Client client, LizardmanShamanConfig config,
							   LizardmanShamanScript script, WorldPoint playerTile)
	{
		final Color fill = config.safeSpotColor();
		final Color outline = new Color(
				fill.getRed(), fill.getGreen(), fill.getBlue(),
				Math.min(255, fill.getAlpha() + 80));

		for (LizardmanShamanSafeSet set : LizardmanShamanSafeSet.values())
		{
			for (WorldPoint tile : script.getSafeSpotTiles(set))
			{
				if (tile.distanceTo(playerTile) > MAX_RENDER_DISTANCE)
				{
					continue;
				}
				drawTile(graphics, client, tile, fill, outline);
			}
		}
	}

	private void drawTile(Graphics2D graphics, Client client, WorldPoint tile, Color fill, Color outline)
	{
		final LocalPoint localPoint = LocalPoint.fromWorld(client, tile);
		if (localPoint == null)
		{
			return;
		}

		final Polygon polygon = Perspective.getCanvasTilePoly(client, localPoint);
		if (polygon == null)
		{
			return;
		}

		OverlayUtil.renderPolygon(graphics, polygon, outline, fill, new BasicStroke(1));
	}
}
