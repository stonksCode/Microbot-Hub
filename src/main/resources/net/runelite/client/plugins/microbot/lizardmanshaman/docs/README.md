# Lizardman Shaman Plugin

Automates fighting **lizardman shamans** in the **Lizardman Temple** beneath Molch (Kebos Lowlands), including the jump attack and the purple exploding spawns.

---

## Features

- Finds and attacks the nearest reachable shaman
- Rotates between three **safe-spot groups** (north-east, south-east, south-west) instead of dodging tile by tile
- Holds a group that no shaman can reach with melee, and switches groups when the shaman walks into range
- Answers **purple spawns** by keeping the fight going for a configurable number of ticks, then moving to a *different* safe-spot group
- Answers a telegraphed **jump** by leaving the exposed north-east group for one of the southern groups, then continuing the fight
- Keeps a configurable overhead prayer and an optional offensive prayer active
- Eats food, drinks prayer potions and antipoison at configurable thresholds
- Overlay panel with state, shaman/spawn counts, current spot, and highlighted danger areas, safe spots and the move target
- Stops automatically after death (configurable)

---

## Requirements

- Microbot RuneLite client
- Plugin enabled in the Microbot plugin list
- Food, prayer potions and (recommended) antipoison in the inventory
- Combat gear and stats for lizardman shamans

The plugin does **not** bank, travel to the temple, or loot. Start it inside the temple with your supplies already in the inventory.

---

## How It Works

1. Start the plugin while standing in the Lizardman Temple.
2. The script scans for shamans and purple spawns within the configured radius.
3. While fighting, it keeps the player inside one of the three safe-spot groups and attacks the nearest reachable shaman.
4. When the shaman can reach the held group, the script walks to the nearest group that is outside melee range.
5. When spawns appear, it waits the configured number of ticks and then moves to a different group.
6. When a jump is telegraphed, it leaves the north-east group for a southern one.
7. Prayers are reapplied and supplies consumed as needed.

### Safe-spot groups

There are three groups, each a small set of tiles. The tiles are **hard-coded** for the Lizardman Temple, since the plugin neither banks nor travels and therefore never needs them to vary:

| Group | Tiles |
| --- | --- |
| North-east | `1295,10100` and `1296,10099` |
| South-east | `1289,10094` and `1290,10093` |
| South-west | `1294,10093` and `1295,10093` |

The groups are checked as a whole: if a shaman is within **2 tiles** of any tile in a group, the whole group counts as unsafe, so the player never half-stands in a group that can be hit. Destinations are chosen from groups outside melee range, nearest first.

A shaman that jumps out of the room can disappear from the NPC cache for a tick. Its last known tiles are kept for about three ticks, so a vanished shaman still counts as a melee threat instead of making every group look safe.

Only the north-east group is exposed to the jump attack. The two southern groups sit against the temple wall and are used whenever a jump is telegraphed.

### Purple spawns

Purple spawns walk towards the player before exploding, so moving immediately just drags them along. The script:

1. Notes which group the player was standing in when the spawns appeared.
2. Keeps attacking for **Spawn move delay (ticks)** game ticks (default 2).
3. Walks to a *different* group that no shaman can melee. The delay is always honoured, even if a spawn's blast area already covers the player, so the spawns commit to the group being abandoned instead of following the player.
4. Holds that group until the spawns and their blast tiles are gone.

While the spawn sequence is active it owns the player's movement, so attacking cannot pull the player back into the blast. The attack is re-issued after the move.

### Jump attacks

Jump detection is animation-id free. The first jump of a session is recognised when a shaman animation that lasts at least 500 ms ends in a 2+ tile teleport; the animation id is then remembered, so later jumps are detected at take-off, which is when the landing tile is decided. You can also enter the jump animation id in **Jump animation ids** for exact detection from the first jump.

When a jump is telegraphed, the script avoids the north-east group and moves to one of the southern groups, then continues fighting.

### Temple vs surface

The temple variant (NPC 8565) uses 2x2 jump and 3x3 spawn areas. Surface shamans are supported with the larger 3x3 jump and 5x5 spawn areas, but the plugin is tuned for the temple.

---

## Settings

| Section | Setting | Description |
| --- | --- | --- |
| General | Stop after death | Stop the script when you die |
| General | Search radius | Maximum distance considered for shamans and spawns |
| Combat | Eat below (min/max) | Randomised health percentage at which to eat |
| Combat | Overhead prayer | None / Protect from Melee / Protect from Missiles |
| Combat | Offensive prayer | None / Piety / Rigour / Augury |
| Combat | Use antipoison | Drink antipoison when poisoned by acid spit |
| Combat | Drink prayer below (min/max) | Randomised prayer percentage at which to drink |
| Mechanics | Avoid jump attacks | Move to a southern group when a jump is telegraphed |
| Mechanics | Move on purple spawns | Wait the configured ticks, then move to a different group |
| Mechanics | Safety margin | Extra tiles added around every danger area |
| Mechanics | Spawn move delay (ticks) | Game ticks to keep fighting after spawns appear before moving (default 2) |
| Mechanics | Jump animation ids | Optional comma-separated animation ids |
| Overlay | Show info panel / danger tiles / move target / safe spots | Toggle the overlay elements |
| Overlay | Danger color / move target color / safe spot color | Tile colors |

---

## Limitations

- **No banking, travel or looting.** The script expects you to be in the temple with supplies.
- Acid spit ignores prayer and poisons you. Wear Shayzien armour if available and keep antipoison enabled.
- Jump detection is heuristic without a known animation id. Supply **Jump animation ids** if you want exact detection.
- The safe-spot tiles are hard-coded for the Lizardman Temple. They do not work on surface shamans or in other locations without changing the source.
- The script does not use the mystic barriers; crossing them repeatedly increases the damage they deal.

---

## Troubleshooting

- **Script does nothing:** make sure you are logged in, inside the temple, and that shamans are within the search radius.
- **Not eating:** check that food is in the inventory and that the minimum threshold is above your current health percentage.
- **Walking into danger:** increase the safety margin, or set the jump animation ids if you know them.
- **Standing still between groups:** if every group is in melee range of a shaman, the script holds its current group instead of running between them; kill the shaman or let it move away.
- **Overlay not visible:** enable **Show info panel** and **Show danger tiles** in the Overlay section.
