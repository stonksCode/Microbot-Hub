# Tormented Demons Plugin

Automates Tormented Demon kills, including fire bomb dodging, punish and special attack phases, prayer flicking, gear switching, restocking, and looting.

## Credits and lineage

- Original plugin: **zerozero's Tormented Demons** v1.2.3, part of the Microbot Hub (BSD 2-Clause, © 2025 chsami). The banking, restocking, potion, loot, and retreat logic is carried over from that version.
- This fork is maintained by **fyrestarter11** (Discord: `fyrestarter11`, in-game tag `FYRE`). It adds the fire bomb tracking and dodge system, the punish phase, the special attack phase, per-prayer punish/spec gear, and the post-dodge combat walk guard.
- The original author is not responsible for this version. Report problems with this build to `fyrestarter11`, not to zerozero.
- Redistribution must keep the Microbot Hub `LICENSE` (BSD 2-Clause) with the source and with any distributed jar.

## Modes

- **Full Auto**: drinks at the Ferox Enclave pool, banks and loads the `tormented` inventory setup, travels to the demons, fights, loots, and retreats when supplies or health run low.
- **Combat Only**: start the plugin near the demons. Prayer flicking, gear switching, magic utility, and looting still run, but banking and travel are skipped. Because it cannot restock, it stops itself when health or prayer drops to the configured threshold with nothing left to restore it.

## Full Auto requirements

- An Inventory Setup named `tormented` containing the desired gear, food, potions, and teleports.
- Guthixian temple teleports for travel to the demons.
- Ring of dueling for retreating to Ferox Enclave.

## Supply safety

- **Full Auto**: reaching **Health Threshold to Exit** (or running out of food/prayer doses) retreats to Ferox Enclave and restocks from the `tormented` setup. Before withdrawing, the bank is checked: if it has no food, or no prayer-restoring potion while defensive/offensive prayer is enabled, the plugin stops with a console message instead of making another trip.
- **Combat Only**: there is no banking, so the plugin stops instead. It stops when health falls to the exit threshold with no food left, when health reaches **Minimum Health Percent** with no food left, and when prayer reaches **Minimum Prayer Percent** with no prayer doses left.
- The checks run on every combat tick, including right after a kill and while no demon is targeted, so the bot cannot idle at low health waiting for a target.
- **Emergency Teleport on Stop** (on by default): when a stop happens while the player is still in combat, the plugin teleports out before disabling itself instead of leaving the player in the cave with nothing to heal. Available methods: **Ring of Dueling** (Ferox Enclave, bank + pool), **Amulet of Glory** (Edgeville bank), **Ring of Wealth** (Grand Exchange), **Skills Necklace** (Fishing Guild), **Combat Bracelet** (Warriors' Guild), **Royal seed pod** (Tree Gnome Stronghold), **Varrock/Lumbridge/Falador/Camelot/Ardougne/Watchtower teleport tabs**, **Teleport to house tab**, **Guthixian temple teleport**, or **None**. Jewellery in the inventory is equipped automatically; tabs are broken from the inventory. If the chosen method is missing or fails, the plugin works down the remaining bank-adjacent teleports and finally the Guthixian temple scroll, then waits up to 8 seconds for combat to end before shutting down. Offensive prayer is dropped for the escape; protection prayer stays up until shutdown.
- Prayer supply detection covers every potion the drink routine accepts: prayer potions, super restores, Sanfew serums, Moonlight potions, Egniol potions, Tears of Elidinis, blighted super restores, and prayer regeneration potions. Dose suffixes are ignored, so `Prayer potion(1)` still counts as a dose.
- The setup loader accepts a partial restock when the bank is short, so the plugin verifies the loaded inventory after banking. If no food or prayer doses were actually withdrawn, it stops with the missing supply named in the message.
- Disable **Stop When Out of Supplies** to keep the old behaviour: Combat Only never stops itself and Full Auto never stops at the bank.


## Gear switching

- Configure comma-separated gear lists for melee, range, and magic.
- Auto gear switching only uses styles that are enabled **and** have a non-empty gear list. Blank lists are ignored.
- The plugin counters the demon's current protection prayer and prefers keeping the currently equipped style when it is a valid counter.
- The plugin also reads the weapon's current attack style, so hybrid weapons such as the Blue Moon spear are detected as melee or magic based on the selected combat style.
- **Verify Correct Weapon** periodically checks that the equipped weapon and gear counter the demon's current protection prayer. It retries a failed or partial gear switch even when the prayer icon has not changed.
- If two styles use the same gear list, equipping gear cannot change the style. For the Blue Moon spear, the plugin switches between its melee and magic attack styles automatically.
- Use **Copy Current Gear** to copy the names of your currently equipped items to the clipboard as a comma-separated list (for example `Helm of neitiznot,Amulet of torture,Torva platebody,...`). The list is also printed to the Microbot console. The plugin must be enabled while you click the button.
- Gear switches are batched: all missing slots are clicked back-to-back and then verified once, instead of pausing between every item. Multi-slot switches log a `[TD gear]` line with how long the switch took.
- **Gear Switch Mode** controls how those clicks are sent:
  - **Normal (mouse clicks)** - the default simulated mouse click per slot.
  - **Quick clicks** - the same mouse movement, but with a much shorter press and settle time, so a multi-slot switch is roughly twice as fast while still looking human. This is the default.
  - **Instant (one tick)** - every equip is queued in a single game tick with no mouse movement, the same as the action-bar style plugins. Fastest, but the most robotic, so use it only if you accept that trade-off.

## Punish and special attack gear

- **Use Punish Gear** swaps to a dedicated weapon/gear set for the single shield-down hit after a fire bomb. Assign one list per overhead prayer the demon can use: **Punish Gear vs Melee**, **Punish Gear vs Ranged**, and **Punish Gear vs Magic**, so the plugin never attacks into a prayed-against style.
- The punish swap happens only after the fire bombs have exploded and the dodge is over: the fire shield drops when the fire bomb attack lands and stays down until the demon is attacked once, so the first hit you land after stepping off the bombs gets the bonus damage. Your previous gear is restored immediately after that first hit (or after 8 seconds if no hit lands). The shield drop at the very start of a fight is intentionally ignored; only the fire bomb punish is handled.
- **Use Special Attack Gear** swaps to a special-attack weapon while the demon is defenceless (the flames on the model are extinguished) and your special energy is at or above **Special Attack Energy Threshold**. Once energy drops below the threshold the plugin returns to the normal rotation. Enabling this option also enables the special attack orb, so you do not need **Use Special Attack** as well.
- **Special Attack Energy Threshold** is a percentage of the full special attack bar. Specs are used while energy is at or above the value and stop once it drops below it.
- Special attack gear is also chosen by the demon's overhead prayer, exactly like punish gear: **Special Attack Gear vs Melee**, **Special Attack Gear vs Ranged**, and **Special Attack Gear vs Magic**. Leave a prayer's box blank to skip special attacks against that prayer entirely.
- If none of the three prayer-specific special attack boxes are filled in, the single **Special Attack Gear (Fallback)** box is used so older single-set configurations keep working. With per-prayer boxes set, the fallback is ignored.
- Leaving the melee-prayer spec box blank is the safe choice for a Blue Moon spear: the spear's Break Shackles special only works with the full Blue Moon set, so the plugin will not swap to it (or fire the orb) while the demon prays melee unless you actually configure that setup.
- Defenceless starts 30 ticks (18 seconds) after the fight starts and again 18 seconds after each fire bomb attack, and lasts until the next fire bomb. Attacks are 100% accurate in that window, so it is the right time to use special attacks.
- Both phases restore your previous gear when the window ends, the demon dies, or the plugin stops.

## Defensive prayers

- **Enable Defensive Prayer** tracks every Tormented Demon attacking you, not only your current target.
- Melee, magic, and ranged attack animations switch protection immediately.
- After a firebomb style change, the plugin predicts the next style: an approaching demon is treated as melee; otherwise it uses the non-melee style that was not used previously. If the previous style was melee, the prediction is corrected from the next attack animation.
- Prayer changes are invoked off the client thread so they do not block animation processing.
- Protection is only kept active while a Tormented Demon is targeting you. Once the last demon dies, the prayer is switched off during looting and travel, and comes back automatically when the next demon attacks.
- Enable **Debug Prayer Switching** to log `[TD prayer]` lines for animations, predicted/actual styles, prayer requests/retries/confirmations, and incoming hits. `[TD spec]` lines for special-attack energy, threshold, orb state, and activation/release failures are always logged (one line per state change) so spec problems can be diagnosed without any debug toggle.

## Offensive prayers

Enable **Enable Offensive Prayer**, then pick one prayer per combat style in the **Offensive Prayers** section:

- **Auto** uses the best prayer your levels and unlocks allow.
- **None** disables offensive prayer for that style.
- **Melee**: Burst of Strength, Superhuman Strength, Ultimate Strength, Chivalry, Piety.
- **Ranged**: Sharp Eye, Hawk Eye, Eagle Eye, Deadeye (Royal Titans), Rigour.
- **Magic**: Mystic Will, Mystic Lore, Mystic Might, Mystic Vigour (Royal Titans), Augury.

Only one offensive prayer is active at a time; it is switched when the combat style changes. It is only turned on while the target is in attack range with line of sight, so it does not drain while walking to the next demon or looting. A short keep-alive window keeps it on through the firebomb dodge and through single out-of-range ticks; it switches off again once you are no longer fighting. The active state is verified against the game every tick, so a prayer that drops on its own is turned back on within about a second. If the selected prayer cannot be activated, check your Prayer level, quest unlocks, and prayer points.

## Special attacks

- **Use Special Attack** enables the equipped weapon's special attack while fighting when the weapon has enough energy. **Use Special Attack Gear** also enables the orb on its own, so you do not need both. The configured **Special Attack Energy Threshold** is a percentage of the bar.
- The plugin only disables special attack that it enabled itself. A special attack you toggle manually is left alone, including when **Use Special Attack** is off.
- Plugin-managed special attack is released when the weapon changes, energy is insufficient, combat ends, or the plugin stops.
- The Blue Moon spear's Break Shackles is supported when the full Blue Moon armour set is equipped; it costs 50% energy.

## Magic utility (Arceuus)

While the current combat style is magic, the plugin can cast:

- **Mark of Darkness** (refreshed when it is close to expiring or if not currently active).
- The best castable **thrall** (lesser, superior, or greater).

Requirements:

- Arceuus spellbook active.
- Magic level and runes for the spell.
- Book of the Dead in the inventory or equipped for thralls.

Both features are skipped automatically when the requirements are not met. Disable them in the **Magic Utility** config section if desired.

## Looting

- Comma-separated item names are looted from the ground within 10 tiles. Leave empty to disable looting.
- **Loot Delay (ms)** adds a short pause after a kill before looting and targeting the next demon.
- **Scatter Ashes** picks up and scatters Infernal ashes.
- **When Inventory Is Full** and **If That Fails (Fallback)** decide what happens when a loot pickup needs a slot but the inventory has none. The primary option is tried first; if it does not free a slot the fallback is tried; if that also fails the plugin stops with an `Inventory full. Stopping.` message (and uses the emergency teleport if it is still in combat). Options:
  - **Do nothing** - leave the item on the ground and keep fighting. As a primary option it never stops, even if the fallback is something else.
  - **Stop plugin** - stop immediately with the `Inventory full` message.
  - **Eat food for space** - eat one food (even at full health) and pick the item up.
  - **Scatter ashes/bones** - bury one bones item or scatter one ashes item. Bones are used first, then non-infernal ashes, then Infernal ashes.
  - **Demonic Offering** - cast the Arceuus spell if you are on the Arceuus spellbook, have level 84 Magic, the required soul and wrath runes, and are carrying demonic ashes (fiendish, vile, malicious, abyssal, or infernal). One cast frees up to three slots. The option is ignored (the chain moves on) if any requirement is missing or the 5.4 second cooldown has not elapsed.
- Setting both dropdowns to **Do nothing** disables all inventory management: loot the plugin cannot fit is simply left on the ground.
- The loot engine no longer eats food by itself, in either mode; space handling is entirely driven by these two dropdowns.

## Firebomb dodging

- While **Dodge Firebombs** is enabled, the plugin reads each ground-targeted projectile's **landing tile** the moment the demon launches it, so the whole 3x3 blast is known before the bombs even arrive. The `2856` impact graphic is used as a confirmation/fallback.
- Both firebombs from a special attack are tracked, so the plugin does not step onto the second bomb while dodging the first.
- **The player never walks onto a blast tile.** The dodge is a breadth-first search over the live collision map with every blast tile (3x3 per bomb, including bombs still in the air) and every NPC-occupied tile removed. Only the first step of the bomb-free path is clicked, so the walk can never cut through a bomb on the way to a farther safe tile. Safe tiles are chosen purely by distance from the player — the demon's position is not used at all, and the plugin never steps toward the demon to keep it in range.
- The plugin stops moving as soon as the player is clear of every blast and hands control back to combat immediately, so attacks resume as soon as the dodge lands. While the player is still inside a blast it keeps re-clicking the same step every 150 ms, because the demon briefly binds the player and movement clicks made during the bind are ignored. Once the player starts walking it stops clicking, so the path is not restarted and does not detour.
- **Combat cannot walk back through the fire either.** If the player is out of attack range while a bomb is still burning, the attack click is held until the fire goes out. Clicking the demon would otherwise make the client auto-walk into range, and that walk does not know about bomb tiles. Attacking from the current tile (already in range) is never blocked, and the punish window is measured from the bomb impact, so the shield-down hit is still available once the fire is gone.
- If overlapping bombs cover every tile next to the player, the escape may cross fire that has **already landed**, but never a landing zone of a bomb still in the air, and the destination is always outside every blast.
- Safe tiles exclude tiles occupied by NPCs (including a Tormented Demon's 3x3 footprint), not just collision-blocked tiles.
- Enable **Debug Dodging** to log the landing tiles and impact countdown, the chosen step, the player tile, and the NPC-occupied tile count. Each line ends with `after Xms`, the time from bomb detection to that click, which shows how quickly the dodge reacted.
- Fire bombs are tracked for the punish phase even when **Dodge Firebombs** is disabled. The punish window and the defenseless timer both start at the **impact**, not at launch.

## Overlay

The on-screen panel is deliberately narrow and shows one stat per row so nothing spills outside the background:

| Row | Meaning |
|---|---|
| **Status** | Current plugin state: Banking, Retreating, Travelling, or Fighting |
| **Kill count** | Tormented Demons killed since the plugin was started |
| **Avg kill** | Average time from engaging a demon to its death, excluding fights longer than 15 minutes |
| **Last kill** | Duration of the most recent kill |
| **Loot (trip)** | Total Grand Exchange value of items picked up since the current trip began (reset after restocking) |
| **Avg loot / kill** | Loot value of the current trip divided by the number of kills in it |

Values are abbreviated (`12.4k`, `1.2m`) using the same formatter as the rest of RuneLite. A dash means there is nothing to show yet.

### Reset and visibility

The **Overlay** section at the bottom of the plugin settings controls the panel:

- **Reset Statistics** — a button that clears the kill count, average and last kill time, and this trip's loot totals. The overlay updates immediately; a message is printed to the client log.
- **Show Status**, **Show Kill Count**, **Show Average Kill Time**, **Show Last Kill Time**, **Show Loot This Trip**, and **Show Average Loot Per Kill** — untick any stat to hide its row. If every stat is hidden, the panel shows only the title and version.

Statistics also reset on their own when the plugin starts and when a new trip begins after restocking. In Combat Only mode the whole session counts as one trip.

## Troubleshooting

- **Retreat does nothing**: confirm a charged Ring of Dueling is equipped or in the inventory.
- **Banking loop**: confirm the `tormented` inventory setup exists and can be loaded; the plugin stops if loading fails.
- **Dodging**: confirm **Dodge Firebombs** is enabled. If a dodge is still missed, enable **Debug Dodging** and check whether both firebombs and a safe tile are logged.
- **No gear switching**: fill in the gear lists for the styles you want to use and keep **Verify Correct Weapon** enabled.
- **Magic not detected**: if melee and magic use the same gear, enable **Verify Correct Weapon** so the plugin can switch a Blue Moon spear between melee and magic. Other hybrid weapons may still require manual style changes.
- **Special attack not used**: confirm either **Use Special Attack** or **Use Special Attack Gear** is enabled and the required energy is available. Check the `[TD spec]` lines in the client log: they are always printed and report the energy, threshold, weapon, orb state, and activation failures. If the demon is not defenceless yet, the spec gear is intentionally not equipped. If you enable the orb manually, the plugin leaves it alone when neither option is on.
- **Wrong defensive prayer**: enable **Debug Prayer Switching** and check the `[TD prayer]` animation/style, request, confirmation, and hitsplat lines in the client log. The prediction after a melee style change is provisional and should be corrected by the following attack animation.
