package net.runelite.client.plugins.microbot.marketfactory.enums;

/**
 * What the plugin does with the candidates it finds.
 *
 * <ul>
 *   <li>{@link #OFF}: scan only. Trades that are already open keep being managed.</li>
 *   <li>{@link #PAPER}: run the full trade state machine against a simulated Grand Exchange.
 *       Nothing is clicked in the game. Use it to smoke-test the flow and the journal.</li>
 *   <li>{@link #LIVE}: place real offers on the Grand Exchange.</li>
 * </ul>
 */
public enum ExecutionMode
{
	OFF("Off (scan only)"),
	PAPER("Paper trade (simulated)"),
	LIVE("Live (real GE offers)");

	private final String label;

	ExecutionMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
