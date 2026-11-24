package net.runelite.client.plugins.microbot.marketfactory;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import lombok.extern.slf4j.Slf4j;

/**
 * Snapshot of liquid GP available to the plugin.
 */
@Slf4j
public final class CapitalSnapshot
{
	private final long inventoryCoins;
	private final long bankCoins;
	private final long totalCoins;
	private final boolean bankOpen;
	private final boolean bankMirror;
	private final String note;

	public CapitalSnapshot(long inventoryCoins, long bankCoins, long totalCoins,
		boolean bankOpen, boolean bankMirror, String note)
	{
		this.inventoryCoins = Math.max(0L, inventoryCoins);
		this.bankCoins = Math.max(0L, bankCoins);
		this.totalCoins = Math.max(0L, totalCoins);
		this.bankOpen = bankOpen;
		this.bankMirror = bankMirror;
		this.note = note == null ? "" : note;
	}

	public static CapitalSnapshot detect()
	{
		long inventory = readInventoryCoins();

		boolean bankOpen = false;
		try
		{
			bankOpen = Rs2Bank.isOpen();
		}
		catch (Exception ex)
		{
			bankOpen = false;
		}

		boolean bankMirror = false;
		try
		{
			bankMirror = Rs2Bank.hasBankMirrorSnapshot();
		}
		catch (Exception ex)
		{
			bankMirror = false;
		}

		long bank = 0L;
		String note = "";
		if (bankOpen)
		{
			try
			{
				bank = Rs2Bank.count(ItemID.COINS);
				if (bank <= 0)
				{
					bank = Rs2Bank.count(995);
				}
				note = "bank open";
			}
			catch (Exception ex)
			{
				bank = 0L;
				note = "bank open but count failed";
			}
		}
		else if (bankMirror)
		{
			try
			{
				bank = Rs2Bank.count(ItemID.COINS);
				if (bank <= 0)
				{
					bank = Rs2Bank.count(995);
				}
				note = "bank mirror";
			}
			catch (Exception ex)
			{
				bank = 0L;
				note = "bank mirror unavailable";
			}
		}
		else
		{
			note = "inventory only";
		}

		if (bankOpen && inventory <= 0)
		{
			inventory = readInventoryCoins();
		}

		long total = inventory + bank;
		log.debug("CapitalSnapshot.detect() inv={} bank={} total={} bankOpen={} mirror={} note={}",
			inventory, bank, total, bankOpen, bankMirror, note);
		return new CapitalSnapshot(inventory, bank, total, bankOpen, bankMirror, note);
	}

	private static long readInventoryCoins()
	{
		long coins = 0L;
		try
		{
			coins = Rs2Inventory.itemQuantity(ItemID.COINS);
		}
		catch (Exception ignored)
		{
		}

		if (coins <= 0)
		{
			try
			{
				coins = Rs2Inventory.itemQuantity(995);
			}
			catch (Exception ignored)
			{
			}
		}

		if (coins <= 0)
		{
			try
			{
				coins = Rs2Inventory.count(ItemID.COINS);
			}
			catch (Exception ignored)
			{
			}
		}

		if (coins <= 0)
		{
			try
			{
				coins = Rs2Inventory.count(995);
			}
			catch (Exception ignored)
			{
			}
		}

		return Math.max(0L, coins);
	}

	/** Capital available for trading after applying the configured cash reserve. */
	public long availableForTrading(double cashReservePercent)
	{
		double reserveFraction = Math.max(0D, Math.min(100D, cashReservePercent)) / 100D;
		long reserve = Math.round(totalCoins * reserveFraction);
		return Math.max(0L, totalCoins - reserve);
	}

	public long getInventoryCoins()
	{
		return inventoryCoins;
	}

	public long getBankCoins()
	{
		return bankCoins;
	}

	public long getTotalCoins()
	{
		return totalCoins;
	}

	public boolean isBankOpen()
	{
		return bankOpen;
	}

	public boolean hasBankMirror()
	{
		return bankMirror;
	}

	public String getNote()
	{
		return note;
	}

	@Override
	public String toString()
	{
		return "CapitalSnapshot{inv=" + inventoryCoins + ", bank=" + bankCoins
			+ ", total=" + totalCoins + ", note='" + note + "'}";
	}
}
