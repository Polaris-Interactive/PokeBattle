# PokeBattle

PokeSMP battle system: Showdown bridge to `poke-battle-service` (Node), battle lifecycle, formats, presenter (HUD, VFX, choice UI) and end-of-battle progression through PokeCore.

Modules: `common` (the packet contract shared with the worker), `api` (`com.astralrealms:pokebattle-api`, what other
plugins compile against) and `paper` (the plugin, artifact `com.astralrealms:pokebattle`).

Build: `mvn clean install` with JDK 25 (needs `core-paper` 1.6.3-SNAPSHOT and PokeCore from `maven.astralrealms.fr`
or a local `mvn install`).

## How a battle runs on a server

1. `BattleService.start(BattleRequest.wild(player, signedRoll[, captureFlags]))`: the player's party is packed with
   neutral labels (`1`..`6`) and its current HP, status and PP, standing members first (the lead must stand); fainted
   ones come last, only there to be revived from the bag. The wild creature is the roll PokeCore signed when it
   appeared: catching it redeems that roll. The player is reserved
   here, then network-wide (`pokebattle:player:<uuid>:battle`, `battle.lock-ttl`), and the start goes to the shared
   queue. No reply within `start.timeout` (retried `start.attempts` times with the same id): `NO_WORKER`.
2. Worker packets arrive on `pokebattle.shard.<serverId>`, are put back in order (`seq`) and handled under the
   battle's monitor: lines feed the tracker and the French log (`log-*` messages); requests open the choice UI;
   a refused choice reopens it.
3. The choice UI is the **battle HUD** when AstralHologram is installed (below), otherwise the **choice dialog**,
   built in code, every text a `messages.yml` entry: moves (PP, type, category, power, accuracy), then the team,
   **Ball**, **Sac** and **Fuite**. Choices are checked against the request before they leave. `/combat` reopens
   it; `/combat attaque <n>`, `/combat changer <position>` and `/combat fuite` choose without it.
   - **Ball:** the balls the player carries (items PokeCore maps to a ball). The capture is **rolled here**
     (`CaptureService`, ball rules with the request's capture flags, the foe's HP from its percentage) and the result
     goes with the choice; the worker ends the battle on a catch.
   - **Sac:** items whose blueprint fixes an effect the simulator applies (`poke-heal`, `poke-revive`,
     `poke-cure-status`, `poke-restore-pp`, read through `ItemMappingService.fixedEffects`), then a target among the
     members it would change. Same YAML in and out of battle.
   - The ball or item is taken from the inventory when the choice leaves, and given back if the worker refuses it.
   - The turn timer runs on the worker (`battle.turn-timer-seconds`): when it runs out the AI chooses and the log says
     so.
4. `BattleEndedPacket`: the condition every creature left with is always written; experience, EVs and friendship
   (`rewards.*`) only for a win or a catch (`rewards.experience-on-capture`), to the creatures that fought each
   defeated or caught opponent and still stand. Both go through `ProgressionService.settle` in one transaction. A
   catch first redeems the roll (`CreationService.redeem`: party, else PC), and the caught creature keeps the
   condition the battle left it in. Then the lock is released and `BattleEndEvent` fires (outcomes `WON`, `CAPTURED`,
   `LOST`, `TIE`, `FLED`, `ABORTED`).
   The event carries the request's `tag` (also on `BattleStartEvent`), kept in the Redis context so it is right on
   whichever server ends the battle.
5. A player who leaves has `battle.disconnect-grace` to come back to this server, then the battle is aborted (no
   rewards, condition kept). An abort the worker never confirms releases the player after `battle.end-timeout`.

## Trainer battles

`BattleRequest.trainer(player, new TrainerOpponent(name, team, ai), format, rules, tag)` battles an NPC trainer:
`team` is 1 to 6 PokeCore `TrainerMember`s (species and form ids, level, optional moves, ability, item, nature,
IVs, EVs, gender, shininess, nickname). At the start PokeCore checks the team (`CreationService.validate`, refused as
`ERROR` with the problems logged) and rolls what it leaves out once (`CreationService.complete`). The opponent side
is sent with the trainer's name and `ai` (`trainer`, `gym`), with no condition (a fresh team).

- `BattleRules` (`WILD`, `TRAINER`, `GYM`): bag, capture and flee for the player (a trainer's creature is never
  caught), and an experience multiplier on top of `rewards.experience-multiplier` (1.5 for trainers and gyms).
- Formats `polaristrainer` and `polarisgym` (`BattleRequest.TRAINER_FORMAT`, `GYM_FORMAT`) are the worker's.
- The log names the trainer (`log-trainer-challenge`, `log-trainer-sends`, `foe-name-trainer`) and so do the end
  messages (`battle-won-trainer`, `battle-lost-trainer`). The stage shows the trainer's active creature and changes it
  on every switch. Prize money, badges, level caps and the NPC itself belong to the caller (PokeTrainers), through
  the tag on `BattleEndEvent`.

## Recovery and re-attach

- **Only one shard settles a battle:** it claims `settledBy` in the battle's Redis hash first.
- **The battle's context** (format, player, creature ids, foes, signed roll, options) is saved in Redis
  (`pokebattle:battle:<id>:context`) before the start, with the lock's TTL.
- **Dead worker:** every `recovery.check-interval` the shard compares each battle's worker `instance` with that worker's
  heartbeat. If it is gone, the shard sends `BattleRecoverPacket` to the start queue. Another worker replays the
  battle, and the shard starts its view over from the resync update (`|polaris-resync|`, sequence 0). An end the shard
  missed (stored in the hash as `ended`) is settled from Redis.
- **Server switch, or a stopped shard:** a player who joins with a battle lock gets the battle rebuilt from its context
  (creatures read back from their storage). The shard records itself in the hash, then:
  - attaches to the worker (`BattleAttachPacket`, which resends the battle from 0);
  - recovers it, if its worker is dead;
  - or settles its stored end.
  The shard they left lets the battle go once the hash names another shard, instead of aborting it after the grace.
- **Shutdown no longer aborts battles.** They run on and are re-attached when their players come back. A player who
  never returns is aborted by the worker after `ABSENT_TURNS` turn timers in a row.
- **Limits:**
  - A choice sent just before a worker died can be lost; the new worker asks again. A ball or item spent on it counts
    as used.
  - A battle whose end is only settled from Redis (no lines seen by the settling shard) gets the creatures' condition
    but may miss rewards earned in its last lines.

## Arenas (PolarisInstances)

Gyms and ranked battles take place in **arenas**: one instance per battle on an arena server (VoidPaper with
PolarisInstances and the Poké stack), built from an instance template.

1. **The player's server** (with PolarisInstances-Bridge) asks for an instance of type `pokebattle-arena` with the
   arena's template (`templates/pokebattle-arena/<template>.yml` on the arena servers). The battle
   travels in the metadata as JSON (`pokebattle.battle`, `ArenaBattle`). Try it with
   `/pokebattle arena <player> <template> <species> [level]`.
2. **The arena server** (with PolarisInstances) starts the battle when the player enters the instance. The battle is
   staged at the template's `arena.stage-spawn` spawn point (default `stage`): the player's side stands there, facing
   the opponent. Without that spawn point, the battle is laid in front of where the player arrives. A caught creature
   is recorded as met in the template.
3. When the battle ends, the arena closes after `arena.return-delay` and the player goes back to their server. If they
   leave mid-battle, the battle is re-attached wherever they join next, like any other.

The arena server rolls the wild opponent itself, so its roll can be redeemed there. Trainer, gym and ranked battles
will add their own fields to `ArenaBattle`. A template only needs:

```yaml
schematics:
  - { file: arena.schem, position: [0, 64, 0] }
spawns:
  default: { x: 0.5, y: 65, z: -6.5, yaw: 0, pitch: 0 }
  stage: { x: 0.5, y: 65, z: -3.5, yaw: 0, pitch: 0 }
```

## Battle HUD (AstralHologram)

With AstralHologram on the server (optional dependency), a Java player plays the battle in a mouse HUD laid out like
a handheld battle screen: their creature's card top left and the opponent's top right (health bars), the turn and
its timer between them, a grid of buttons bottom left (Attaque, Équipe, Capture, Sac, Fuite, then the moves, team,
balls, bag and targets, paged with **Suivant**, **Retour** to go back) and the last log lines bottom right. Hovering a
move shows its type, category, power and accuracy. The HUD closes `hud.close-delay` after the battle ends.

The camera follows the battle (`hud.camera`). The stage is laid in front of where the player stood when the battle
started: the two creatures' spots, 6 blocks apart. Each update plays a shot per cue (a move from either side, a switch,
a K.O., a capture, the end), then the camera goes back to `idle`, over the shoulder of the player's creature, and only
then does the menu show. A run lasts at most `max-sequence` (GDD: 6 s), and **F** skips it. The HUD glides with the
camera, so it stays on screen.

- Built in code (`ui/hud`): texts are `messages.yml` entries (`hud-*`, plus the dialog's button texts), placement
  and looks are `hud` in `config.yml`. `/hologram hud edit` while the HUD is open prints `position:` blocks that
  paste into `hud.elements`; `/pokebattle reload` applies them to the next HUD.
- `hud.closeable: false` by default: sneaking does not leave a battle. If the HUD closes anyway (another HUD, a
  teleport, an AstralHologram reload), the log goes to the chat and the HUD comes back with the next choice or
  `/combat`.
- The dialog is used instead with `hud.enabled: false`, for Bedrock players (Floodgate UUIDs: display entities do not
  exist on Bedrock), and for the rest of a battle whose HUD could not open.

Admin: `/pokebattle wild <player> <species> [level]`, `/pokebattle abort <player>`, `/pokebattle reload`
(`pokebattle.admin`).

Bundle translation keys the battle texts use: `species|move|type|status|stat|ability|item|condition|category.<id>.name`
(simulator ids; `condition` covers weather, hazards and volatile effects, `category` is `physical|special|status`).

Not there yet: Bedrock forms, staging and effects; evolution prompts and choosing which move to forget after a
battle.
