package net.runelite.client.plugins.microbot.delveprayerhelper.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

@RequiredArgsConstructor
@Getter
/**
 * Projectile IDs for the Doom of Mokhaiotl attacks.
 * <p>
 * The client does not ship a {@code ProjectileID} constant class, so these remain hardcoded. The
 * ids are spotanim ids (the same values as {@code SpotanimID.VFX_STANDARD_PROJECTILE_*}).
 * <p>
 * Rock Throw shrapnel is not mapped here: those projectiles are the falling-rock visuals, not the
 * targeted attacks. Every projectile id is written to the fight log so an unmapped attack can be
 * spotted and added later.
 */
public enum DelvePrayerHelperProjectile
{
    MELEE(3378, Rs2PrayerEnum.PROTECT_MELEE),
    MAGE(3379, Rs2PrayerEnum.PROTECT_MAGIC),
    RANGE(3380, Rs2PrayerEnum.PROTECT_RANGE);

    private final int projectileID;
    private final Rs2PrayerEnum protectionPrayer;

    public static boolean isAttackProjectile(int projectileId)
    {
        return protectionFor(projectileId) != null;
    }

    public static Rs2PrayerEnum protectionFor(int projectileId)
    {
        for (DelvePrayerHelperProjectile projectile : values())
        {
            if (projectile.projectileID == projectileId)
            {
                return projectile.protectionPrayer;
            }
        }
        return null;
    }
}
