package net.runelite.client.plugins.microbot.marketfactory.enums;

public enum RiskLevel
{
	CONSERVATIVE("Conservative"),
	BALANCED("Balanced"),
	AGGRESSIVE("Aggressive"),
	CUSTOM("Custom");

	private final String displayName;

	RiskLevel(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
