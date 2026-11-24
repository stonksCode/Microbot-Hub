package net.runelite.client.plugins.microbot.delveauto.enums;

/**
 * High level state shown by the overlay. The script is a priority arbiter rather than a state
 * machine, so this only describes what the last tick decided to do.
 */
public enum DelveAutoState
{
	IDLE,
	WAITING_FOR_BOSS,
	PRAY_MELEE,
	PRAY_MAGIC,
	PRAY_RANGE,
	EATING,
	DRINKING_PRAYER,
	DODGING_ROCK,
	DESTROYING_EARTH,
	TAKING_SHIELD,
	INTERRUPTING_BEAM,
	KILLING_LARVA,
	CASTING_UTILITY,
	RESTORING_WEAPON,
	PAUSED,
	ATTACKING,
	DESCENDING,
	FINISHED,
	ERROR
}
