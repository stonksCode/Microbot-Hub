package net.runelite.client.plugins.microbot.marketfactory.breaks;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.marketfactory.MarketFactoryConfig;

/**
 * Thin guard layered on top of BreakHandlerV2. The script still checks
 * {@link BreakHandlerScript#isBreakActive()} and {@link BreakHandlerScript#isInGameBreakActive()};
 * this class centralises the decision so future phases can pause GE interactions cleanly.
 *
 * <p>We intentionally do not reimplement break timing or logout logic. If BreakHandler V2
 * is disabled in config or the handler is not running, the guard degrades to a no-op.
 */
@Slf4j
public final class BreakGuard
{
	private BreakGuard()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static boolean shouldPause(MarketFactoryConfig config)
	{
		if (config == null || !config.useBreakHandler() || !config.breakGuardEnabled())
		{
			return false;
		}
		try
		{
			if (BreakHandlerScript.isBreakActive())
			{
				return true;
			}
		}
		catch (Throwable ex)
		{
			log.debug("BreakHandler state unavailable", ex);
		}
		return false;
	}

	/** Delegates to BreakHandlerV2's lock state so other code can observe plugin idleness. */
	public static void setIdleLock(boolean locked)
	{
		try
		{
			BreakHandlerScript.setLockState(locked);
		}
		catch (Throwable ex)
		{
			// BreakHandler may not be loaded
		}
	}

	/** True if BreakHandler reports any break in progress. */
	public static boolean anyBreakActive()
	{
		try
		{
			return BreakHandlerScript.isBreakActive();
		}
		catch (Throwable ex)
		{
			return false;
		}
	}
}
