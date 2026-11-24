package net.runelite.client.plugins.microbot.delveauto;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

@Slf4j
public class DelveAutoOverlay extends OverlayPanel
{
	private final DelvePrayerService prayerService;

	@Inject
	private DelveAutoScript script;

	@Inject
	DelveAutoOverlay(DelveAutoPlugin plugin, DelvePrayerService prayerService)
	{
		super(plugin);
		this.prayerService = prayerService;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			panelComponent.setPreferredSize(new Dimension(220, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Engin's Delve Auto V" + DelveAutoPlugin.version)
				.color(Color.GREEN)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(line("State", script.getState().name()));
			panelComponent.getChildren().add(line("Floors cleared", String.valueOf(script.getFloorsCleared())));

			String weapon = DelveWeaponService.getEquippedWeaponName();
			if (weapon != null)
			{
				panelComponent.getChildren().add(line("Weapon", weapon));
			}

			if (script.isPunishingBeam())
			{
				panelComponent.getChildren().add(line("Beam punish", "melee"));
			}

			if (script.getWeaponService().isRestorePending())
			{
				panelComponent.getChildren().add(line("Weapon restore", "pending"));
			}

			DelveMagicUtilityService utility = script.getMagicUtilityService();
			if (!"off".equals(utility.getStatus()))
			{
				panelComponent.getChildren().add(line("Utility", utility.getStatus()));
			}

			DelveLarvaService larvae = script.getLarvaService();
			if (!"off".equals(larvae.getStatus()))
			{
				panelComponent.getChildren().add(line("Larvae", larvae.getLarvaCount() + " | " + larvae.getStatus()));
				if (!"-".equals(larvae.getTargetStyle()))
				{
					panelComponent.getChildren().add(line("Larva style", larvae.getTargetStyle()));
				}
			}

			DelveHazardTracker hazards = script.getHazardTracker();
			panelComponent.getChildren().add(line("Boss animation", String.valueOf(hazards.getLastBossAnimation())));
			panelComponent.getChildren().add(line("Projectiles in flight", String.valueOf(prayerService.getTrackedProjectiles())));

			int ticksUntilImpact = prayerService.getTicksUntilImpact();
			if (ticksUntilImpact >= 0)
			{
				panelComponent.getChildren().add(line("Impact in", ticksUntilImpact + "t"));
			}

			if (hazards.isRockFuseActive())
			{
				panelComponent.getChildren().add(line("Rock lands in", hazards.getTicksUntilRockImpact() + "t"));
			}

			panelComponent.getChildren().add(line("Shockwave", hazards.isShockwaveActive() ? "active" : "-"));
			panelComponent.getChildren().add(line("Beam charging", hazards.isBeamCharging() ? "yes" : "-"));

			if (hazards.getShieldDestination() != null)
			{
				panelComponent.getChildren().add(line("Shield destination", hazards.getShieldDestination().toString()));
			}
			else if (hazards.getEarthenShieldTile() != null)
			{
				panelComponent.getChildren().add(line("Shield tile", hazards.getEarthenShieldTile().toString()));
			}
		}
		catch (Exception ex)
		{
			log.debug("Error rendering Delve Auto overlay", ex);
		}

		return super.render(graphics);
	}

	private LineComponent line(String left, String right)
	{
		return LineComponent.builder()
			.left(left + ": ")
			.right(right)
			.build();
	}
}
