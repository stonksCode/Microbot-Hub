package net.runelite.client.plugins.microbot.delveauto.enums;

/**
 * Optional demonbane spell used instead of a weapon auto-attack when a magic larva has to be killed.
 *
 * <p>Demonbane spells one-shot a 2 HP larva without the one-tick attack delay that a normal spell
 * suffers, so they are worth using when the runes are available. {@link #AUTO} picks the strongest
 * castable demonbane spell. {@link #NONE} is the default and simply attacks with the configured
 * magic weapon (which must be able to auto-cast, e.g. a powered staff).</p>
 */
public enum DelveLarvaSpell
{
	NONE("None (weapon auto-cast)"),
	AUTO("Auto (best demonbane)"),
	INFERIOR_DEMONBANE("Inferior Demonbane"),
	SUPERIOR_DEMONBANE("Superior Demonbane"),
	DARK_DEMONBANE("Dark Demonbane");

	private final String name;

	DelveLarvaSpell(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
