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
5. A player who leaves has `battle.disconnect-grace` to come back to this server, then the battle is aborted (no
   rewards, condition kept). An abort the worker never confirms releases the player after `battle.end-timeout`.

## Battle HUD (AstralHologram)

With AstralHologram on the server (optional dependency), a Java player plays the battle in a mouse HUD opened where
they stand, looking level: the opponent and their creature with health bars, the turn and its timer, the last log
lines, and a column of buttons (Attaque, Équipe, Ball, Sac, Fuite, then the moves, team, balls, bag and targets,
paged with **Suivant**, **Retour** to go back). Hovering a move shows its type, category, power and accuracy. The
HUD closes `hud.close-delay` after the battle ends.

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

Not there yet: Bedrock forms, staging and effects; re-attach
after a server switch and recovery on another worker; evolution prompts and choosing which move to forget after a
battle.
