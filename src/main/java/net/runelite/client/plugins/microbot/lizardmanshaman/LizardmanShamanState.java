package net.runelite.client.plugins.microbot.lizardmanshaman;

/**
 * High level state of the Lizardman Shaman script, exposed to the overlay.
 */
public enum LizardmanShamanState
{
	IDLE,
	SEARCHING,
	FIGHTING,
	POSITIONING,
	WAITING_SPAWN,
	DODGING,
	STOPPED
}
