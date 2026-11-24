package net.runelite.client.plugins.microbot.marketfactory.enums;

/**
 * How the plugin divides its time between same-item flipping and input-to-output processing.
 */
public enum RouteMode
{
	FLIP_ONLY("Flip only"),
	PROCESS_ONLY("Process only"),
	FLIP_THEN_PROCESS("Flip, then process"),
	PROCESS_THEN_FLIP("Process, then flip"),
	BEST_OF_BOTH("Best of both");

	private final String displayName;

	RouteMode(String displayName)
	{
		this.displayName = displayName;
	}

	public boolean allowsFlips()
	{
		return this != PROCESS_ONLY;
	}

	public boolean allowsProcessing()
	{
		return this != FLIP_ONLY;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
