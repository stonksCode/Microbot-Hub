package net.runelite.client.plugins.microbot.marketfactory.enums;

public enum MembershipMode
{
	AUTO("Detect account and world"),
	F2P_ONLY("Free-to-play items only"),
	MEMBERS_ONLY("Members items only");

	private final String displayName;

	MembershipMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
