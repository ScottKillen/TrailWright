<!--
  Purpose:      Project overview and quick start.
  Dependencies: CLAUDE.md, docs/.
-->

# TrailWright

A Minecraft 1.20.1 mod: walk the same route often enough and the ground wears into a trail.
Grass becomes dirt, then coarse dirt; near water it turns to mud instead. Change is gradual
and uneven, and only blocks feet actually land on are affected. Tall grass is knocked down
and grass is trampled away before the ground beneath it starts to wear. Mounts and mobs leave trails
too, though grazing animals never strip a pasture bare. Bone meal, a brush, or planting on
worn ground restores it. Dirt paths stay your choice: use a shovel.

- **Fabric** 1.20.1: drop the jar in `mods/` with Fabric API.
- **Forge** 1.20.1: same jar, plus Sinytra Connector and Forgified Fabric API. Tested on
  Forge 47.4.28 with Connector 1.0.0-beta.49; see `docs/FORGE.md`.
- Server-side logic. Tunable through `config/trailwright/trailwright.json`; wear rules are plain JSON you
  can change without a data pack. See the player guide: [`docs/RULES.md`](docs/RULES.md),
  which also ships in the jar and is written to `config/trailwright/RULES.md` at startup.
- With [Mod Menu](https://modrinth.com/mod/modmenu) installed (optional), the same settings
  are editable in game from TrailWright's entry in the mod list.
- Stock support for Biomes O' Plenty, Regions Unexplored, Oh The Biomes We've Gone, and
  Nature's Spirit.

## License

MIT. See `LICENSE`.
