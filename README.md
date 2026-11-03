# PokeBattle

PokeLegends battle system: Showdown bridge to `poke-battle-service` (Node), battle lifecycle, formats, presenter (HUD, VFX, choice UI) and end-of-battle progression through PokeCore.

Modules: `api` (`com.astralrealms:pokebattle-api`, what other plugins compile against) and `paper` (the plugin,
artifact `com.astralrealms:pokebattle`).

Build: `mvn clean package` (needs `core-paper` 1.6.3-SNAPSHOT from `maven.astralrealms.fr` or a local
`mvn install` of AstralCore).
