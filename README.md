# PokeBattle

PokeLegends battle system: Showdown bridge to `poke-battle-service` (Node), battle lifecycle, formats, presenter (HUD, VFX, choice UI) and end-of-battle progression through PokeCore.

Modules: `common` (the packet contract shared with the worker), `api` (`com.astralrealms:pokebattle-api`, what other
plugins compile against) and `paper` (the plugin, artifact `com.astralrealms:pokebattle`).

Build: `mvn clean install` with JDK 25 (needs `core-paper` 1.6.3-SNAPSHOT and PokeCore from `maven.astralrealms.fr`
or a local `mvn install`).

## How a battle runs on a server

1. `BattleService.start(BattleRequest.wild(player, creature))`: the player's party members that can fight (fainted
   ones stay out) are packed with neutral labels (`1`..`6`) and their current HP, status and PP. The player is reserved
   here, then network-wide (`pokebattle:player:<uuid>:battle`, `battle.lock-ttl`), and the start goes to the shared
   queue. No reply within `start.timeout` (retried `start.attempts` times with the same id): `NO_WORKER`.
2. Worker packets arrive on `pokebattle.shard.<serverId>`, are put back in order (`seq`) and handled under the
   battle's monitor: lines feed the tracker and the French log (`log-*` messages); requests open the choice dialog;
   a refused choice reopens it.
3. The choice dialog (Java) is built in code, every text a `messages.yml` entry: moves (PP, type, category, power,
   accuracy), then the team. Choices are checked against the request before they leave. `/combat` reopens it;
   `/combat attaque <n>` and `/combat changer <position>` choose without it.
4. `BattleEndedPacket`: the condition every creature left with is always written; experience, EVs and friendship
   (`rewards.*`) only for a win, to the creatures that fought each defeated opponent and still stand. Both go through
   `ProgressionService.settle` in one transaction. Then the lock is released and `BattleEndEvent` fires.
5. A player who leaves has `battle.disconnect-grace` to come back to this server, then the battle is aborted (no
   rewards, condition kept). An abort the worker never confirms releases the player after `battle.end-timeout`.

Admin: `/pokebattle wild <player> <species> [level]`, `/pokebattle abort <player>`, `/pokebattle reload`
(`pokebattle.admin`).

Bundle translation keys the battle texts use: `species|move|type|status|stat|ability|item|condition|category.<id>.name`
(simulator ids; `condition` covers weather, hazards and volatile effects, `category` is `physical|special|status`).

Not there yet: capture, bag, flee and the turn timer (worker); Bedrock forms, HUD, staging and effects; re-attach
after a server switch and recovery on another worker; evolution prompts and choosing which move to forget after a
battle.
