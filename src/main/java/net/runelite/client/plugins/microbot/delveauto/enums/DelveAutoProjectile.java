package net.runelite.client.plugins.microbot.delveauto.enums;

import lombok.Getter;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * The three standard Doom of Mokhaiotl projectiles and the protection prayer each one needs.
 */
@Getter
public enum DelveAutoProjectile
{
	MELEE(SpotanimID.VFX_STANDARD_PROJECTILE_MELEE, Rs2PrayerEnum.PROTECT_MELEE),
	MAGIC(SpotanimID.VFX_STANDARD_PROJECTILE_MAGIC, Rs2PrayerEnum.PROTECT_MAGIC),
	RANGE(SpotanimID.VFX_STANDARD_PROJECTILE_RANGE, Rs2PrayerEnum.PROTECT_RANGE);

	private final int projectileID;
	private final Rs2PrayerEnum protectionPrayer;

	DelveAutoProjectile(int projectileID, Rs2PrayerEnum protectionPrayer)
	{
		this.projectileID = projectileID;
		this.protectionPrayer = protectionPrayer;
	}

	public static DelveAutoProjectile fromId(int projectileID)
	{
		for (DelveAutoProjectile projectile : values())
		{
			if (projectile.projectileID == projectileID)
			{
				return projectile;
			}
		}
		return null;
	}
}
