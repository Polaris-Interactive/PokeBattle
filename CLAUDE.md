# PokeBattle

PokeLegends battle system: Showdown bridge to `poke-battle-service` (Node), battle lifecycle, formats, presenter (HUD, VFX, choice UI) and end-of-battle progression through PokeCore.

- Stack and house style: the `astral-plugin-dev` skill (AstralCore `core-paper` 1.6.3-SNAPSHOT, polaris-api 26.3, Java 25).
- Design, decisions and open questions live in the PokeLegends Project Brain at
  `/Volumes/Untitled/Polaris Interactive/pokemon` (read its `BRAIN.md`; use the `brain` CLI from that directory).
  Start with `brain read-page pokebattle-spec`, `repo-conventions`, `content-agnostic-engine`, `ownership-and-anti-dupe`
  and `open-questions-core-battle`.
- Never hard-code creature, move or item names: reference stable ids and resolve display text from the species bundle and
  translation keys (`content-agnostic-engine`). Tests use fictional fixtures.
- Values the GDD does not specify are read from config with a documented default, never hard-coded constants.
- Commit after every change (`feat:`, `fix:`, `build:`, `refactor:`), branch `master`.
