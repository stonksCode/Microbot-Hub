package net.runelite.client.plugins.microbot.delveauto;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveAutoProjectile;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks incoming Doom projectiles and keeps the matching protection prayer up.
 *
 * <p>Deliberately mirrors the proven standalone prayer helper:</p>
 * <ul>
 *     <li>Projectiles live in a {@link Set}, so the multiple {@code ProjectileMoved} events for the
 *     same projectile cannot duplicate it, and two attacks landing on the same cycle cannot evict
 *     each other.</li>
 *     <li>Prayer switches use the non-blocking {@link Rs2Prayer#toggle(Rs2PrayerEnum)}. The
 *     {@code toggle(prayer, true)} overload waits up to 10 seconds for the server to confirm, which
 *     stalls the loop and makes back-to-back switches miss.</li>
 *     <li>A click is not repeated until it has had a tick or two to register, otherwise the second
 *     click would switch the same prayer straight back off.</li>
 * </ul>
 */
@Slf4j
@Singleton
public class DelvePrayerService
{
	/** Delay after the last tracked projectile lands before the protection prayer is dropped. */
	private static final long PROTECTION_DROP_DELAY_MS = 1000;

	/** A click needs a tick to register; retry after this many game ticks if it did not. */
	private static final int CLICK_RETRY_TICKS = 2;

	/** Landing margin in game cycles, so a prayer is not dropped just before the hit resolves. */
	private static final int LANDING_MARGIN_CYCLES = 0;

	/** Written from the client thread ({@code ProjectileMoved}), read from the script thread. */
	private final Set<Projectile> incomingProjectiles = ConcurrentHashMap.newKeySet();

	private long lastProjectileTime;
	private boolean protectionDropped;

	private Rs2PrayerEnum lastClickedPrayer;
	private int lastClickTick = -100;
	private int lastDropClickTick = -100;
	private int lastOffensiveClickTick = -100;
	private Projectile lastEnsuredProjectile;

	private volatile DelveAutoProjectile lastProjectile;
	private volatile int lastImpactCycle;

	@Inject
	public DelvePrayerService()
	{
	}

	public void onProjectileMoved(Projectile projectile)
	{
		if (projectile == null)
		{
			return;
		}

		if (DelveAutoProjectile.fromId(projectile.getId()) == null)
		{
			return;
		}

		incomingProjectiles.add(projectile);
	}

	/**
	 * Called once per script tick. Must run before any other decision so prayers are never skipped.
	 *
	 * @param reassertProtection when false, protection is only ensured once per projectile instead of
	 *                           being re-checked every tick
	 */
	public void tick(Rs2NpcModel boss, boolean offensivePrayer, boolean reassertProtection)
	{
		int currentCycle = Microbot.getClient().getGameCycle();
		int currentTick = Microbot.getClient().getTickCount();

		removeLandedProjectiles(currentCycle);

		Projectile next = soonestProjectile();
		if (next == null)
		{
			long now = System.currentTimeMillis();
			if (lastProjectileTime == 0)
			{
				lastProjectileTime = now;
			}

			if (!protectionDropped && now - lastProjectileTime > PROTECTION_DROP_DELAY_MS)
			{
				dropProtectionPrayer(currentTick);
				protectionDropped = true;
			}

			updateOffensivePrayer(boss, offensivePrayer, currentTick);
			return;
		}

		protectionDropped = false;
		lastProjectileTime = System.currentTimeMillis();
		lastProjectile = DelveAutoProjectile.fromId(next.getId());
		lastImpactCycle = next.getEndCycle();

		if (reassertProtection || next != lastEnsuredProjectile)
		{
			ensureProtection(next, currentTick);
			lastEnsuredProjectile = next;
		}

		updateOffensivePrayer(boss, offensivePrayer, currentTick);
	}

	/**
	 * Drops projectiles once their landing cycle has passed. This is what moves the protection prayer
	 * on to the next attack in line.
	 */
	private void removeLandedProjectiles(int currentCycle)
	{
		Iterator<Projectile> iterator = incomingProjectiles.iterator();
		while (iterator.hasNext())
		{
			Projectile projectile = iterator.next();
			if (projectile.getEndCycle() + LANDING_MARGIN_CYCLES >= currentCycle)
			{
				continue;
			}

			if (lastEnsuredProjectile == projectile)
			{
				lastEnsuredProjectile = null;
			}
			iterator.remove();
		}
	}

	private Projectile soonestProjectile()
	{
		Projectile soonest = null;
		for (Projectile projectile : incomingProjectiles)
		{
			if (DelveAutoProjectile.fromId(projectile.getId()) == null)
			{
				continue;
			}
			if (soonest == null || projectile.getEndCycle() < soonest.getEndCycle())
			{
				soonest = projectile;
			}
		}
		return soonest;
	}

	/**
	 * Makes sure the protection prayer for this attack is on. Never blocks: the click goes out once and
	 * the loop keeps running, so the next attack in a fast sequence is handled on time.
	 */
	private void ensureProtection(Projectile projectile, int currentTick)
	{
		DelveAutoProjectile attack = DelveAutoProjectile.fromId(projectile.getId());
		if (attack == null)
		{
			return;
		}

		Rs2PrayerEnum wanted = attack.getProtectionPrayer();

		// A drop clicked a moment ago may not have registered yet, so an "active" reading right after a
		// drop cannot be trusted: click anyway (the drop resolves first, ours turns it back on).
		boolean dropMayBePending = currentTick - lastDropClickTick <= 1;

		if (Rs2Prayer.isPrayerActive(wanted) && !dropMayBePending)
		{
			return;
		}

		if (Rs2Prayer.isOutOfPrayer())
		{
			return;
		}

		if (wanted == lastClickedPrayer && currentTick - lastClickTick < CLICK_RETRY_TICKS)
		{
			return;
		}

		Rs2Prayer.toggle(wanted);
		lastClickedPrayer = wanted;
		lastClickTick = currentTick;
	}

	private void updateOffensivePrayer(Rs2NpcModel boss, boolean enabled, int currentTick)
	{
		if (!enabled)
		{
			return;
		}

		Rs2PrayerEnum offensivePrayer = Rs2Prayer.getBestRangePrayer();
		if (offensivePrayer == null)
		{
			return;
		}

		// The shield phase does not drain prayer faster, but keeping Rigour up while the boss is
		// invulnerable is wasted points. Only the ranged prayer matters here.
		boolean shielded = boss != null && boss.getId() == net.runelite.api.gameval.NpcID.DOM_BOSS_SHIELDED;
		boolean shouldBeActive = !shielded;

		if (Rs2Prayer.isPrayerActive(offensivePrayer) == shouldBeActive)
		{
			return;
		}

		if (currentTick - lastOffensiveClickTick < CLICK_RETRY_TICKS)
		{
			return;
		}

		Rs2Prayer.toggle(offensivePrayer);
		lastOffensiveClickTick = currentTick;
	}

	private void dropProtectionPrayer(int currentTick)
	{
		Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
		if (active == null)
		{
			return;
		}

		Rs2Prayer.toggle(active);
		lastDropClickTick = currentTick;
		lastClickedPrayer = null;
	}

	/** Cleanly finish a fight: drop protection and the ranged offensive prayer, then clear tracking. */
	public void endFight()
	{
		incomingProjectiles.clear();
		dropProtectionPrayer(Microbot.getClient().getTickCount());

		Rs2PrayerEnum offensive = Rs2Prayer.getBestRangePrayer();
		if (offensive != null && Rs2Prayer.isPrayerActive(offensive))
		{
			Rs2Prayer.toggle(offensive);
		}

		reset();
	}

	public void reset()
	{
		incomingProjectiles.clear();
		lastProjectileTime = 0;
		protectionDropped = false;
		lastClickedPrayer = null;
		lastClickTick = -100;
		lastDropClickTick = -100;
		lastOffensiveClickTick = -100;
		lastEnsuredProjectile = null;
		lastProjectile = null;
		lastImpactCycle = 0;
	}

	public int getTrackedProjectiles()
	{
		return incomingProjectiles.size();
	}

	public int getTicksUntilImpact()
	{
		if (lastImpactCycle == 0)
		{
			return -1;
		}
		return Math.max(0, (lastImpactCycle - Microbot.getClient().getGameCycle()) / 30);
	}

	public DelveAutoProjectile getLastProjectile()
	{
		return lastProjectile;
	}
}
