# Delve Prayer Helper Plugin

![preview](assets/delveprayerhelper_example.gif)

The **Delve Prayer Helper Plugin** automatically prays against the attacks of the Delve boss.

---

## Feature Overview

| Feature                                 | Description                                                                        |
|-----------------------------------------|------------------------------------------------------------------------------------|
| **Offensive Prayer**                    | Enable to use offensive ranged prayer (choose specific prayer or best available) |
| **No Offensive Prayer in Shield Phase** | Toggle off offensive prayer during the shield phase to save prayer points        |
| **Thrall Recasting**                     | Automatically recast your selected thrall when none is active (Arceuus spellbook) |
| **Death Charge Recasting**               | Automatically recast Death Charge when off cooldown and not active                |
| **Smart Prayer Switching**               | Protection switches are sent straight to the game on the client thread, so a switch lands the instant the previous hit does |
| **Landing Delay**                        | Optional delay after hits land; default 0 switches the instant the hit lands     |
| **Rock Throw Dodge**                     | Steps off the marked tile during the last few ticks of the 7-tick rock fuse       |
| **Acid Avoidance**                       | Steps off acid pools and avoids them when picking a dodge tile                    |
| **Earthen Shield Follow**                | Walks with the moving earth sphere during the volatile earth slam                 |
| **Auto-select Totems**                   | Destroys the two farthest volatile earth totems for the longest path (on by default) |
| **Auto Consumables**                     | Drinks a prayer potion below a prayer threshold and eats food below an HP threshold |
| **Eating / Prayer Restore Toggles**      | Master switches that make it impossible for food or potions to interrupt a switch |
| **Fight Log**                            | Writes a detailed per-fight log file that can be shared for review                 |

---

## Requirements
- Microbot RuneLite client
- Plugin enabled in the Microbot plugin list

---

## How It Works

1. Activate the plugin.
2. Fight the boss.
3. Enjoy! :)

Protection prayer always takes priority: movement, offensive prayer, consumables and thrall/Death
Charge recasts are skipped while a protection switch is still unconfirmed or an attack is about to
land. Prayer decisions run on the client thread and are sent straight to the game, so nothing can sit
in front of a switch — not even a potion, a thrall cast or the mouse input pipeline.

---

## Fight Log

Enable **Write fight log** (on by default) to capture a complete rundown of each fight. A new file is
created every time the plugin starts:

```
~/.runelite/logs/delve-prayer-helper/fight-<yyyyMMdd-HHmmss>.log
```

On Windows that is:

```
C:\Users\<you>\.runelite\logs\delve-prayer-helper\fight-<yyyyMMdd-HHmmss>.log
```

The log contains:

- every projectile, including its id and whether it maps to a known attack (**UNKNOWN** ids are logged too, which is how new attacks in deeper delves get identified)
- every protection prayer decision (direct click, confirmation, retry hold, external clear from a rock shatter, drop)
- hitsplats with the active protection prayer and current HP
- boss and player animations, NPC spawns/despawns, graphics objects and nearby chat
- rock fuse timing and acid tile changes
- the volatile earth totems, the moving earthen shield and the shield destination/spawn pair
- optional **Verbose tick log**: one line per game tick with HP, prayer points, active/wanted prayer, boss animation, hazard state and position

To report a missed switch, capture the fight, then share the newest `fight-*.log` file. The overlay
shows the file name of the current session.

---

## Survival

- **Dodge Rock Throw** steps off the tile marked by the rock during the last **Dodge at (ticks before
  impact)** ticks (default 2). Moving earlier can put you back on the marked tile.
- **Avoid acid pools** steps off acid blood and treats acid tiles as unsafe when choosing a dodge tile.
- **Enable prayer restoration** is the master switch for prayer potions. Turn it off to guarantee that
  a potion is never drunk during a prayer switch (you then manage prayer points yourself).
- **Prayer pot at (points)** drinks a prayer potion below the threshold. It also drinks an emergency
  potion if a protection prayer cannot be activated because prayer points ran out.
- **Enable eating** is the master switch for food. Turn it off to guarantee that eating never races a
  prayer switch (you then eat manually).
- **Eat at (%)** eats food below the configured health percentage.

Set **Eat at** to `0` to disable eating by threshold. Set **Prayer pot at** to `0` to disable threshold
drinking; the emergency drink when prayer runs out still happens while prayer restoration is enabled.
Turning off **Enable eating** or **Enable prayer restoration** disables that action entirely.

---

## Earth shield phase

During the slam the boss covers the arena with **volatile earth** totems and rolls an **earthen
shield** (the earth sphere) across the room. The shield blocks the boss's attacks while you are
standing with it, so the plugin walks the player with it while it is visible. Protection prayers keep
running during the phase: the boss still attacks, and those attacks land the moment the shield
disappears.

- **Follow earthen shield** (on by default) follows the shield's current tile as it moves. This is
  movement only and still runs behind protection prayers: it is skipped while a switch is
  unconfirmed or an attack is about to land.
- **Auto-select totems** (on by default) destroys the two totems that are farthest apart, giving the
  shield the longest path. The **first** totem destroyed becomes the shield's destination and the
  **second** is where it spawns. The totem nearest to you is destroyed first, so the far one (the
  spawn) is destroyed second and the shield appears next to you, ready to follow. With the option
  off you pick the two totems yourself; the plugin still follows the shield and waits on the
  destination once both are down.

Because the shield's path is decided by the two destroyed totems, the plugin waits on the first
(destination) tile after the second totem dies so the shield rolls toward the player instead of away
from them.

Only the two totems of the current slam are attacked. As soon as the pair is down (or the shield is
visible) the plugin stops attacking totems and follows the shield, so it never runs off to a third one.

The arena is instanced, so every position (player, totems, shield, acid) is converted to the
instance's own scene coordinates before moving. This is what keeps the plugin on the shield instead
of walking to an unrelated spot in the room.

---

## Limitations
- Protection switching relies on the prayer tab widgets being loaded. If the prayer book has never been
  opened this session, the direct click is rejected and the plugin falls back to a normal mouse click.
- Prayer switching is fast, but the game still processes a toggle on its own tick. Two different attacks
  landing on the same tick cannot both be protected against.
- Movement and other mouse actions are still simulated, so your own mouse input can briefly interrupt
  them. Protection switches are unaffected because they no longer use the mouse.
- The fight log is written to disk on a background thread; a very long fight produces a large file.

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

