package net.runelite.client.plugins.microbot.delveauto.enums;

import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;

/**
 * Arceuus thrall choices exposed in the config dropdown.
 *
 * <p>{@link #AUTO} summons the highest-tier thrall that can currently be cast. Choosing a specific
 * thrall prefers that exact one and falls back to the best castable thrall of the same type (so a
 * Greater ghost choice degrades to a Superior or Lesser ghost instead of not summoning at all).</p>
 */
public enum DelveThrallChoice
{
	AUTO("Auto (best available)", null),
	GREATER_GHOST("Greater ghost (magic)", Rs2Thrall.GREATER_GHOST),
	GREATER_SKELETON("Greater skeleton (ranged)", Rs2Thrall.GREATER_SKELETON),
	GREATER_ZOMBIE("Greater zombie (melee)", Rs2Thrall.GREATER_ZOMBIE),
	SUPERIOR_GHOST("Superior ghost (magic)", Rs2Thrall.SUPERIOR_GHOST),
	SUPERIOR_SKELETON("Superior skeleton (ranged)", Rs2Thrall.SUPERIOR_SKELETON),
	SUPERIOR_ZOMBIE("Superior zombie (melee)", Rs2Thrall.SUPERIOR_ZOMBIE),
	LESSER_GHOST("Lesser ghost (magic)", Rs2Thrall.LESSER_GHOST),
	LESSER_SKELETON("Lesser skeleton (ranged)", Rs2Thrall.LESSER_SKELETON),
	LESSER_ZOMBIE("Lesser zombie (melee)", Rs2Thrall.LESSER_ZOMBIE);

	private final String label;
	private final Rs2Thrall thrall;

	DelveThrallChoice(String label, Rs2Thrall thrall)
	{
		this.label = label;
		this.thrall = thrall;
	}

	public Rs2Thrall getThrall()
	{
		return thrall;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
