<!--
  Purpose:      Release notes for TrailWright, newest first.
  Dependencies: Version numbers match gradle.properties mod_version.
-->

# Changelog

## Unreleased

### Settings screen
- With Mod Menu installed, TrailWright's entry in the mod list opens a settings screen for
  every option in `trailwright.json`. Changes apply at once in worlds you host. Mod Menu stays
  optional; without it nothing changes.

### Translations
- The F3 wear readout and TrailWright's Mod Menu description are translatable. English is the
  only language included.

## 1.0.0 — 2026-10-24

First release, for Minecraft 1.20.1 on Fabric, and on Forge through Sinytra Connector.

### Wear
- Ground wears where players, their mounts, and mobs actually step: grass, podzol, and
  mycelium become dirt, and dirt becomes coarse dirt, or mud within two blocks of water.
- In the Nether, crimson and warped nylium wear to netherrack.
- Wear builds up gradually and unevenly, so regular routes become ragged trails while a single
  crossing leaves nothing. Unused wear fades over in-game days.
- Short grass and ferns on top are trampled first.
- Sprinting wears faster; sneaking never wears the ground.
- Dirt paths are never made by wear; they stay a shovel choice.

### Mobs and mounts
- Ridden horses, pigs, striders, and camels wear trails (`countMounts`).
- Mobs wear trails at half rate (`countMobs`, `mobMultiplier`), but only grass to dirt, so
  pastures thin out without going barren. Mobs never wear nylium. Small mobs (chickens,
  rabbits, bees, and others) leave no mark.
- With `respectMobGriefing`, the `mobGriefing` gamerule stops mob wear; players are unaffected.

### Restoring ground
- New growth on worn ground (planting flowers, saplings, or grass, or bone meal making plants
  sprout) clears its wear.
- A brush stroke clears a block's wear.
- Replacing a block clears its wear.

### Rules and configuration
- Every progression is a JSON rule that data packs, mods, and players can change. Drop a file
  in `config/trailwright/rules/` and run `/reload`; no data pack needed. The player guide is
  written to `config/trailwright/RULES.md`.
- Rules can target block IDs or tags, branch on water, biome tags, or rain, and are skipped
  quietly when they name blocks from mods that are not installed.
- Server settings in `config/trailwright/trailwright.json`.

### Compatibility
- Stock rules for Biomes O' Plenty, Regions Unexplored, Oh The Biomes We've Gone, and
  Nature's Spirit, including their grass plants.
- Server-side; optional on clients. With TrailWright on the client, ops see the targeted block's
  wear on the F3 screen.
- Tested on Forge 1.20.1-47.4.28 with Connector 1.0.0-beta.49 and Forgified Fabric API
  0.92.6+1.11.15.

---
<sub>Artifact ID: `trailwright-changelog-001` · Last updated: 2026-10-24</sub>
