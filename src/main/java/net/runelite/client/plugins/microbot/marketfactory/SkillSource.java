package net.runelite.client.plugins.microbot.marketfactory;

import net.runelite.api.Skill;

/**
 * Reads the account's real (unboosted) skill levels. Kept behind an interface so the
 * candidate engine stays free of client calls and can be exercised offline.
 */
@FunctionalInterface
public interface SkillSource
{
	int level(Skill skill);
}
