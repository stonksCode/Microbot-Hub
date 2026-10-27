# Tormented Demons Plugin

Automates Tormented Demon kills, including prayer flicking, gear switching, restocking, and looting.

## Modes

- **Full Auto**: drinks at the Ferox Enclave pool, banks and loads the `tormented` inventory setup, travels to the demons, fights, loots, and retreats when supplies or health run low.
- **Combat Only**: start the plugin near the demons. Prayer flicking, gear switching, magic utility, and looting still run, but banking and travel are skipped.

## Full Auto requirements

- An Inventory Setup named `tormented` containing the desired gear, food, potions, and teleports.
- Guthixian temple teleports for travel to the demons.
- Ring of dueling for retreating to Ferox Enclave.

## Gear switching

- Configure comma-separated gear lists for melee, range, and magic.
- Auto gear switching only uses styles that are enabled **and** have a non-empty gear list. Blank lists are ignored.
- The plugin counters the demon's current protection prayer and prefers keeping the currently equipped style when it is a valid counter.
- The plugin also reads the weapon's current attack style, so hybrid weapons such as the Blue Moon spear are detected as melee or magic based on the selected combat style.
- **Verify Correct Weapon** periodically checks that the equipped weapon and gear counter the demon's current protection prayer. It retries a failed or partial gear switch even when the prayer icon has not changed.
- If two styles use the same gear list, equipping gear cannot change the style. For the Blue Moon spear, the plugin switches between its melee and magic attack styles automatically.
- Use **Copy Gear Setup** to copy the currently equipped gear to the clipboard.

## Defensive prayers

- **Enable Defensive Prayer** tracks every Tormented Demon attacking you, not only your current target.
- Melee, magic, and ranged attack animations switch protection immediately.
- After a firebomb style change, the plugin predicts the next style: an approaching demon is treated as melee; otherwise it uses the non-melee style that was not used previously. If the previous style was melee, the prediction is corrected from the next attack animation.
- Prayer changes are invoked off the client thread so they do not block animation processing.
- Enable **Debug Prayer Switching** to log `[TD prayer]` lines for animations, predicted/actual styles, prayer requests/retries/confirmations, and incoming hits. It also logs `[TD spec]` lines for special-attack energy, orb state, and activation/release failures.

## Offensive prayers

Enable **Enable Offensive Prayer**, then pick one prayer per combat style in the **Offensive Prayers** section:

- **Auto** uses the best prayer your levels and unlocks allow.
- **None** disables offensive prayer for that style.
- **Melee**: Burst of Strength, Superhuman Strength, Ultimate Strength, Chivalry, Piety.
- **Ranged**: Sharp Eye, Hawk Eye, Eagle Eye, Deadeye (Royal Titans), Rigour.
- **Magic**: Mystic Will, Mystic Lore, Mystic Might, Mystic Vigour (Royal Titans), Augury.

Only one offensive prayer is active at a time; the plugin switches it when the combat style changes. If the selected prayer cannot be activated, check your Prayer level, quest unlocks, and prayer points.

## Special attacks

- **Use Special Attack** enables the equipped weapon's special attack while fighting when the weapon is recognised and has enough energy.
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

## Firebomb dodging

- While **Dodge Firebombs** is enabled, the plugin tracks both the ground-targeted projectiles and the `2856` impact graphics for a special attack, then moves off their impact tiles.
- Both firebombs from a special attack are tracked, so the plugin does not step onto the second bomb while dodging the first.
- Safe tiles exclude tiles occupied by NPCs (including a Tormented Demon's 3x3 footprint), not just collision-blocked tiles.
- Enable **Debug Dodging** to log impact tiles, the player tile, NPC-occupied tile count, and the chosen safe tile to the client log when diagnosing a missed dodge.

## Troubleshooting

- **Retreat does nothing**: confirm a charged Ring of Dueling is equipped or in the inventory.
- **Banking loop**: confirm the `tormented` inventory setup exists and can be loaded; the plugin stops if loading fails.
- **Dodging**: confirm **Dodge Firebombs** is enabled. If a dodge is still missed, enable **Debug Dodging** and check whether both firebombs and a safe tile are logged.
- **No gear switching**: fill in the gear lists for the styles you want to use and keep **Verify Correct Weapon** enabled.
- **Magic not detected**: if melee and magic use the same gear, enable **Verify Correct Weapon** so the plugin can switch a Blue Moon spear between melee and magic. Other hybrid weapons may still require manual style changes.
- **Special attack not used**: confirm **Use Special Attack** is enabled, the weapon has a recognised special attack, and the required energy is available. Enable **Debug Prayer Switching** and check the `[TD spec]` lines for the reported energy, required energy, weapon, orb state, and activation failures. If you enable the orb manually, the plugin leaves it alone when **Use Special Attack** is off.
- **Wrong defensive prayer**: enable **Debug Prayer Switching** and check the `[TD prayer]` animation/style, request, confirmation, and hitsplat lines in the client log. The prediction after a melee style change is provisional and should be corrected by the following attack animation.
