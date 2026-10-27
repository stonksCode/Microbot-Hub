# Vorkath Plugin

The **Vorkath Plugin** provides an automated way to fight Vorkath.

---

## Feature Overview

| Feature                      | Description                                                           |
|------------------------------|-----------------------------------------------------------------------|
| **Teleport**                 | Choose your mode of Teleport                                          |
| **Price of items to loot**   | Price of items to loot comma seperated                                |
| **Sell items every X kills** | Sell items every X kills                                              |
| **Items to not sell**        | Items to not sell comma seperated                                     |
| **KC per Trip**              | Kills per trip before banking (0 to disable it)                       |
| **POH in Rellekka**          | Teleport to POH in Rellekka and exit via portal                       |
| **Rejuvenation Pool**        | Use POH rejuvenation Pool to restore stats                            |
| **Rigour**                   | Activate Rigour? (Make sure you have it unlocked and have 74 prayer!) |
| **Use Slayer's staff**       | Kill the Zombified Spawn by switching to a Slayer's staff (Crumble Undead autocast preset) instead of casting from the spellbook |

---

## Requirements
- Microbot RuneLite client
- Requirements to fight Vorkath
- Plugin enabled in the Microbot plugin list

---

## How It Works
1. Use the equipment inventory plugin to setup your plugin and give it the name 'vorkath'.
2. Make sure to start at a bank.
3. Activate the plugin.
4. Enjoy! :)

### Slayer's staff
When **Use Slayer's staff** is enabled, the player must keep a Slayer's staff in the inventory setup with
**Crumble Undead already set as its autocast**. Before each fight the plugin verifies the staff is available;
when the Zombified Spawn appears it swaps to the staff and attacks the spawn (autocasting Crumble Undead),
then swaps back to the primary weapon. When disabled, the plugin casts Crumble Undead from the spellbook and
verifies you have the level, spellbook and runes before the fight.

### Potions
Before the fight the plugin drinks only a ranging potion and an extended super antifire. Anti-venom is **not**
drunk pre-emptively; it is used on demand mid-fight the moment Vorkath's venom hitsplat lands, so a single dose
lasts the whole kill.

### Bolts
The fight starts and continues on your primary bolts (e.g. ruby bolts (e)) and automatically switches to
diamond bolts (e) once Vorkath drops below 30% health, then back to the primary bolts above 30%.

### Debug
Enable **Verbose logging** under the Debug section to print state transitions and special-attack handling
(poke, red ball sidestep, acid woox-walk steps, venom cure, bolt swaps) to the Microbot log.

---

## Limitations
- No Melee Support

---

## Disclaimer & Waiver of Liability

**The Plugin is provided for educational purposes only.** By using this Plugin, you acknowledge and agree to the following:

### 1. Educational Use Only
- The Plugin is intended solely for research, learning, and understanding botting mechanics.
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
- Automation tools violate [Jagex’s Terms of Service](https://www.jagex.com/en-GB/terms).
- Use may result in **permanent account bans**. This Plugin does **not** endorse rule-breaking.

### 6. No Affiliation
- This Plugin is **not affiliated with** Jagex Ltd. or *Old School RuneScape*.

**By using this Plugin, you confirm you have read and agreed to this disclaimer. If you disagree, cease use immediately.**

---

## Feedback
Open an issue or feel free to contribute improvements.

