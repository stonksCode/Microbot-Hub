package net.runelite.client.plugins.microbot.marketfactory.enums;

public enum CapitalSource
{
	AUTO_DETECT("Detect from bank and inventory"),
	FIXED_GP("Use configured GP limit");

	private final String displayName;

	CapitalSource(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
