# Auto Kraken

An in-room automation plugin for the **Kraken** boss (level 291). It is intended to be started
while already inside Kraken Cove, on a cave kraken / Kraken Slayer task.

The main difference from the existing Kraken plugin is the opener: instead of manually disturbing
the four small whirlpools, this plugin uses a **fishing explosive** on the large whirlpool to wake
the Kraken instantly.

## Features

- **Slayer-task gate** - optionally stops if you are not assigned cave krakens / Kraken.
- **Fishing-explosive opener** - uses a fishing explosive on the large whirlpool automatically.
- **Disturb fallback** - if enabled and no explosives are left, disturbs the four small whirlpools
  and then the main whirlpool instead.
- **Boss-alive detection** - if the Kraken is already awake it goes straight to combat; it only
  uses an explosive when the main whirlpool is present.
- **Auto combat** - re-attacks the Kraken when not interacting, eats at a configurable HP
  percentage, and optionally keeps quick prayers on.
- **Auto looting** - always loots Kraken uniques (tentacle, trident, jar, pet, Brimstone key,
  clue/scroll box), coin drops, any extra item names you configure, and optionally everything above
  a minimum GE value.
- **Stop conditions** - out of food (between kills), inventory full, death, task completion, and
  off-task chat messages.

## Requirements

- Level 87 Slayer and 50 Magic.
- A cave kraken / Kraken Slayer task (unless the task gate is disabled).
- Magic gear and runes/charged trident.
- Food in the inventory.
- Fishing explosives in the inventory (or `Fall back to disturbing` enabled).
- Start the plugin **inside the Kraken boss room**. Travelling to Kraken Cove and banking are not
  handled by this version.

## Configuration

| Setting | Description |
|---------|-------------|
| Require Kraken task | Stop when the current Slayer task is not a kraken task. |
| Use fishing explosive | Use a fishing explosive on the large whirlpool. |
| Fall back to disturbing | Disturb the whirlpools when no explosives remain. |
| Eat at % HP | Eat food at or below this health percentage during the fight. |
| Use quick prayers | Re-enable quick prayers while fighting. Configure them before starting. |
| Stop when out of food | Stop between kills when no edible food remains. |
| Stop on death | Stop when the death message appears. |
| Extra loot names | Comma-separated item names to always loot. |
| Also loot by GE value | Loot items above the configured GE value. |
| Min GE value | Minimum GE value for the value-based loot pass. |
| Loot coins | Always pick up coins. |

## Notes

- Kraken's attacks are typeless magic, so Protect from Magic does **not** reduce its damage.
  Quick prayers are therefore optional; use offensive prayers if you configure them.
- The plugin does not teleport away when out of food. It stops between kills so you can restock.
- The four Enormous Tentacles are not targeted; the plugin focuses the Kraken itself.

## Disclaimer

By using this plugin you accept all risks associated with automation, including account
penalties. Use at your own discretion.
