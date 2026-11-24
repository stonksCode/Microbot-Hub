# Kitten Tracker

Tracks kitten growth, sends hunger/attention warnings, and can raise a kitten unattended.

## Set-and-forget setup

1. Enable the plugin and get a kitten from Gertrude.
2. Bring raw karambwanji (or a bucket of milk) and a ball of wool.
3. Turn on **Prevent idle logout** if you intend to walk away.

The plugin will feed the kitten when it gets hungry and keep it entertained when it gets
neglected. Once the kitten grows into a cat it no longer needs food or attention, so those
actions stop automatically.

## Options

- **Kitten food** — dropdown of foods kittens accept: raw/cooked fish (including karambwanji,
  sardines, shrimp, trout, salmon, tuna, lobster, shark, manta ray, anglerfish), seasoned
  sardines, bucket/bottomless bucket of milk, frog spawn, roe/caviar, and blighted fish.
  The default, **Any available food**, uses the first valid item in your inventory (stackable
  raw karambwanji first).
- **Automatically play with kitten** — keeps the kitten happy before it runs away.
- **Use ball of wool for attention** — when a ball of wool is in your inventory it is used
  on the kitten instead of stroking, giving roughly 51 minutes between interactions instead
  of 18–22. Falls back to stroking when you run out of wool.
- **Prevent idle logout** — presses Backspace before the client times out, so the kitten can
  keep growing while you are away.
- The existing overlay, timer and notification options are unchanged.
