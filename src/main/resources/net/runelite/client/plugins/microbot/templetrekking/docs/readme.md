# Temple Trekking

Automates the easy (Route 1 / Mort Myre Swamp) Temple Trekking route. Plugin version **1.1.4**.


## Requirements

- Both ends of the minigame unlocked (Paterdomus and Burgh de Rott).
- An **axe**, **knife** and **hammer** in your inventory for the event puzzles.
- Food for the Undead Lumberjack encounter.
- Start at either end with an easy companion available.

## Behaviour

1. Right-click `Escort` on an `…(easy)` follower at the current end.
2. Continue the NPC's "Okay, which route should we take?" dialogue, then select the easy
   route on the map ("slow route through Mort Myre Swamp"). This runs on every trek — the
   previous route flag is cleared at the next pickup. The exact Continue widget is clicked
   before falling back to the dialogue API or Space, so a stuck route prompt is retried and
   reset through the watchdog instead of being skipped silently.
3. For each event:
   - Combat (snakes, shades, vampyre juvinates, nail beasts, ghasts, giant snails,
     combined events): click `Evade-event` once. Combat has priority over vine and bridge
     detection: the `Evade-event` path is unambiguous, and Riyl shadow scenes also contain
     decorative choppable Dead trees that must not start the log handler. Food is eaten at
     the configured HP threshold. Undead Lumberjacks are never treated as a combat event.
   - Swamp vine: checked before either bridge. Every tick the script re-checks the rope state:
     if the long rope is already attached/swung, it never cuts again; otherwise it cuts until
     it has 3 short ropes, combines them into a long rope, checks that the rope is connected to
     the tree (`13846` / attach chat message), then uses it on the un-vined branch (`13845`)
     and swings from the transformed branch (`13846`). This fixes repeated `Cut-vine` clicks
     after combining: the leftover short rope no longer triggers another cut cycle. After the
     swing the attached-branch object is **not** required — it despawns before the first
     `Continue-trek` in the recording. The script now tries `Continue-trek` immediately and only
     clears follower chatter when the path is not clickable yet; the "heading off" line is no
     longer treated as a completion signal because it appears before the paths are ready. A
     scene change after the swing, or two successful `Continue-trek` clicks with the vine objects
     gone, clears the event. A solved latch prevents the same vine scene from re-entering `VINE`
     before the next instance loads.
   - **Plank/beam bridge**: `13834 → 22533 → 22534 → 22535`. Loot Planks and
     repair with 3 Planks, `Cross`, and `Continue-trek`. When **Farm lumberjacks** is checked,
     also pick up one lumberjack outfit piece and keep killing Undead Lumberjacks until it drops;
     only one piece drops per encounter. Otherwise kill
     only enough lumberjacks for the three Planks and leave outfit pieces on the ground. This handler never references log-bridge
     object IDs.
   - **Log bridge**: `13834 → 13835 → 13836 → 13837`. Chop 3 Dead trees, repair with the
     3 Logs, `Cross`, then `Continue-trek`. This handler never references plank/beam IDs.
   - Bridge detection uses only scene objects/NPCs, never follower dialogue, so the vine
     event's "can't get over this bog" line cannot start a bridge state. A completed handler
     also stays latched until its bridge object despawns, preventing an immediate re-entry.
4. On arrival, collect the reward token and pick up the next follower at that end. The
   script alternates direction automatically until stopped (or until Max treks is reached).
   Follower detection now searches 35 tiles instead of 20, the action cooldown matches the
   300 ms loop, and the route/recovery watchdogs are shorter, which removes most of the idle
   time between treks. If the player dies mid-trek, the script waits for the respawn, clears
   the encounter state and starts again from the current hub instead of remaining stuck in the
   old event state.
5. When **Redeem reward tokens** is enabled and the inventory holds at least **Redeem at
   tokens**, the script opens the Temple Trekking Rewards interface at the hub, selects the
   configured **Reward to redeem** by matching the item shown in the reward icon (so it never
   clicks an inert text label), and presses Buy once per token until all accumulated tokens are
   gone. If the chosen reward is an XP tome, the script then reads the tomes so they cannot fill
   the inventory. Redemption happens before the next follower pickup (and before stopping at Max
   treks). Tokens of all colours from both ends are counted; the overlay shows the current
   count and target.

## Configuration

- **Start side** — preferred end for the first pickup only. After that both hubs are
  accepted so the script keeps alternating direction.
- **Max treks** — 0 runs until stopped; otherwise stops after N deliveries.
- **Farm lumberjacks** — off by default. When checked, the Undead Lumberjack bridge event keeps
  killing lumberjacks until one Lumberjack outfit piece drops, then finishes the bridge. Only
  one piece drops per encounter, so the event stops farming after the first piece. When off,
  only enough lumberjacks are killed for the three Planks needed. Planks are always looted, but
  outfit pieces are left on the ground unless this option is on. The separate log bridge always
  chops Dead trees for Logs.
- **Redeem reward tokens** — off by default, so tokens simply accumulate. When on:
  - **Redeem at tokens** — wait until the inventory holds this many tokens (1–28), then redeem
    all of them. Each token takes a slot, so leave room for axe, knife, hammer and food.
  - **Reward to redeem** — XP tome (random skill), Herbs, Pure essence, Bow strings, Silver
    bars, Coal & iron ore, Watermelon seeds or Raw lobsters.
- **Supplies** — optional prechecks and food threshold.

## Debugging

The script is a `StateMachineScript`, so its live state is available from the agent server:

```
curl 'http://127.0.0.1:8081/debug/snapshot?script=TempleTrekkingScript'
```

This reports the current state, pending transitions, and the last 50 state transitions.
The overlay also shows state, side, event, trek count, reward-token count and a
"NO PROGRESS" stall warning.

Useful log lines (transitions and bounded retries only, not every tick):

- `[TempleTrekking] Found follower ...`
- `[TempleTrekking] Route dialogue detected; clicking Continue`
- `[TempleTrekking] Selected easy route ...`
- `[TempleTrekking] Clicked Evade-event`
- `[TempleTrekking] Entered plank/beam bridge event`
- `[TempleTrekking] Entered Dead tree / log bridge event`
- `[TempleTrekking] Chopping Dead tree for bridge logs`
- `[TempleTrekking] Clicked Cross on fixed plank bridge (attempt N)`
- `[TempleTrekking] Clicked Cross on fixed log bridge (attempt N)`
- `[TempleTrekking] Plank bridge event cleared`
- `[TempleTrekking] Log bridge event cleared`
- `[TempleTrekking] Used Long vine on Swamp tree branch`
- `[TempleTrekking] Swung from vine`
- `[TempleTrekking] Clicked Continue-trek after vine swing (N)`
- `[TempleTrekking] Vine swing landed but no Continue-trek path was clickable (...)`
- `[TempleTrekking] Vine event cleared (scene changed | two Continue-trek clicks)`
- `[TempleTrekking] Route widget absent; assuming route already selected`
- `[TempleTrekking] Trek N completed at ...`
- `[TempleTrekking] Reward redemption started (N tokens held)`
- `[TempleTrekking] Opened Temple Trekking Rewards interface`
- `[TempleTrekking] Selecting '...' via Details (icon index N, item M)`
- `[TempleTrekking] Redeemed one token (N left)`
- `[TempleTrekking] Read XP tome (item N)`
- `[TempleTrekking] Recovered; re-evaluating scene`

## Notes

Every event is instanced. The script identifies events by NPC/object ID in the scene cache
rather than by world coordinates. See `docs/TEMPLE_TREKKING_RESEARCH.md` in the repository
for the full recording analysis.
