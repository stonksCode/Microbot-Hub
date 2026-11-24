package net.runelite.client.plugins.microbot.marketfactory.trade;

/**
 * Read-only view of one Grand Exchange offer, independent of how it was obtained (the live
 * client or the paper simulation).
 */
public final class OfferView
{
	public enum Status
	{
		/** Still buying or selling. */
		ACTIVE,
		/** Fully filled, waiting to be collected. */
		COMPLETE,
		/** Cancelled; anything filled so far is waiting to be collected. */
		CANCELLED
	}

	private final int slot;
	private final int itemId;
	private final boolean buy;
	private final Status status;
	private final int filled;
	private final int total;
	private final int price;

	public OfferView(int slot, int itemId, boolean buy, Status status, int filled, int total, int price)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.buy = buy;
		this.status = status;
		this.filled = filled;
		this.total = total;
		this.price = price;
	}

	public int getSlot()
	{
		return slot;
	}

	public int getItemId()
	{
		return itemId;
	}

	public boolean isBuy()
	{
		return buy;
	}

	public Status getStatus()
	{
		return status;
	}

	public int getFilled()
	{
		return filled;
	}

	public int getTotal()
	{
		return total;
	}

	public int getPrice()
	{
		return price;
	}

	public boolean isActive()
	{
		return status == Status.ACTIVE;
	}

	public boolean isComplete()
	{
		return status == Status.COMPLETE;
	}

	public boolean isCancelled()
	{
		return status == Status.CANCELLED;
	}

	@Override
	public String toString()
	{
		return (buy ? "BUY " : "SELL ") + itemId + " " + filled + "/" + total + " @" + price + " " + status;
	}
}
