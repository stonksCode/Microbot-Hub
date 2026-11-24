# Temple Trekking — Research & Automation Plan (Easy Route 1)

Status: **research complete; v1.1.2 built and compiling, awaiting live validation.**
Source recordings:
`C:\Users\user\.runelite\dro-recorder\20261120-143125-953` (original),
`C:\Users\user\.runelite\dro-recorder\20261123-110417-395` (vine/combat/death analysis).
Analysis artifacts: `C:\Users\user\AppData\Local\Temp\opencode\temple-trek-recording\` and
`C:\Users\user\AppData\Local\Temp\opencode\temple-trek-20261123-110417\`

## 1. Recording overview

| Property | Value |
|---|---|
| Wall clock (UTC) | 2026-11-20 19:31:25 → 19:47:26 |
| Session time | 0 → 960.6 s (16 min) |
| Streams | `events.jsonl` (56,865 lines), `session.jsonl` (65,804 lines) |
| Treks completed | 10 (5 each direction) |
| Start location | **Paterdomus**, region `13622`, player ~`(3432–3437, 3484, 0)` |
| Alternating end | **Burgh de Rott**, region `13874`, player ~`(3480–3483, 3242, 0)` |
| Route chosen every time | Easy / "slow route through Mort Myre Swamp" |
| Followers used | Mage (easy), Dalcian Fang (easy), Fyiona Fray (easy), Adventurer (easy) |

The player has both ends unlocked. The recording starts at the temple end and alternates
Paterdomus → Burgh de Rott → Paterdomus … after each completed trek. The plugin must
therefore handle **both endpoints symmetrically** — after arriving, pick up the next
available easy follower *at that end* and walk back.

Player loadout seen throughout: Rune axe, Knife, Hammer, 4–5 Swordfish, teleports,
`Efaritay's aid`. This is the standard "hard follower / easy route" supply setup: the
axe, knife and hammer are needed for the two solvable puzzle events.

## 2. Trek lifecycle (observed, exact)

### 2.1 Pick up follower (hub scene, not instanced)
- `menu_click option='Escort'` on an NPC whose name ends in `(easy)` — action
  `NPC_THIRD_OPTION`.
- Dialogue (widget `14221318`): *"I can take you to Burgh de Rott."* / *"I can take you to
  Paterdomus."*
- Dialogue (widget `15138822`): *"Oh really? That would be great! I'll give you a small
  reward once we reach our destination."*
- `LOADING` → `LOGGED_IN` (enters first instanced travel scene).

Notes:
- Sometimes the client needs `Continue` / `Cancel` clicks before the escort dialogue
  advances (seen at t=78.3, 661.1, 849.2). A robust handler must poll dialogue state and
  click through `WIDGET_CONTINUE` / `CC_OP` until the route dialogue appears.
- Followers are all named `<name> (easy)` at both hubs; query by suffix, not a fixed name.

### 2.2 Choose route
- Dialogue (widget `15138822`): *"Okay, which route should we take?"* — opens the map
  interface (`InterfaceID.TEMPLETREK_MAP = 329`).
- `menu_click option='Select'`, `action=CC_OP`.
- Game message: `You choose the slow route through Mort Myre Swamp.`
- Route-select widget children (confirmed in generated `InterfaceID.TempletrekMap`):
  - Easy: `TEMPLETREK_ROUTE_1 = 0x0149_0015` (21561365)
  - Medium: `TEMPLETREK_ROUTE_2 = 0x0149_0019` (21561369)
  - Hard: `TEMPLETREK_ROUTE_3 = 0x0149_001d` (21561373)
- The map also exposes per-event markers `TEMPLETREK_TRIP_ROUTE_*` (`0x0149_0022`+), usable
  later for progress display, not needed for control flow.

### 2.3 Event loop
After route selection the player is shuttled through a chain of **instanced scenes**. Every
event is entered via `LOADING`→`LOGGED_IN`. The travel hub instance uses region `8270`
(scene key hash `-1025899792`); each event type swaps in a distinct template region. In the
recording the instance base coordinates were always in the high-corner band
(X ≈ 10k–14.5k, Y ≈ 8k–12.1k), consistent with the instancing pitfalls in
`docs/PLUGIN_DEBUGGING_NOTES.md`.

Three path objects are present in every event scene:

| Object ID | Generated constant | Action | Use |
|---|---|---|---|
| 13831 | `TEMPLETREK_SWAMP_EVADE_PATH` | `Evade-event` (2nd option) | Skip a combat event |
| 13832 | `TEMPLETREK_SWAMP_SUCCESS_PATH` | `Continue-trek` (1st option) | Leave after a solved puzzle |
| 13833 | `TEMPLETREK_SWAMP_FAIL_PATH` | `Escape` (2nd option) | Abandon trek (not used) |

Detection rule: **puzzle objects take priority**; if no puzzle object is present, the event
is a combat event and the plugin clicks `Evade-event` on object 13831.

### 2.4 Trek complete
- Game message: `You reach your destination.`
- `LOADING` → `LOGGED_IN` back at the opposite hub.
- Dialogue (widget `15138822`): *"Many thanks for getting me safely to Burgh de Rott /
  the temple. Here's your reward."*
- Inventory gains a `Reward token`; dialogue (widget `12648450`): *"<Follower> gives you a
  reward token!"*
- Click `Continue`, then immediately start the next trek from this hub.

## 3. Event catalogue (all observed in the recording)

### 3.1 Combat events — always evaded
Every one of these was cleared with a single `Evade-event` click on object 13831, followed
by `You manage to evade the enemies.`

| Event | NPC IDs | Template region(s) seen | Notes |
|---|---|---|---|
| Swamp snake | `TEMPLETREK_SNAKE_1..3` (5615–5617) | 9550 | 3 snakes |
| Riyl/Asyn shadow (Shades) | `TEMPLETREK_SHADE_1..3` (5631–5633) | 8526 | 3–4 shades; a Dead tree (1289) is also in the scene |
| Vampyre Juvinate | `TEMPLETREK_VAMPIRE_1..3` (5634–5636) | 8014, 8782, 9294 | sometimes combined with Ghast / Nail beast |
| Nail beast | `TTREK2_NAIL_BEAST_1..3` (2946–2948) | 9294 | sometimes combined with Vampyre Juvinate |
| Ghast | `TEMPLETREK_GHAST_INVIS_1..3` (5622–5624), `..._VIS_1..3` (5625–5627) | 8782, 9294 | invisible until druid pouch; evading makes this moot. Spam: *"An attacking ghast just misses you."* |
| Giant snail | `TEMPLETREK_GIANTSNAIL_1..3` (5628–5630) | 9038, 9294 | sometimes paired with friendly `MORYTANIA_BOOT_SAMARITAN` (8679, Abidor Crank) — still evaded |
| Combined events | mixed | 8782, 9038, 9294 | one Evade click clears all NPCs in the scene |

Also in the tracker/questhelper but **not** seen on easy route 1: Head/tentacles
(`TEMPLETREK_TENTACLE_*`), Campsite (known-bugged), and Bog (removed from game).

Ghast side-effect observed: *"You feel something attacking your backpack, and smell a
terrible stench."* converts Swordfish → `Rotten food` in inventory (t=547.1). Cosmetic for
of the loop; no intervention needed.

### 3.2 Broken bridge (two physically different events)
Both variants start on `TEMPLETREK_BRIDGE_BROKEN` (13834) at template ~(2090, 5039),
region 8270, but they have **different repair object progressions** and must never share
material logic.

**Variant A — Undead Lumberjacks / Planks** (recording `20261123-080044-936`, t=49–204):
1. Undead Lumberjacks (`TTREK2_ZOMBIE_*` 5648+) spawn. Kill as many as the chosen strategy
   requires; they drop Planks (960) and lumberjack outfit pieces.
2. `Use Plank → Broken bridge`. Messages: *"You place a beam down over the shaky wooden
   support struts."* and *"You use your hammer to tap the plank into place."*
3. Object degrades through `RAMBLE_BRIDGE_BEAM_FIX_1` (22533) → `_2` (22534) → `_3` (22535).
   Two more `Use Plank → bridge` interactions (3 planks total).
4. `Cross` on 22535 → *"You walk across the fixed bridge."*
5. `Continue-trek` on a Path object (13832/other green/brown variants) → next scene.

**Variant B — Dead trees / Logs** (original recording, t=883–910):
1. No choppable trees means it is **not** this variant. When choppable `Dead tree` objects
   (1365 `DEADTREE2_SWAMP` or 1289 `DEADTREE2_DARK`) are present, chop three of them for Logs
   (1511). Stumps (1352/1353) confirm the tree event.
2. `Use Logs → Broken bridge` with the same hammer messages.
3. Object degrades through `TEMPLETREK_BRIDGE_FIXED_1` (13835) → `_2` (13836) → `_3` (13837).
4. `Cross` on 13837 → *"You walk across the fixed bridge."* → `Continue-trek`.

Choppable Dead trees are **not** exclusive to the log bridge. The Riyl shadow combat scene at
`t=1507.4` in `20261123-110417-395` spawned many `1365`/`1289` Dead trees with `Chop down`,
plus `Evade-event` path `13831`, but no bridge object. A tree-only check is therefore not a
valid log-bridge test; the fixed `TEMPLETREK_BRIDGE_BROKEN`/`_FIXED_*` object must also be
present, and combat must take priority when an Evade path exists.

**Implementation:** two independent handlers (`TempleTrekkingPlankBridgeHandler` and
`TempleTrekkingLogBridgeHandler`) own their own object IDs and materials. Detection is by
scene objects/NPCs only, never follower dialogue, because the vine event's "can't get over
this bog" line must not be mistaken for a bridge.

### 3.3 Swamp vine / rope swing (once, t=713–744)
Objects in region 8270 near template (2093–2096, 5011–5015):
`TEMPLETREK_SWAMPTREE_SMALL_VINES` 13847, `..._SMALL_1` 13848, `..._SMALL_2` 13849,
`TEMPLETREK_SWAMPTREE_BRANCH_VINE` 13846.

Preceded by dialogue *"Well that's torn it… we can't get over this bog."*

Sequence observed:
1. `Cut-vine` on Swamp tree ×3 (requires Knife) → 3 × `TEMPLETREK_SHORT_VINE` (7778).
2. `Use` Short vine → Short vine → `TEMPLETREK_LONG_VINE` (7777), message
   *"You twist the vines together to make a long vine."*
3. `Use` Long vine → `TEMPLETREK_SWAMPTREE_BRANCH_VINE` (13846), message
   *"You throw the vine up and wrap it around the branch."*
4. `Swing-from` on 13846 → follower dialogue *"That's amazing! I'll have to do the same."*
   then *"Whahay! That was great!…"*
5. `Continue-trek` on object 13832 ×2 → next scene.

## 4. State machine plan

Package: `net.runelite.client.plugins.microbot.templetrekking`
Template: `pestcontrol` family (Plugin/Config/Script/Overlay, `RuntimeState` +
`transitionTo()`, watchdog, snapshot overlay). Reset all state at the top of `run()` before
scheduling (debugging notes §5).

Proposed states and transitions:

```
STOPPED
  -> FIND_FOLLOWER   (at either hub; query NPCs with name containing "(easy)")
  -> PICK_UP         (Escort + dialogue clicks)
  -> SELECT_ROUTE    (map interface; click TEMPLETREK_ROUTE_1, confirm "Select")
  -> TREK_TRAVEL     (instanced travel scene; wait for event objects/NPCs)
       -> COMBAT_EVADE  (no puzzle objects + combat NPCs -> click 13831 Evade-event)
       -> VINE          (13847/13848/13849 present -> cut/combine/use/swing)
       -> BRIDGE_LOGS   (13834 + Dead trees/stumps -> chop 3 Logs, repair, cross)
       -> BRIDGE_PLANKS (13834/22533-35 + lumberjacks -> loot 3 Planks, repair, cross)
       -> each puzzle ends with Continue-trek on 13832
  -> TREK_COMPLETE   ("You reach your destination" -> reward dialogue -> token)
  -> FIND_FOLLOWER   (at the new hub)  [loop]
  + RECOVER          (watchdog: no progress for N seconds, re-evaluate scene/varbits)
```

Guards / signals:
- Hub detection: `Rs2Player.getWorldLocation()` region 13622 (Paterdomus) or 13874
  (Burgh de Rott), `scene.isInstance() == false`.
- Instance detection: `scene.isInstance() == true`; use **entity cache queries by ID/name**,
  never hardcoded world points against mixed coordinate spaces (debugging notes §2/§7).
- Event confirmation: chat messages `You manage to evade the enemies.`,
  `You walk across the fixed bridge.`, `You throw the vine up…`,
  `You reach your destination.`
- Optional cross-check via varbits `1954–1960` / varps `700, 702` (see below), once live
  probing confirms values in this client build.

Watchdog: treat `LOADING`/`LOGGED_IN` transitions and a 3–6 s no-progress window as normal;
only escalate after the existing bounded-retry window, then log state + nearby object IDs
and re-evaluate rather than spamming clicks.

## 4a. Exact interaction verbs from the recording (139 menu clicks total)

These are the exact option strings and action types the plugin should issue:

| Option | Action type | Target | Use |
|---|---|---|---|
| `Escort` | `NPC_THIRD_OPTION` | `<name> (easy)` NPC | Pick up follower |
| `Select` | `CC_OP` | widget `0x0149_0015` | Choose easy route |
| `Continue` | `WIDGET_CONTINUE` | widget `0x00E7_0005` | Advance NPC dialogue |
| `Continue` | `CC_OP` | widget `0x00C1_0002` | Reward-token dialogue |
| `Evade-event` | `GAME_OBJECT_SECOND_OPTION` | `Path` (13831) | Skip combat event |
| `Continue-trek` | `GAME_OBJECT_FIRST_OPTION` | `Path` (13832) | Leave solved event |
| `Inspect` | `GAME_OBJECT_FIRST_OPTION` | `Broken bridge` (13834) | Start bridge repair |
| `Use` | `WIDGET_TARGET_ON_GAME_OBJECT` | Plank/Logs → bridge | Repair (×3) |
| `Cross` | `GAME_OBJECT_FIRST_OPTION` | `Fixed bridge` (13837) | Cross bridge |
| `Chop down` | `GAME_OBJECT_FIRST_OPTION` | `Dead tree` (1289/1365) | Get logs |
| `Attack` | `NPC_SECOND_OPTION` | Zombie (5647), Undead Lumberjacks | Bridge guards |
| `Take` | `GROUND_ITEM_THIRD_OPTION` | Plank / drops | Loot guards |
| `Cut-vine` | `GAME_OBJECT_FIRST_OPTION` | `Swamp tree` (13847-49) | Get short vines |
| `Use` | `WIDGET_TARGET_ON_WIDGET` | Short vine → Short vine | Make long vine |
| `Use` | `WIDGET_TARGET_ON_GAME_OBJECT` | Long vine → branch (13846) | Attach vine |
| `Swing-from` | `GAME_OBJECT_FIRST_OPTION` | `Swamp tree branch` (13846) | Cross bog |

`CANCEL` (×4) and ordinary `WALK` (×17) round out the click set.

## 5. Varbits / interfaces for live probing (from earlier research)

- Varbit `1956` (`TEMPLETREK_BUTTON_CONTROL`): 1 when leaving either hub, 0 on
  teleport/escape/next event — good "in trek" flag.
- Varbit `1957` (`TEMPLETREK_LAST_EVENT`): 1 Shades, 2 Head/tentacles, 3 Vampyre Juvinates,
  4 Ghasts, 5 Snails, 6 Snakes, 7 Bog, 8 Bridge/trees, 9 Rope, 10 Abidor Crank,
  11 Bridge/zombies, 12 Nail beasts. This is the cleanest event classifier if the value
  persists long enough in this build.
- Varbit `1958` (`TEMPLETREK_EVENT_FLASH`): current event location; resets to 0 on finish.
- Varbit `1955` (`TEMPLETREK_NPC_POINT_VALUE`): points; resets on reward/early leave.
- Varbit `1954` `TEMPLETREK_NPCs_VISIBLE`, `1959` `TEMPLETREK_MULTI_NPC_FOOD_GIVE`,
  `1960` `TEMPLETREK_DEBUG_ROOM_ID`.
- Varp `700` `TEMPLETREK_MAIN_VAR`, `702` `TEMPLETREK_TEMP_VAR`.
- Varbit `2458` `TEMPLETREK_REWARD_TOKEN_SLOT`, `2459` `TEMPLETREK_REWARD_SELECTED`.

The DRO recorder does not emit varbit events, so these must be verified with the agent
server (`/state`, `/debug/snapshot?script=<name>`) on a live trek.

## 6. Decisions for implementation

1. **Bridge policy** — expose a player-facing strategy config. The Undead Lumberjacks can
   drop the lumberjack outfit, so farming them must remain an option.
   The strategy only changes the plank event: default `FASTEST_AVAILABLE` kills enough lumberjacks
   for three Planks, while `KILL_FOR_PLANKS` farms every lumberjack and outfit drop. The
   separate log event always chops three Dead trees; config never substitutes materials.
2. **Combat fallback** — the recording shows melee attacks only for the bridge guards. On
   easy route 1 all other combat is evadable, so no general combat module is needed.
3. **Point optimisation** — solving bridge/vine yields points; evading yields few/none. Plan
   is to solve puzzles and evade all combat (matches recording).
4. **Run mode** — support **both**: run indefinitely until stopped (default), plus an
   optional max-treks config that stops the script once reached.
5. **Config surface** — start side (auto-detect), max treks, bridge strategy, solve-vs-evade
   puzzles, food/knife/axe/hammer prechecks, overlay verbosity.
6. **Loot and reward options** — lumberjack outfit drops can be ignored after the set is
   complete (planks are always looted); reward tokens can accumulate until a configurable count,
   then all be redeemed for one configurable reward type at the hub.

## 7. Bugfix pass — recording 20261120-152135-564

Analysis of the second DRO recording found four concrete defects, all fixed in
`TempleTrekkingScript`:

1. **Escort spam when the plugin starts mid-instance.** 47 `Escort` clicks produced 30
   "You're in the middle of an escort already!" messages. The route prompt arrives as a
   `dialogue_changed`, and `Rs2Dialogue.isInDialogue()` reported false for it, so the
   script never left `PICK_UP`. Fix: instance detection now outranks follower detection,
   and `SELECT_ROUTE` detects and clicks the route widget directly
   (`InterfaceID.TempletrekMap.TEMPLETREK_ROUTE_1`) instead of relying on dialogue APIs.
   `FIND_FOLLOWER -> SELECT_ROUTE` and `PICK_UP -> SELECT_ROUTE` transitions were added.
2. **Stopped after one delivery.** The reward arrives as a dialogue change plus an
   inventory change, not a GAMEMESSAGE, so `rewardCollected` was never set and
   `TREK_COMPLETE` never transitioned. Fix: reward is detected from the dialogue text and
   from a reward-token inventory-count increase. Also, `startSide` now only constrains the
   very first pickup; after that both hubs are accepted so treks alternate.
3. **Bridge material choice.** The plank event has Undead Lumberjacks and dropped planks but
   no choppable trees; the log event has choppable Dead trees but no lumberjacks. Choosing
   material from inventory alone caused stalls. Fix: `detectBridgeMaterial()` inspects the
   scene first, and each strategy falls back to whatever material is actually available.
4. **Fixed bridge not crossed.** Repair state is now driven by the authoritative chat
   messages "You place a beam/log down..." (one per placement) and
   "You walk across the fixed bridge.". Crossing retries are bounded (12) and the script
   no longer advances to `CONTINUE` until crossing is confirmed. Green and brown path
   variants are both handled for `Evade-event` and `Continue-trek`.
5. **Route dialogue must be continued first.** The original recording shows the NPC text
   "Okay, which route should we take?" rendering before the map interface appears. The
   text widget (`CHAT_LEFT` child 6) can be visible before `Rs2Dialogue.isInDialogue()`
   returns true, so the script skipped past route selection. `SELECT_ROUTE` now detects that
   widget directly, presses Continue/Space until the map opens, then clicks `TEMPLETREK_ROUTE_1`.
   Route-skipping now only applies when neither the dialogue nor the map is present.

The same recording confirmed the easy-route flow: Paterdomus <-> Burgh de Rott, the
`Select` CC_OP on widget `21561365`, one `Evade-event` per combat encounter, and reward
delivery immediately after "You reach your destination."

## 8. Bugfix pass — recording 20261123-080044-936 (v1.0.2)

Source: `C:\Users\user\.runelite\dro-recorder\20261123-080044-936`
Analysis artifacts: `C:\Users\user\AppData\Local\Temp\opencode\temple-trek-20261123\`

### 8.1 What the recording showed

- Start: Burgh de Rott `r13874`, `Escort` Dalcian Fang at `t=3.175`.
- Route prompt rendered at `t=8.086`; the plugin clicked the exact Continue widget and
  then `Select` (`CC_OP p1=21561365`) at `t=9.578`. Route selection itself worked here, but
  the prompt is a `dialogue_changed`, not a chat message, so only direct widget probing can
  detect it reliably.
- After a combat event, the bridge scene loaded at `t=48.335`. The follower's
  "Blimey! ... can't get over that bridge!" dialogue opened at `t=49.474` and stayed open
  until a `Cancel` at `t=178.455`. Meanwhile the plugin spent `t=115..153` attacking the
  bridge guards.
- Root cause of the long delay: bridge guards (`TEMPLETREK_ZOMBIE` / Undead Lumberjacks)
  were still in `COMBAT_NPCS`, so the state machine entered `COMBAT_EVADE` before the bridge
  object was visible. It only reached `BRIDGE` after the 60 s watchdog recovery path
  (`RECOVER -> FIND_FOLLOWER -> SELECT_ROUTE -> TREK_TRAVEL -> BRIDGE`).
- Planks were looted at `t=184.376`, `186.135`, `187.976`. Three `Use Plank -> bridge`
  placements succeeded at `t=190.317`, `196.115`, `199.915` (messages at 192.875, 196.534,
  200.076). `Cross` on `Fixed bridge` happened at `t=203.016`, followed by
  "You walk across the fixed bridge." and `Continue-trek` at `t=204.415`.
- One self-targeting misclick (`Use Plank -> Plank` at `t=194.097`) occurred while the
  bridge object was briefly absent; a selected item with no valid target must be deselected.
- Reward dialogue/inventory arrived at `t=221..223`. The user then stopped the plugin, so
  the absence of a second pickup in the recording is expected, not a loop defect.

### 8.2 v1.0.2 fixes

1. Split the single `BRIDGE` state into `BRIDGE_PLANKS` and `BRIDGE_LOGS`. Each has its own
   handler; `Material`, `detectBridgeMaterial()` and shared `gatherMaterial()` were removed.
2. Scene classification is explicit and follows the physical scene: if any Dead tree is
   choppable it is a log event; otherwise a broken bridge/guards/follower dialogue is the
   plank event. This also covers the window before all lumberjacks spawn. Config only controls
   how aggressively to farm lumberjacks, never which material the event uses.
3. Removed `TEMPLETREK_ZOMBIE` from generic combat NPCs and made bridge detection outrank
   combat detection, including guard-only scenes before the bridge object spawns.
4. Bridge dialogue is cleared immediately (follower chatter and the repair confirmation),
   so it can no longer block interactions for two minutes.
5. Plank repair no longer requires a separate mandatory `Inspect` phase; direct `Use Plank`
   on the bridge works (confirmed in this recording), and the repair confirmation is handled
   if it appears. Selected items are deselected when no bridge target is present.
6. Crossing retries remain bounded and `Continue-trek` is only clicked after
   "You walk across the fixed bridge." or a visible fixed bridge.
7. Route dialogue handling reads `InterfaceID.ChatLeft.TEXT` (`0x00E7_0006` / `15138822`)
   directly and clicks the exact Continue widget (`InterfaceID.ChatLeft.CONTINUE` /
   `0x00E7_0005` / `15138821`) before the dialogue API/Space fallback. Repeated failure now
   sends `SELECT_ROUTE` to `RECOVER` rather than silently marking the route as skipped.
8. `routeSelected` is reset when entering `SELECT_ROUTE` on every trek. Previously the flag
   from the previous delivery remained set, so the second trek skipped the route prompt and
   appeared stuck at "Okay, which route should we take?".
9. Version bumped to `1.0.2`; JAR rebuilt as `TempleTrekkingPlugin-1.0.2.jar`.

## 9. Vine event fix (v1.0.3)

Re-checking the original recording around `t=696–745` showed why the vine event was ignored:

- The event opens with NPC dialogue at `t=697.180`: *"Well that's torn it... Looks like we
  can't get over this bog. We'll have to go back to Burgh de Rott."* The old `doVine()` never
  dismissed dialogue, so it tried to cut vines while the dialogue blocked interactions.
- The branch is **object 13845** (`TEMPLETREK_SWAMPTREE_BRANCH`) when the Long vine is used at
  `t=724.775`. Only after that does it become **object 13846**
  (`TEMPLETREK_SWAMPTREE_BRANCH_VINE`), which is the object with `Swing-from` at `t=738.292`.
  The old code only looked for 13846, so `branch == null` and it returned forever.
- Two follower lines appear after the swing (`t=740.375` and `t=743.369`) and must be dismissed
  before each `Continue-trek`.

v1.0.3 changes:

1. `doVine()` now dismisses dialogue before every interaction.
2. Cutting is restricted to the three `Cut-vine` tree stages (13847–13849); the branch is no
   longer mistakenly treated as a cuttable vine.
3. The Long vine is used with `Rs2Inventory.useItemOnObject(...)` on either branch object id.
   Attachment is confirmed by the existing chat message or by the 13845 → 13846 transform.
4. `Swing-from` is performed on the transformed branch (13846), followed by two
   `Continue-trek` clicks with follower chatter cleared between them.
5. Vine state is reset on recovery; version bumped to `1.0.3` and the JAR rebuilt.

## 10. Bridge rewrite (v1.0.4)

The attached recording `20261123-083650-252` completed three treks and then entered the vine
event, but no bridge event ever spawned. It exposed a false positive in the v1.0.3 bridge
detection: `hasBridgeFollowerDialogue()` matched the substring "can't get over" in the bog
dialogue *"Well that's torn it... Looks like we can't get over this bog."* The script entered
a bridge state before `hasVine()` ran, did nothing, and eventually hit the watchdog.

v1.0.4 changes:

1. Deleted the old shared bridge implementation from `TempleTrekkingScript`: `BridgeEvent`,
   `BridgePhase`, `doBridge(...)`, `enterBridge(...)`, `hasBridgeFollowerDialogue()`,
   `detectBridgeMaterial()` remnants, shared placement/cross counters and material fallback.
2. Added two independent handlers:
   - `TempleTrekkingPlankBridgeHandler`: Undead Lumberjacks, Planks (960) and the
     `RAMBLE_BRIDGE_BEAM_FIX_*` progression `13834 → 22533 → 22534 → 22535`.
   - `TempleTrekkingLogBridgeHandler`: choppable Dead trees, Logs (1511) and the
     `TEMPLETREK_BRIDGE_FIXED_*` progression `13834 → 13835 → 13836 → 13837`.
   They share only stateless plumbing (`TempleTrekkingBridgeContext`, `TempleTrekkingBridgeHandler`);
   no object ID, item ID or repair rule is shared.
3. Detection no longer reads follower dialogue. A bridge requires its own scene objects/NPCs:
   plank requires a bridge object; log requires a bridge object plus a choppable Dead tree or
   stump (or a repaired log-bridge stage). `hasVine()` is evaluated before both bridge
   transitions and is also excluded from the bridge guards.
4. `TEMPLETREK_ZOMBIE` remains out of generic combat NPCs; Undead Lumberjacks are likewise
   excluded so they cannot trigger `COMBAT_EVADE`.
5. Both handlers dismiss only active bridge dialogue, loot/plank or chop/log, place exactly
   three materials, click `Cross`, wait for "You walk across the fixed bridge.", then click
   `Continue-trek` on every green/brown Path variant.
6. Each handler keeps a solved latch until its final bridge object despawns. This prevents the
   lingering fixed bridge from re-entering the same event after the script returns to
   `TREK_TRAVEL`.
7. Config descriptions were rewritten to describe the two events separately while preserving
   the lumberjack-outfit farming option and continuous loop/`maxTreks` behaviour.
8. Version bumped to `1.0.4`; JAR rebuilt as `TempleTrekkingPlugin-1.0.4.jar`.

Live validation still needed: vine completion, both bridge variants, route re-selection, reward
handoff, and indefinite back-and-forth looping.

## 11. Vine continuation and death fix (v1.0.5)

Recording `20261123-110417-395` contains both a **successful** vine cycle and the reported death.

Successful attach evidence (first cycle):

- `t=1214.134` `Use` Long vine on Swamp tree branch.
- `t≈1214` chat: *"You throw the vine up and wrap it around the branch."*
- Object `13845 → 13846`, then `t=1235.315` `Swing-from`.
- `t=1240.554` and `t=1241.275` `Continue-trek`, `t=1258.894` *"You reach your destination."*

Failure evidence (second cycle):

- Attach at `t=1377.774`, swing at `t=1398.415`.
- Follower chatter cleared at `t=1404.114`, but no `Continue-trek` until `t=1495.433` (~91 s).
- Cause: `doVine()` returned whenever `ObjectID.TEMPLETREK_SWAMPTREE_BRANCH_VINE` (13846) was
  absent **after** the swing. The recording proves that object despawns before the first
  `Continue-trek`, so the script sat in `VINE` and never evaluated the next combat event.

Death evidence:

- `t=1507.373` three `Riyl shadow` NPCs (`NpcID.TEMPLETREK_SHADE_1` / 5631) spawn;
  `t=1507.409` `Evade-event` path `13831` spawns. Many choppable Dead trees are also present.
- No `Evade-event` click occurred before the player died at `t=1549.916`. The first post-death
  evade click was at `t=1549.918`.
- HP fell 16 → 14 → 8 → 5 → 0 between `t=1530` and `t=1549`.

v1.0.5 changes:

1. After `Swing-from`, the attached-branch object is no longer required. `Continue-trek` is
   retried promptly through every green/brown Path variant.
2. Vine completion is confirmed by the follower's "heading off" dialogue, an instance/region
   change, or two successful `Continue-trek` clicks — not by a fixed number of blind clicks.
3. Post-swing follower chatter is cleared before continuation, and both `13845` branch tiles are
   covered when attaching/swinging.
4. Combat has absolute priority over vine and bridge states. `Evade-event` path detection is
   explicit, and `COMBAT_EVADE` can be entered from `VINE`, `BRIDGE_PLANKS`, `BRIDGE_LOGS` and
   `RECOVER`. The NPC-only fallback additionally requires no vine/bridge object in the scene,
   so a stale NPC cannot steal a puzzle event. Dead trees alone can no longer pull a combat
   scene into the log bridge handler.
5. Food is eaten in `COMBAT_EVADE` and `VINE` at the configured HP percentage.
6. Death transitions send in-trek states to `RECOVER`; recovery waits for respawn, resets all
   encounter flags, then resumes follower pickup at the current hub.
7. Version bumped to `1.0.5`; JAR rebuilt as `TempleTrekkingPlugin-1.0.5.jar`.

Live validation still needed: vine completion, both bridge variants, route re-selection, reward
handoff, death/respawn recovery, and indefinite back-and-forth looping.

## 12. Loot/reward options (v1.0.6)

User requests:
- Ignore lumberjack outfit pieces after the set is complete.
- Check how many reward tokens are held, wait until a configured count, then redeem for a chosen
  reward type such as XP tomes or herbs.

Implementation notes:
- New `lootLumberjackPieces` checkbox gates only `OUTFIT_DROPS` in
  `TempleTrekkingPlankBridgeHandler.loot()`. `ItemID.WOODPLANK` is always taken first, so
  disabling outfit loot can never break the plank bridge.
- Generated token IDs are `TEMPLETREK_{YELLOW,RED,BLUE}_TOKEN` (7774–7776) and the `_VAR`
  variants (10934–10936). All six are counted; the pre-existing `"Reward token"` string count
  remains the trek-completion signal.
- Reward interface constants come from generated `InterfaceID.TrekRewards`:
  `UNIVERSE = 17956864`, `FRAME = 17956865`, `CONTENTS = 17956866`,
  `REWARDS = 17956868`, `TEXT = 17956869`, `ICONS = 17956870`,
  `BUYBUTTON = 17956875`, `BUYBUTTONTEXT = 17956876` (interface 274).
- No recording contains reward-interface events (`179568`/`17956864` never appears), so the
  handler locates the selected reward by visible widget text across REWARDS/ICONS/TEXT, with a
  child-index fallback, rather than hardcoding an unverified child index.
- New `REDEEM_REWARDS` state runs from `FIND_FOLLOWER` at either hub only when the token count is
  at least `rewardThreshold`. Once started, it redeems every accumulated token for
  `rewardType`, returns to `FIND_FOLLOWER`, and then honours `maxTreks` shutdown. This keeps the
  reward interface out of instanced trek scenes.
- Reward types: XP tome (random skill), herbs, pure essence, bow strings, silver bars,
  coal & iron ore, watermelon seeds and raw lobsters. XP tome skills reported by the wiki are
  Agility, Firemaking, Fishing, Mining, Slayer, Thieving and Woodcutting.
- Overlay now shows `Reward tokens: current / target` when redemption is enabled, and the debug
  snapshot includes the new state.

v1.0.6 changes:
1. Added `TempleTrekkingReward` enum and `TempleTrekkingRewardHandler`.
2. Added `Loot lumberjack pieces` checkbox to the Events section.
3. Added a Rewards config section: `Redeem reward tokens`, `Redeem at tokens` (1–28) and
   `Reward to redeem`.
4. Added the `REDEEM_REWARDS` state and hub-only redemption before the next follower pickup.
5. Version bumped to `1.0.6`; JAR rebuilt as `TempleTrekkingPlugin-1.0.6.jar`.

Live validation still needed: all reward dropdown options, token threshold behaviour, lumberjack
loot checkbox, plus the outstanding v1.0.5 checks.

## 13. Vine rope re-check fix (v1.0.7)

Reported symptom: after combining the ropes, the script kept clicking `Cut-vine` for more short
rope instead of attaching the long rope.

Root cause in `doVine()`: the short-rope count was checked **before** the long-rope check.
Combining two short vines consumes them but leaves the third short vine in the inventory, so the
next tick saw `Short vine count = 1 < 3` and cut again. The long rope never got used.

v1.0.7 changes:

1. Every tick now re-checks rope state in this order: attached/swung → long rope on tree or in
   inventory → three short ropes → cut short rope. Once attached or swung, rope cutting is skipped.
2. The "connected to tree" check is explicit: the transformed branch object `13846` or the
   `You throw the vine up...` chat confirmation sets `vineAttached` before any rope decision.
3. If a long rope exists, it is used on branch `13845`/`13846` immediately; the old one-shot
   `vineUseAttempted` latch was removed so attachment is retried each tick until it succeeds.
4. Combining now happens as soon as three short vines are held; the leftover short vine no longer
   starts another cut cycle.
5. `continueVineTrek()` is a separate post-swing step and still does not require the despawned
   attached-branch object.
6. Version bumped to `1.0.7`; JAR rebuilt as `TempleTrekkingPlugin-1.0.7.jar`.

Live validation still needed: full vine cycle (cut → combine → attach → swing → continue), plus all
outstanding v1.0.5/v1.0.6 checks.

## 14. Post-swing Continue-trek fix (v1.0.8)

Reported symptom: the vine rope/attach/swing portion completed, but the script never clicked the
`Continue-trek` path and kept looping as if the vine event had already been cleared.

Root causes:

1. `dialogueIndicatesHeadingOff()` matched the follower line that appears immediately after the
   swing, before the `Continue-trek` paths were ready. `doVine()` set `vineSolved = true` and left
   `VINE` without clicking the path.
2. `vineSolved` was never reset when re-entering `VINE`. With old vine objects still cached, the
   state machine bounced `VINE → TREK_TRAVEL → VINE` with the latch already set.
3. The internal `hasCombatEvent()` check could also mark the vine solved before continuation.
   Combat is already promoted out of `VINE` by a transition guard, so that shortcut was removed.

v1.0.8 changes:

1. `"heading off"` now confirms completion only after at least one `Continue-trek` click. Normal
   completion is still two successful `Continue-trek` clicks or a changed instance.
2. `TREK_TRAVEL → VINE` now requires `hasVine() && !vineSolved`, and `vineSolved` is reset when
   entering a new `VINE`, when the solved vine scene disappears from `TREK_TRAVEL`, and when a new
   `FIND_FOLLOWER` cycle begins.
3. Removed the premature `hasCombatEvent() → vineSolved` shortcut from `doVine()`; the state-machine
   transition guard handles combat without skipping continuation.
4. Version bumped to `1.0.8`; JAR rebuilt as `TempleTrekkingPlugin-1.0.8.jar`.

Live validation still needed: confirm two `Continue-trek` clicks after the swing and no repeated
"Vine event cleared" transitions, plus all outstanding v1.0.5–v1.0.7 checks.

## 15. Reward selection fix (v1.0.9)

Recording: `C:\Users\user\.runelite\dro-recorder\20261123-135949-429`

Reported symptom: reward redemption reached the Rewards interface but appeared to click a reward
with no effect, then got stuck instead of consuming tokens.

Recording evidence (session.jsonl):

- Opening a token issues `CC_OP` option `Look-at` on widget `9764864` (inventory).
- Every reward choice is a `CC_OP` option `Details` on widget `17956870` (`TrekRewards.ICONS`)
  with `param0` = child index; `Claim` is `CC_OP` on `17956875` (`BUYBUTTON`).
- The ICONS children are item widgets with ids: `7936` pure essence (0), `1777` bow string (1),
  `3049` grimy toadflax/herbs (2), `2355` silver bar (3), `453` coal (4), `5146` watermelon
  seeds (5), `377` raw lobster (6), `7779` fishing tome / XP tome (7), and `5079` dummy (8).
- XP tomes are then read with `CC_OP` option `Read` on inventory item ids `7779–7799`.

Root cause: `findRewardWidget()` searched generic text in `REWARDS`/`FRAME`/`UNIVERSE` before the
clickable ICONS child. A descriptive label could match "xp"/"herb"/"essence" and be clicked even
though it has no `Details` action, so selection never changed and `Claim` did nothing.

v1.0.9 changes:

1. `TempleTrekkingReward` now carries the exact icon item ids and matches the ICONS child by
   `Widget.getItemId()` first. The old text search is only a fallback and now maps TEXT labels
   back to the same-index ICONS child rather than clicking the label.
2. Token opening prefers the real left-click option `Look-at`, with the old names as fallbacks.
3. Selection logs the icon index and item id, making a wrong click obvious in the log.
4. When XP tome is the chosen reward, accumulated tomes are read with `Read` after all tokens are
   redeemed, preventing inventory overflow during long sessions.
5. Version bumped to `1.0.9`; JAR rebuilt as `TempleTrekkingPlugin-1.0.9.jar`.

Live validation still needed: confirm one `Selected reward option ... (icon index N, item M)` line
per token and one `Redeemed one token` line per token, plus all outstanding earlier checks.

## 16. Simplified lumberjack setting (v1.1.0)

Request: remove the whole **Events** settings section. The only event option should be one
checkbox to farm lumberjacks. When checked, the Undead Lumberjack encounter runs until one
outfit piece is picked up; only one piece drops per encounter.

v1.1.0 changes:

1. Removed the `Events` config section and its three settings: `bridgeStrategy`
   (`Bridge repair`), `clearBridgeGuards` (`Farm all lumberjacks`) and `lootLumberjackPieces`
   (`Loot lumberjack pieces`). Deleted the now-unused `TempleTrekkingBridgeStrategy` enum.
2. Added one `farmLumberjacks` checkbox in **General**, off by default.
3. `TempleTrekkingPlankBridgeHandler` now tracks `outfitPieceLooted` per encounter. With the
   checkbox on it keeps killing Undead Lumberjacks until an outfit piece is looted, then stops
   farming and completes the bridge with the gathered Planks. With the checkbox off it kills
   only enough lumberjacks for the three Planks needed.
4. Planks and any outfit piece that drops are always looted; the old separate loot toggle is gone.
5. Version bumped to `1.1.0`; JAR rebuilt as `TempleTrekkingPlugin-1.1.0.jar`.

Live validation still needed: confirm one piece per encounter ends the farming behaviour and the
bridge still completes, plus all outstanding earlier checks.

## 17. Exact widget actions for reward redemption (v1.1.1)

Reported symptom after v1.1.0: the Rewards interface opened but the bot never redeemed; it sat on
the option screen.

Root cause: v1.0.9/v1.1.0 used `Rs2Widget.clickWidget(Widget)`, which is a plain left-click at the
widget bounds. The recording showed that selecting a reward is a `CC_OP "Details"` action on the
ICONS child (`17956870`), and claiming is `CC_OP "Claim"` on BUYBUTTON (`17956875`). A generic
left-click did not trigger those actions, so the option was never selected and `Claim` did nothing.

v1.1.1 changes:

1. `TempleTrekkingReward` now stores the recorded child index for every reward: essence 0, bow
   string 1, herbs 2, silver 3, coal/iron 4, seeds 5, lobster 6, XP tome 7.
2. `TempleTrekkingRewardHandler` now uses Microbot's `NewMenuEntry` + `Microbot.doInvoke()` to send
   the exact widget actions from the recording: `Details` on `ICONS` child N, then `Claim` on
   `BUYBUTTON` with `param0 = -1`, both as `MenuAction.CC_OP` with identifier 1.
3. Selection logs the configured reward, child index and item id, e.g.
   `Selecting 'XP tome (random skill)' via Details (icon index 7, item 7779)`.
4. Version bumped to `1.1.1`; JAR rebuilt as `TempleTrekkingPlugin-1.1.1.jar`.

Live validation still needed: confirm each token logs one `Selecting ... via Details` line followed
by one `Redeemed one token` line, plus all outstanding earlier checks.

## 18. Client-thread fix for widget visibility (v1.1.2)

Reported error after v1.1.1:

```
[TempleTrekking] Error in state REDEEM_REWARDS: must be called on client thread
java.lang.IllegalStateException: must be called on client thread
```

Root cause: `lz.isHidden()` (the injected implementation of `Widget.isHidden()`) asserts that the
caller is on the RuneLite client thread. Scripts run on their own thread, so calling
`widget.isHidden()` while locating ICONS/TEXT children threw before the `Details` action could be
sent.

v1.1.2 changes:

1. Replaced direct `Widget.isHidden()` calls with a small helper that delegates to
   `Rs2Widget.isHidden(widget.getId())`, which schedules the visibility check on the client thread.
2. Left the recorded widget actions unchanged: `CC_OP "Details"` on ICONS child N and `CC_OP
   "Claim"` on BUYBUTTON, both through `Microbot.doInvoke()`.
3. Version bumped to `1.1.2`; JAR rebuilt as `TempleTrekkingPlugin-1.1.2.jar`.

Live validation still needed: confirm one `Selecting ... via Details` line followed by one
`Redeemed one token` line per token, with no client-thread exception.

## 19. Faster loop and post-swing Continue-trek priority (v1.1.3)

Reported symptom: noticeable idle time between treks, and after the vine branch swing the script
could still sit in `VINE` without clicking `Continue-trek`.

The original vine stall analysis (section 11) showed the failure sequence:

- `t=1398.415` `Swing-from` on the vined branch.
- `t=1400.512` / `t=1403.514` follower chatter, dialogue closed at `t=1404.114`.
- No `Continue-trek` until `t=1495.433` (~91 s later).

The old handler required the transformed branch (`13846`) to still exist before it would consider
the swing finished; the recording proves that object despawns before the first `Continue-trek`.
v1.0.8 removed that gate, but the handler still cleared dialogue before attempting the click and
used the "heading off" line as a secondary completion signal even though that line appears before
the Continue paths are ready.

v1.1.3 changes:

1. In `doVine()`, once `vineSwung` is true, `continueVineTrek()` is tried on every tick **before**
   dialogue handling. Dialogue is only cleared when no Continue path could be clicked. This
   prevents a stale chat-text widget from indefinitely masking an available Continue path.
2. Removed the remaining dialogue-based completion shortcut. The event is now cleared by a region
   change after the swing, or by two successful `Continue-trek` clicks with the vine objects gone.
   A throttled diagnostic warning reports region, dialogue text and Continue-path count if a swing
   produces no click within six seconds.
3. The swing log now records the region and how many Continue-path objects are already cached.
4. Reduced idle time between treks: action cooldown `600 ms → 300 ms` (matching the scheduler),
   route grace `5 s → 1.5 s`, route dialogue timeout `20 s → 12 s`, recovery wait `3 s → 1.5 s`,
   progress watchdog `60 s → 45 s`, state watchdog `180 s → 120 s`.
5. Follower search radius `20 → 35` tiles so the next companion is found immediately at either
   hub. `Escort` is latched as soon as the click succeeds, with destination dialogue still handled,
   so the bot no longer re-clicks the follower while the instance loads.
6. Version bumped to `1.1.3`; JAR rebuilt as `TempleTrekkingPlugin-1.1.3.jar` with
   `-PmicrobotClientVersion=2.6.61`. The auto-fetched `2.6.62` client has moved/removed
   `Rs2GameObject`, which breaks the whole Hub build, so the verified 2.6.61 client was pinned.

Live validation still needed: confirm the vine event clicks `Continue-trek` within a few seconds of
the swing, and that shorter watchdogs do not interrupt slow combat encounters.

## 20. Outfit pieces respect the Farm lumberjacks toggle (v1.1.4)

Reported symptom: Lumberjack outfit pieces were still picked up while **Farm lumberjacks** was off.

Root cause: `TempleTrekkingPlankBridgeHandler.loot()` unconditionally looped over the five
`OUTFIT_DROPS` names after taking Planks. The `farmLumberjacks` flag only controlled whether extra
lumberjacks were killed; it never gated the ground-item pickup.

v1.1.4 changes:

1. `loot()` now returns after Planks when `farmLumberjacks()` is false, leaving outfit pieces on
   the ground.
2. When farming is enabled, only one piece is taken per encounter; `outfitPieceLooted` now also
   blocks further outfit pickups, not just extra kills.
3. Version bumped to `1.1.4`; JAR rebuilt as `TempleTrekkingPlugin-1.1.4.jar`.

Live validation still needed: with the checkbox off, kill only the lumberjacks needed for Planks
and confirm outfit drops remain on the ground.
