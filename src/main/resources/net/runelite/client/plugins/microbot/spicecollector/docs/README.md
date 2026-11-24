# Spice Collector

Automatically collects 4-dose spices from the **Hell-Rat Behemoths** in Evil Dave's Basement of
Doom. The plugin walks to the alcove curtain for the selected spice, sends your cat in to fight,
heals the cat with the configured food, and repeats until it reaches the requested amount or you
stop it.

## Features

- **Colour selection** - choose red, orange, yellow, or brown spice. The plugin finds the matching
  curtain dynamically, so it does not rely on hard-coded curtain IDs.
- **All cat stages** - works with a kitten, normal cat, overgrown cat, lazy cat, wily cat, and
  their hellcat variants.
- **Curtain dialogue handling** - automatically clicks through the "too big to enter" message and
  chooses **Insert your cat**, then waits for the instanced fight to begin. The behemoths that roam
  the ordinary basement are not mistaken for the start of the fight.
- **Automatic feeding** - uses the configured food directly on the cat while it fights. If direct
  feeding fails, it falls back to using the food on the alcove curtain.
- **Health-aware feeding** - checks the cat's health bar and only offers food below the configured
  percentage, saving food. If the health bar is unavailable, it offers food every feed interval.
- **Target amount** - stop after collecting X spices, or set X to `0` to collect endlessly.
- **Drop recovery** - if a spice is dropped because the inventory was full, it attempts to pick it
  up once a slot is available.
- **Stop conditions** - target reached, out of cat food, inventory full, cat lost, or repeated
  fights without a spice reward.
- **Optional debug trace** - records state changes, curtain discovery, dialogue checks, clicks, key
  presses, feeding, spice counts and stop reasons to a timestamped file.

## Requirements

- Completed the **Freeing Evil Dave** subquest of Recipe for Disaster.
- A cat or kitten (following you or in your inventory).
- Cat food in the inventory. **Raw karambwanji** is recommended because it stacks in one slot.
- Start anywhere. The plugin will walk toward the Basement of Doom and the selected curtain. If
  your account cannot route there automatically, start inside the basement.

## Configuration

| Setting | Description |
|---------|-------------|
| Spice to collect | Red, orange, yellow, or brown. Each colour has its own behemoth alcove. |
| Spices to collect | Stop after this many spices. `0` means collect endlessly. |
| Cat food | Exact item name used to heal the cat. Defaults to `Raw karambwanji`. |
| Feed interval (ms) | How often food may be offered during a fight. |
| Feed at % health | Only feed when the cat's health bar is at or below this percentage. |
| Stop when out of food | Stop before the next fight if no configured food remains. |
| Debug trace | Write a detailed trace to the RuneLite log and to `~/.runelite/spice-collector/debug-<time>.log`. Turn this on when reporting a problem. |

## Notes

- Wily cats are strongly recommended. Kittens and normal cats can be killed quickly by a behemoth,
  and a dead cat is permanently lost.
- The fight is instanced. When the behemoth dies, the player exits automatically and the 4-dose
  spice is awarded. If the inventory is full, the spice drops under the player.
- Walking away from a fight exits the instance with the cat unharmed, but no spice is awarded.
- The script does not bank automatically. Stock up on stacked food before starting a long session.

## Disclaimer

By using this plugin you accept all risks associated with automation, including account
penalties. Use at your own discretion.
