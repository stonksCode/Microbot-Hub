package net.runelite.client.plugins.microbot.delveauto;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveCombatStyle;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveLarvaSpell;
import net.runelite.client.plugins.microbot.delveauto.enums.DelveThrallChoice;

@ConfigGroup(DelveAutoConfig.configGroup)
public interface DelveAutoConfig extends Config
{
	String configGroup = "delve-auto";

	@ConfigSection(
		name = "Prayer",
		description = "Protection and offensive prayer handling",
		position = 0
	)
	String prayerSection = "prayer";

	@ConfigSection(
		name = "Combat",
		description = "Attacking and survival",
		position = 1
	)
	String combatSection = "combat";

	@ConfigSection(
		name = "Mechanics",
		description = "Boss mechanics to handle",
		position = 2
	)
	String mechanicsSection = "mechanics";

	@ConfigSection(
		name = "Beam punish",
		description = "Melee weapon swap used to interrupt the Special Beam Cannon",
		position = 3
	)
	String beamSection = "beam";

	@ConfigSection(
		name = "Arceuus",
		description = "Thralls and Death Charge from the Arceuus spellbook",
		position = 5,
		closedByDefault = true
	)
	String arceuusSection = "arceuus";

	@ConfigSection(
		name = "Progression",
		description = "Floor progression",
		position = 6
	)
	String progressionSection = "progression";

	@ConfigSection(
		name = "Larvae",
		description = "Demonic larva style swaps and shield-phase handling",
		position = 4
	)
	String larvaeSection = "larvae";

	@ConfigItem(
		keyName = "offensivePrayer",
		name = "Offensive prayer",
		description = "Keep the best unlocked ranged prayer active while attacking",
		position = 0,
		section = prayerSection
	)
	default boolean offensivePrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "reassertProtection",
		name = "Re-assert protection",
		description = "Re-enable the correct protection prayer every tick while a projectile is in flight. "
			+ "Required because Rock Throw clears overhead prayers on impact",
		position = 1,
		section = prayerSection
	)
	default boolean reassertProtection()
	{
		return true;
	}

	@ConfigItem(
		keyName = "eatAtPercent",
		name = "Eat at (%)",
		description = "Eat food when health drops below this percentage (0 disables)",
		position = 0,
		section = combatSection
	)
	@Range(min = 0, max = 100)
	default int eatAtPercent()
	{
		return 55;
	}

	@ConfigItem(
		keyName = "beamEatAtPercent",
		name = "Eat at (%) while beam charges",
		description = "Raised eat threshold while the Special Beam Cannon is charging, since an unblocked beam hits for 60+. "
			+ "Set to 0 to use the normal threshold instead",
		position = 1,
		section = combatSection
	)
	@Range(min = 0, max = 100)
	default int beamEatAtPercent()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "prayerPotAtPoints",
		name = "Prayer pot at (points)",
		description = "Drink a prayer potion when prayer points fall below this value (0 disables)",
		position = 2,
		section = combatSection
	)
	@Range(min = 0, max = 99)
	default int prayerPotAtPoints()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "preferredDistance",
		name = "Preferred distance",
		description = "Ideal number of tiles to keep between you and the boss",
		position = 3,
		section = combatSection
	)
	@Range(min = 3, max = 10)
	default int preferredDistance()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "dodgeRockThrow",
		name = "Dodge Rock Throw",
		description = "Step off the marked tile before the rock lands",
		position = 0,
		section = mechanicsSection
	)
	default boolean dodgeRockThrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "handleShockwave",
		name = "Handle shockwave",
		description = "Destroy volatile earth and stand on the earthen shield before the slam",
		position = 1,
		section = mechanicsSection
	)
	default boolean handleShockwave()
	{
		return true;
	}

	@ConfigItem(
		keyName = "avoidAcid",
		name = "Avoid acid (L3+)",
		description = "Treat acid pools as dangerous tiles when choosing where to stand",
		position = 2,
		section = mechanicsSection
	)
	default boolean avoidAcid()
	{
		return true;
	}

	@ConfigItem(
		keyName = "beamPunishWeapon",
		name = "Punish weapon",
		description = "Melee weapon to swap to when the Special Beam Cannon charges. A melee hit interrupts the beam. "
			+ "The crystal halberd is ideal: 2-tile reach and a strong special attack",
		position = 0,
		section = beamSection
	)
	default String beamPunishWeapon()
	{
		return "Crystal halberd";
	}

	@ConfigItem(
		keyName = "beamPunishReach",
		name = "Punish reach (tiles)",
		description = "How close you must stand to melee the boss. 0 = automatic (2 for halberds, otherwise 1)",
		position = 1,
		section = beamSection
	)
	@Range(min = 0, max = 2)
	default int beamPunishReach()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "beamPunishUseSpecial",
		name = "Use special attack",
		description = "Enable the special attack before the punish hit when enough energy is available",
		position = 2,
		section = beamSection
	)
	default boolean beamPunishUseSpecial()
	{
		return true;
	}

	@ConfigItem(
		keyName = "beamPunishSpecialEnergy",
		name = "Special energy cost (%)",
		description = "Special attack energy the punish weapon consumes, as a percentage (crystal halberd = 30). "
			+ "The special is only used when your energy is at least this high",
		position = 3,
		section = beamSection
	)
	@Range(min = 0, max = 100)
	default int beamPunishSpecialEnergy()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "handleLarvae",
		name = "Handle demonic larvae",
		description = "Kill demonic larvae before they reach the boss. Each larva has protection prayers that can "
			+ "block the equipped style, so the bot swaps to a weapon of an unprotected style",
		position = 0,
		section = larvaeSection
	)
	default boolean handleLarvae()
	{
		return true;
	}

	@ConfigItem(
		keyName = "larvaKillDistance",
		name = "Kill within (tiles of boss)",
		description = "Start attacking a larva once it is this many tiles from the boss. Killing it close to the "
			+ "boss makes the 3x3 explosion damage the boss (or its shield) instead of you. Ranged and magic "
			+ "projectiles need a little travel time, so 2 is a good starting point",
		position = 1,
		section = larvaeSection
	)
	@Range(min = 1, max = 8)
	default int larvaKillDistance()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "neutralLarvaStyle",
		name = "Neutral larva style",
		description = "Preferred style for a beige larva (delve 1-3) when it is not praying protection. If it "
			+ "protects this style, another configured style is used instead",
		position = 2,
		section = larvaeSection
	)
	default DelveCombatStyle neutralLarvaStyle()
	{
		return DelveCombatStyle.RANGE;
	}

	@ConfigItem(
		keyName = "larvaRangedWeapon",
		name = "Ranged weapon",
		description = "Weapon used for ranged larvae (green). The Scorching bow is demonbane and one-shots a larva",
		position = 3,
		section = larvaeSection
	)
	default String larvaRangedWeapon()
	{
		return "Scorching bow";
	}

	@ConfigItem(
		keyName = "larvaMagicWeapon",
		name = "Magic weapon",
		description = "Weapon used for magic larvae (blue). A powered staff such as the trident auto-casts when "
			+ "you click the larva. Ignored when a demonbane spell is configured below",
		position = 4,
		section = larvaeSection
	)
	default String larvaMagicWeapon()
	{
		return "Trident of the swamp";
	}

	@ConfigItem(
		keyName = "larvaMagicSpell",
		name = "Magic spell",
		description = "Optional demonbane spell to cast on a magic larva. Demonbane spells one-shot a larva without "
			+ "the normal one-tick attack delay and need the Arceuus spellbook and runes",
		position = 5,
		section = larvaeSection
	)
	default DelveLarvaSpell larvaMagicSpell()
	{
		return DelveLarvaSpell.NONE;
	}

	@ConfigItem(
		keyName = "larvaMeleeWeapon",
		name = "Melee weapon",
		description = "Weapon used for melee larvae (red, shield phase) and for neutral larvae that protect ranged "
			+ "and magic. Walk into reach before attacking. A demonbane weapon such as Emberlight one-shots",
		position = 6,
		section = larvaeSection
	)
	default String larvaMeleeWeapon()
	{
		return "Emberlight";
	}

	@ConfigItem(
		keyName = "useThralls",
		name = "Use thralls",
		description = "Keep an Arceuus thrall summoned for the whole fight. Requires the Arceuus spellbook, "
			+ "the Book of the Dead, and the resurrection runes",
		position = 0,
		section = arceuusSection
	)
	default boolean useThralls()
	{
		return false;
	}

	@ConfigItem(
		keyName = "thrallChoice",
		name = "Thrall",
		description = "Thrall to summon. Auto picks the highest tier you can cast. A specific choice falls back "
			+ "to the best castable thrall of the same type if you are missing its runes or level",
		position = 1,
		section = arceuusSection
	)
	default DelveThrallChoice thrallChoice()
	{
		return DelveThrallChoice.AUTO;
	}

	@ConfigItem(
		keyName = "useDeathCharge",
		name = "Use Death Charge",
		description = "Cast Death Charge when it is neither active nor on cooldown. The next kill restores "
			+ "prayer points and special attack energy",
		position = 2,
		section = arceuusSection
	)
	default boolean useDeathCharge()
	{
		return false;
	}

	@ConfigItem(
		keyName = "maxLevel",
		name = "Max delve level",
		description = "Stop (or stop descending) once this many floors have been cleared",
		position = 0,
		section = progressionSection
	)
	@Range(min = 1, max = 8)
	default int maxLevel()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "autoDescend",
		name = "Auto descend",
		description = "Walk to the descend hole after each kill to move to the next floor",
		position = 1,
		section = progressionSection
	)
	default boolean autoDescend()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pauseAfterFight",
		name = "Pause after each fight",
		description = "Stop before descending after every kill so you can choose to take the rewards or walk to the hole yourself. "
			+ "The script resumes automatically once the next floor's boss appears, or when this option is turned off",
		position = 2,
		section = progressionSection
	)
	default boolean pauseAfterFight()
	{
		return false;
	}

	@ConfigItem(
		keyName = "stopAfterMaxLevel",
		name = "Stop after max level",
		description = "Shut the script down after clearing the configured number of floors",
		position = 3,
		section = progressionSection
	)
	default boolean stopAfterMaxLevel()
	{
		return true;
	}
}
