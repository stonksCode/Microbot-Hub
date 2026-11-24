package net.runelite.client.plugins.microbot.delveauto;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveThrallChoice;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Arceuus spellbook upkeep: a summoned thrall and Death Charge.
 *
 * <p>Both are optional and only meaningful on the Arceuus spellbook. They run after every defensive
 * mechanic and before the normal attack, so a rock dodge, shield setup, beam punish or acid escape is
 * never delayed by a cast. Casting is also skipped while the player is already animating, which stops
 * the utility from clipping an in-progress attack.</p>
 *
 * <p>Thralls last about a minute and then go on a short cooldown. Death Charge is a one-off buff that
 * restores prayer and special energy on the next kill, so it is refreshed whenever it is neither
 * active nor cooling down. Varbit values are read every tick so the overlay stays accurate even when
 * the feature is disabled.</p>
 */
@Slf4j
public class DelveMagicUtilityService
{
	/** Failed casts are retried on a slower cadence than the 50 ms tick. */
	private static final long THRALL_RETRY_MS = 2000;
	private static final long DEATH_CHARGE_RETRY_MS = 3000;

	/** Do not start a cast while an attack or another spell is still playing. */
	private static final int CAST_ANIMATION_GUARD_MS = 400;

	private long lastThrallAttemptAt;
	private long lastDeathChargeAttemptAt;
	private boolean warnedSpellbook;

	@Getter
	private String lastThrallName;

	@Getter
	private boolean deathChargeActive;

	@Getter
	private boolean deathChargeOnCooldown;

	@Getter
	private String status = "off";

	/**
	 * Runs one tick of Arceuus upkeep.
	 *
	 * @return true when a spell was cast, in which case the arbiter should consume the tick
	 */
	public boolean tick(DelveAutoConfig config)
	{
		refreshDeathChargeState();

		if (!config.useThralls() && !config.useDeathCharge())
		{
			status = "off";
			return false;
		}

		if (!Rs2Magic.isSpellbook(Rs2Spellbook.ARCEUUS))
		{
			status = "need Arceuus";
			if (!warnedSpellbook)
			{
				warnedSpellbook = true;
				Microbot.log("Delve Auto: thralls and Death Charge require the Arceuus spellbook");
			}
			return false;
		}

		warnedSpellbook = false;

		// Never start a cast on top of an attack or an existing spell.
		if (Rs2Player.isAnimating(CAST_ANIMATION_GUARD_MS))
		{
			return false;
		}

		if (config.useThralls() && trySummonThrall(config.thrallChoice()))
		{
			return true;
		}

		return config.useDeathCharge() && tryDeathCharge();
	}

	private void refreshDeathChargeState()
	{
		deathChargeActive = Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE) > 0;
		deathChargeOnCooldown = Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_COOLDOWN) == 1;
	}

	private boolean trySummonThrall(DelveThrallChoice choice)
	{
		// Covers both the summoned thrall and its short resurrection cooldown.
		if (Rs2Thrall.isActive())
		{
			status = lastThrallName != null ? lastThrallName + " active" : "thrall active";
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastThrallAttemptAt < THRALL_RETRY_MS)
		{
			return false;
		}

		Rs2Thrall thrall = resolveThrall(choice);
		if (thrall == null)
		{
			status = "no thrall available";
			return false;
		}

		lastThrallAttemptAt = now;
		if (!Rs2Thrall.cast(thrall))
		{
			status = "thrall cast failed";
			return false;
		}

		lastThrallName = thrall.getName();
		status = "summoned " + lastThrallName;
		Microbot.log("Delve Auto: summoned " + lastThrallName);
		return true;
	}

	/**
	 * Resolves the thrall to summon. With {@link DelveThrallChoice#AUTO} this is simply the
	 * highest-tier thrall that can be cast. A specific choice is used when possible, otherwise it
	 * degrades to the strongest castable thrall of the same type so the player still gets a summon
	 * instead of nothing when, for example, they are short on blood runes.
	 */
	private Rs2Thrall resolveThrall(DelveThrallChoice choice)
	{
		Rs2Thrall preferred = choice == null ? null : choice.getThrall();
		if (preferred != null && Rs2Thrall.canCast(preferred))
		{
			return preferred;
		}

		return bestCastable(preferred == null ? null : preferred.getThrallType());
	}

	/**
	 * Finds the highest-level castable thrall. {@link Rs2Thrall#getBestThrall(ThrallType)} returns the
	 * first match in declaration order, which is the <em>lowest</em> tier, so it cannot be used here.
	 */
	private Rs2Thrall bestCastable(ThrallType type)
	{
		Rs2Thrall best = null;
		for (Rs2Thrall thrall : Rs2Thrall.values())
		{
			if (type != null && thrall.getThrallType() != type)
			{
				continue;
			}
			if (!Rs2Thrall.canCast(thrall))
			{
				continue;
			}
			if (best == null || thrall.getRequiredLevel() > best.getRequiredLevel())
			{
				best = thrall;
			}
		}
		return best;
	}

	private boolean tryDeathCharge()
	{
		if (deathChargeActive)
		{
			status = "Death Charge active";
			return false;
		}

		if (deathChargeOnCooldown)
		{
			status = "Death Charge cooling down";
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastDeathChargeAttemptAt < DEATH_CHARGE_RETRY_MS)
		{
			return false;
		}

		// Spell.hasRequirements only checks level and spellbook, so this stays cheap and does not
		// switch tabs. The actual cast below does switch to the magic tab.
		if (!Rs2Magic.canCast(Rs2Spells.DEATH_CHARGE))
		{
			status = "Death Charge not castable";
			return false;
		}

		lastDeathChargeAttemptAt = now;
		if (!Rs2Magic.cast(Rs2Spells.DEATH_CHARGE))
		{
			status = "Death Charge cast failed";
			return false;
		}

		status = "cast Death Charge";
		Microbot.log("Delve Auto: cast Death Charge");
		return true;
	}

	public void reset()
	{
		lastThrallAttemptAt = 0;
		lastDeathChargeAttemptAt = 0;
		warnedSpellbook = false;
		lastThrallName = null;
		deathChargeActive = false;
		deathChargeOnCooldown = false;
		status = "off";
	}
}
