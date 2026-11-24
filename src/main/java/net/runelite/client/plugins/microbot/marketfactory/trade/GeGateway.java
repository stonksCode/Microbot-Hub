package net.runelite.client.plugins.microbot.marketfactory.trade;

/**
 * Everything the trade state machine needs from the Grand Exchange. There are two
 * implementations: the live one drives the real client, the paper one simulates fills from
 * market data. Keeping the state machine behind this interface is what makes it testable
 * offline.
 *
 * <p>All methods are blocking and must be called from the script thread, never the client
 * thread.
 */
public interface GeGateway
{
	/** False while logged out, on a break or otherwise unable to act. */
	boolean isReady();

	/** Coins that can be spent right now (offers already placed have escrowed theirs). */
	long freeCoins();

	/** Number of empty Grand Exchange slots. */
	int freeSlots();

	/** Places an offer. Returns false if it could not be placed. */
	boolean place(boolean buy, int itemId, String itemName, int price, int quantity);

	/** The offer for this item on the given side, or null when there is none. */
	OfferView offer(int itemId, boolean buy);

	/**
	 * Requests cancellation of the offer. A live gateway also collects, so the offer can
	 * disappear straight away; the paper gateway leaves it cancelled until {@link #collect()}.
	 */
	boolean abort(int itemId, String itemName, boolean buy);

	/** Collects every finished or cancelled offer into the inventory. */
	boolean collect();

	/** Units of this item held in the inventory, noted or not. */
	int inventoryCount(String itemName);

	/** Advances any simulation. No-op for the live gateway. */
	default void tick()
	{
	}

	/** Called when a step needed no Grand Exchange action; lets the live gateway close the GE. */
	default void idle()
	{
	}

	boolean isPaper();
}
