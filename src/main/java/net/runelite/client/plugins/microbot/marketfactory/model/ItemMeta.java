package net.runelite.client.plugins.microbot.marketfactory.model;

/**
 * Static item data from /api/v1/osrs/mapping.
 */
public final class ItemMeta
{
	private final int id;
	private final String name;
	private final boolean members;
	private final int buyLimit;
	private final int highAlch;
	private final int lowAlch;
	private final int value;

	public ItemMeta(int id, String name, boolean members, int buyLimit, int highAlch, int lowAlch, int value)
	{
		this.id = id;
		this.name = name;
		this.members = members;
		this.buyLimit = buyLimit;
		this.highAlch = highAlch;
		this.lowAlch = lowAlch;
		this.value = value;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean isMembers()
	{
		return members;
	}

	/** Official four-hour Grand Exchange buy limit, or 0 when unknown. */
	public int getBuyLimit()
	{
		return buyLimit;
	}

	public int getHighAlch()
	{
		return highAlch;
	}

	public int getLowAlch()
	{
		return lowAlch;
	}

	public int getValue()
	{
		return value;
	}

	@Override
	public String toString()
	{
		return name + " (" + id + ')';
	}
}
