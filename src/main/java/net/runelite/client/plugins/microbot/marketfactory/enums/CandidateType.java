package net.runelite.client.plugins.microbot.marketfactory.enums;

public enum CandidateType
{
	FLIP("Flip"),
	PROCESS("Process");

	private final String displayName;

	CandidateType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
