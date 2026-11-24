package net.runelite.client.plugins.microbot.delveauto;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.Locale;

/**
 * Temporary weapon swapping shared by every feature that needs a weapon other than the bow.
 *
 * <p>One swap cycle is open at a time. The first call captures the weapon that was worn as the
 * <em>base</em> weapon; every later call simply retargets the swap while keeping the same base. That
 * lets the beam punish take over a larva swap (or vice versa) without ever forgetting what to put
 * back on afterwards.</p>
 *
 * <p>All clicks are non-blocking: the arbiter keeps praying and eating while the swap is in flight.
 * Special attacks are opt-in and only ever released by the beam punish that armed them.</p>
 */
public class DelveWeaponService
{
	/** A wield click needs a game tick to register; retry slowly instead of every 50 ms. */
	private static final long WIELD_RETRY_MS = 450;

	/** Same for the special-attack orb. */
	private static final long SPECIAL_RETRY_MS = 600;

	/** Crystal and dragon halberds are the only common melee weapons with a 2-tile reach. */
	private static final int HALBERD_REACH = 2;
	private static final int DEFAULT_REACH = 1;

	/** The weapon that was worn before the swap cycle opened. Restored when the cycle ends. */
	private String baseWeapon;
	/** The weapon the swap cycle currently wants in the weapon slot. */
	private String swapWeapon;
	private boolean swapActive;
	private boolean restorePending;
	private boolean specialArmed;
	private boolean specialUsed;
	private boolean specialReleasePending;
	private long lastWieldAt;
	private long lastSpecialAttemptAt;

	/**
	 * Opens or retargets a swap cycle. The first call captures the currently worn weapon as the
	 * base; later calls only change the requested weapon. Returns true when a target was set.
	 */
	public boolean beginSwap(String weapon)
	{
		String trimmed = weapon == null ? "" : weapon.trim();
		if (trimmed.isEmpty())
		{
			return false;
		}

		if (baseWeapon == null)
		{
			baseWeapon = getEquippedWeaponName();
		}

		swapWeapon = trimmed;
		// A new target supersedes any pending restore; the base weapon is still remembered.
		restorePending = false;
		return true;
	}

	/** True once the swap cycle has actually changed the weapon slot at least once. */
	public boolean isSwapActive()
	{
		return swapActive;
	}

	/** The weapon the swap cycle currently wants equipped, or null when no cycle is open. */
	public String getSwapWeaponName()
	{
		return swapWeapon;
	}

	/** True once the requested weapon is actually in the weapon slot. */
	public boolean isSwapWeaponEquipped()
	{
		return swapWeapon != null && isWearing(swapWeapon);
	}

	/** True when the weapon is either worn or still in the inventory (so a swap is possible). */
	public boolean hasSwapWeapon()
	{
		return swapWeapon != null && (isWearing(swapWeapon) || Rs2Inventory.hasItem(swapWeapon));
	}

	/**
	 * Wields the requested weapon if needed. Returns true when a click was sent this call, false when
	 * the weapon is already worn or the retry throttle is still active.
	 */
	public boolean equipSwapWeapon()
	{
		if (swapWeapon == null)
		{
			return false;
		}

		if (isWearing(swapWeapon))
		{
			swapActive = true;
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastWieldAt < WIELD_RETRY_MS)
		{
			return false;
		}
		lastWieldAt = now;

		if (!Rs2Inventory.wield(swapWeapon))
		{
			return false;
		}

		swapActive = true;
		return true;
	}

	/**
	 * Ends the current swap cycle. The base weapon and the special-attack release are performed by
	 * {@link #tick()} over the next few ticks, so they cannot stall prayer handling.
	 */
	public void endSwap()
	{
		if (specialArmed)
		{
			specialReleasePending = true;
		}
		if (swapActive)
		{
			restorePending = true;
		}
		swapWeapon = null;
	}

	/** Starts a beam-punish cycle. Behaves like {@link #beginSwap(String)} plus a special reset. */
	public void beginPunish(String weapon)
	{
		if (!beginSwap(weapon))
		{
			return;
		}

		// A fresh beam cycle always starts with an unarmed special.
		specialArmed = false;
		specialUsed = false;
		specialReleasePending = false;
		lastSpecialAttemptAt = 0;
	}

	/** True once the configured melee weapon is actually in the weapon slot. */
	public boolean isPunishWeaponEquipped()
	{
		return isSwapWeaponEquipped();
	}

	/** True when the punish weapon is either worn or still in the inventory. */
	public boolean hasPunishWeapon()
	{
		return hasSwapWeapon();
	}

	/** Wields the punish weapon if needed. */
	public boolean equipPunishWeapon()
	{
		return equipSwapWeapon();
	}

	/**
	 * Arms the special attack once per beam. Returns true when the special is ready (already armed
	 * or successfully toggled this call). Returns false when it cannot or should not be used, in
	 * which case the caller should attack normally.
	 */
	public boolean tryArmSpecial(int energyPercent)
	{
		if (specialArmed)
		{
			return true;
		}
		if (specialUsed)
		{
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastSpecialAttemptAt < SPECIAL_RETRY_MS)
		{
			return false;
		}
		lastSpecialAttemptAt = now;

		int required = Math.max(0, Math.min(100, energyPercent)) * 10;
		if (required > 0 && Rs2Combat.getSpecEnergy() < required)
		{
			return false;
		}

		if (!Rs2Combat.setSpecState(true, required))
		{
			return false;
		}

		specialArmed = true;
		return true;
	}

	/** Marks the special as spent so further punish attacks in the same beam are normal hits. */
	public void markSpecialUsed()
	{
		if (!specialArmed)
		{
			return;
		}
		specialUsed = true;
		specialReleasePending = true;
	}

	/** Ends a beam-punish cycle. Identical to {@link #endSwap()}. */
	public void endPunish()
	{
		endSwap();
	}

	/** True while the base weapon still has to be put back on. */
	public boolean isRestorePending()
	{
		return restorePending;
	}

	/** Drives the deferred special release and weapon restore. Safe to call every tick. */
	public void tick()
	{
		tickSpecialRelease();
		tickRestore();
	}

	private void tickSpecialRelease()
	{
		if (!specialReleasePending)
		{
			return;
		}

		// Never touch a special we did not arm.
		if (specialArmed && isSpecialAttackEnabled())
		{
			Rs2Combat.setSpecState(false, -1);
			if (isSpecialAttackEnabled())
			{
				return;
			}
		}

		specialArmed = false;
		specialReleasePending = false;
	}

	private void tickRestore()
	{
		if (!restorePending)
		{
			return;
		}

		// A new swap cycle opened before the restore finished; leave the base weapon remembered
		// and let the new cycle run.
		if (swapWeapon != null)
		{
			restorePending = false;
			return;
		}

		if (baseWeapon == null || isWearing(baseWeapon))
		{
			restorePending = false;
			swapActive = false;
			baseWeapon = null;
			return;
		}

		if (!Rs2Inventory.hasItem(baseWeapon))
		{
			// The base weapon is gone (dropped, or the inventory changed). Do not block combat
			// forever trying to restore something that no longer exists.
			restorePending = false;
			swapActive = false;
			baseWeapon = null;
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastWieldAt < WIELD_RETRY_MS)
		{
			return;
		}
		lastWieldAt = now;

		Rs2Inventory.wield(baseWeapon);
	}

	private boolean isSpecialAttackEnabled()
	{
		int enabled = Microbot.getVarbitPlayerValue(VarPlayerID.SA_ATTACK);
		if (enabled == 0 || enabled == 1)
		{
			return enabled == 1;
		}
		return Rs2Combat.getSpecState();
	}

	private boolean isWearing(String name)
	{
		return name != null && !name.isEmpty() && Rs2Equipment.isWearing(name);
	}

	public static String getEquippedWeaponName()
	{
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		return weapon == null ? null : weapon.getName();
	}

	/**
	 * Resolves the configured override, falling back to the weapon type: halberds reach two tiles,
	 * everything else has to be adjacent.
	 */
	public static int reachFor(String weaponName, int configuredReach)
	{
		if (configuredReach >= 1 && configuredReach <= HALBERD_REACH)
		{
			return configuredReach;
		}

		if (weaponName == null)
		{
			return DEFAULT_REACH;
		}

		return weaponName.toLowerCase(Locale.ROOT).contains("halberd") ? HALBERD_REACH : DEFAULT_REACH;
	}

	public void reset()
	{
		baseWeapon = null;
		swapWeapon = null;
		swapActive = false;
		restorePending = false;
		specialArmed = false;
		specialUsed = false;
		specialReleasePending = false;
		lastWieldAt = 0;
		lastSpecialAttemptAt = 0;
	}
}
