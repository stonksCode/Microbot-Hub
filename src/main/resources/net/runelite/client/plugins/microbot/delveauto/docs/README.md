# Delve Auto Plugin

Full automation for the **Doom of Mokhaiotl** (Delve), built in stages starting with levels 1-3.

> **Do not run this alongside Delve Prayer Helper.** Both plugins control your overhead prayers and
> will fight each other. Disable the prayer helper first.

---

## Current Status

This is the M0-M4c build. The latest version adds demonic larva handling with style-aware weapon
swaps and shield-phase explosions:

| Milestone | Scope | Status |
|-----------|-------|--------|
| **M0** | Plugin skeleton, config, overlay, prayer service, hazard tracker, 50 ms arbiter | Done |
| **M1** | Level 1: prayer swaps, rock-throw dodge, volatile earth / earthen shield, eat + prayer pot, attack re-arm, auto-descend | Static fixes done, live testing pending |
| **M2** | Level 2: melee projectile, double rock volley | Not yet |
| **M3** | Level 3: acid avoidance, demonic shield phase, two-slam hold | Partial (acid tiles tracked) |
| **M4a** | Beam melee punish: weapon swap, optional special attack, post-fight pause | Done, live testing pending |
| **M4b** | Arceuus utility: thrall upkeep with configurable thrall, Death Charge | Done, live testing pending |
| **M4c** | Larva style swaps and shield-phase explosions | Done, live testing pending |
| **M4d** | Banking, restock | Not yet |

---

## Feature Overview

| Feature | Description |
|---------|-------------|
| **Priority arbiter** | Prayers run every tick, then eat/pot, rock dodge, shockwave, beam, acid, larvae, utility, DPS |
| **Protection re-assert** | Non-blocking prayer clicks with a 2-tick retry; re-checks the correct protection every tick while a projectile is in flight, covering Rock Throw's overhead clear |
| **Rock Throw dodge** | Steps off the marked tile in the last two ticks of the 7-tick fuse, moving to the farthest reachable safe tile |
| **Volatile earth** | Destroys two earths (first = shield destination, second = spawn point), then follows the moving earthen shield |
| **Beam melee punish** | During the Special Beam Cannon charge, swaps to the configured melee weapon, moves into reach (2 tiles for a halberd), optionally enables the special attack, hits once, then restores the bow. The beam is never tanked by default |
| **Demonic larvae** | Waits until a larva is near the boss, reads the required style (colour or the neutral larva's overhead prayer), swaps to the configured ranged/magic/melee weapon, and kills it so the 3x3 explosion damages the boss or its shield instead of the player. A demonbane spell can replace the magic weapon attack |
| **Post-fight pause** | Optionally stops before descending after a kill so you can take rewards; resumes automatically once the next floor's boss appears |
| **Arceuus utility** | Optional thrall upkeep (choose the exact thrall, or Auto for the highest castable tier) and Death Charge, refreshed whenever the buff is neither active nor cooling down. Runs after every defensive mechanic and before the next attack |
| **Auto descend** | Walks to either burrow-hole id after each kill and confirms the unique hole dialog, up to the configured number of floors |
| **Form-change grace** | A short boss-disappearance grace period stops shield/burrow transitions being mistaken for a kill |
| **Prayer potions / food** | Uses the standard Microbot potion and food helpers at configurable thresholds; the food threshold rises while the beam is charging |

---

## Requirements

- Microbot RuneLite client **2.6.52+**
- A ranged setup. The current build assumes a **Scorching bow** so the level 3 shield phase is broken
  by normal demonbane attacks and larvae are one-shot.
- A melee punish weapon in the inventory. A **crystal halberd** is the default: 2-tile reach, a strong
  special attack, and a melee kill during the charge heals you and restores prayer.
- Larva weapons in the inventory: a ranged weapon (**Scorching bow** by default), a magic weapon
  (**Trident of the swamp** by default, or an optional demonbane spell with the Arceuus spellbook and
  runes), and a melee weapon (**Emberlight** by default). Larvae are only attacked with styles whose
  weapon is actually worn or carried.
- Food and prayer potions in the inventory.
- Optional Arceuus utility: the **Arceuus spellbook** plus the resurrection runes, level and a **Book of
  the Dead** (inventory or equipped) for thralls. Death Charge additionally needs blood, death and soul
  runes and level 80 Magic. The utility is skipped with a one-time chat warning on any other spellbook.

---

## Configuration

- **Prayer** - offensive prayer, re-assert protection.
- **Combat** - eat percentage, beam-charging eat percentage, prayer pot threshold, preferred distance.
- **Mechanics** - dodge rock throw, handle shockwave, avoid acid.
- **Beam punish** - punish weapon name, reach override (0 = auto: 2 for halberds, 1 otherwise),
  use-special checkbox, and the special energy cost as a percentage (crystal halberd = 30).
- **Larvae** - enable larva handling, kill distance from the boss, preferred style for neutral larvae,
  and the ranged / magic / melee larva weapons plus an optional demonbane spell.
- **Arceuus** - use thralls, thrall dropdown (Auto = highest castable tier; a specific choice falls back to
  the best castable thrall of the same type), use Death Charge.
- **Progression** - max delve level, auto descend, pause after each fight, stop after max level.

---

## Known Limitations

- **Beam punish needs the weapon available.** If the configured melee weapon is neither equipped nor
  in the inventory, the bot cannot interrupt the charge; it falls back to normal combat and the raised
  eat threshold. Keep the punish weapon in the inventory at all times.
- **Punish timing is untested live.** The swap, optional special and hit all have to complete inside the
  charge window. Watch the first few beams; if the special costs more than one tick to arm, disable
  the special or use a faster weapon.
- **Rock shadow spotanim IDs are unverified.** Dodging currently uses the 7-tick fuse and moves to
  the farthest reachable safe tile rather than tracking a verified shadow ID, so very wide debris
  fields may still clip the player.
- **Shield timing at level 1** is the riskiest part: the shield moves at half speed, so if the two
  earths are far apart the bot may reach the destination late. The tracker retains the destroyed
  pair across the brief gap before the shield NPC spawns.
- **Larva timing is untested live.** The kill distance is a trade-off: starting too early can make the
  3x3 explosion miss the boss and hurt you, while waiting too long lets the larva reach the boss.
  Ranged and magic projectiles need travel time, so 2 tiles is a starting point; watch the first few
  waves and adjust.
- **Neutral larva protection is read from the overhead sprite.** If the prayer icon cannot be read, the
  configured preferred style is used and the larva may block it. Coloured larvae in delve 4+ do not
  depend on overhead detection.
- **Magic larva attacks need a workable setup.** With the spell set to None, the configured magic weapon
  must auto-cast when clicked. With a demonbane spell selected, the Arceuus spellbook and runes are
  required; if the spell is not castable the bot falls back to the magic weapon.
- Deaths are not detected explicitly; a respawn outside the arena is treated like a finished floor.

---

## Disclaimer & Waiver of Liability

**The Plugin is provided for educational purposes only.** By using this Plugin, you acknowledge and agree to the following:

### 1. Educational Use Only
- The Plugin is intended solely for research, learning, and understanding of botting mechanics.
- It is **not** meant for active use in *Old School RuneScape (OSRS)* or any live game environment.

### 2. No Warranty
- The Plugin is provided **"as-is"** without warranties of any kind.
- Developers disclaim all liability for damages, losses, or penalties incurred through its use.

### 3. User Responsibility
- You assume **all risks** (e.g., account bans, progress loss) from using this Plugin.
- Any penalties imposed by Jagex or third parties are **your sole responsibility**.

### 4. Waiver of Claims
- You **waive all rights** to hold developers/contributors liable for any damages (direct, indirect, or consequential).

### 5. Compliance with Jagex Terms
- Automation tools violate [Jagex's Terms of Service](https://www.jagex.com/en-GB/terms).
- Use may result in **permanent account bans**. This Plugin does **not** endorse rule-breaking.

### 6. No Affiliation
- This Plugin is **not affiliated with** Jagex Ltd. or *Old School RuneScape*.

**By using this Plugin, you confirm you have read and agreed to this disclaimer. If you disagree, cease use immediately.**
