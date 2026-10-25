<!--
  Purpose:      Player guide and reference for TrailWright wear rules: where they live, how to
                write them, copy-paste examples, tags, and troubleshooting.
  Dependencies: Parsed by wear/WearRule.java (codec) and wear/WearRuleReloader.java.
                Defaults live in src/main/resources/data/trailwright/wear_rules/.
-->

# Writing your own wear rules

TrailWright decides how ground wears using **rules**: small JSON files, one per kind of block.
You can change any default, add blocks from other mods, or switch wear off for a block,
without writing code.

This guide ships inside TrailWright. Every launch keeps a copy at `config/trailwright/RULES.md`.

## Quick start (no data pack needed)

1. Open your Minecraft folder's `config/trailwright/rules/` (on a server, the server's folder).
   TrailWright creates it on first launch, with a disabled `example.json.disabled` inside.
2. Create a file ending in `.json`, for example `my_rules.json`, and paste one of the examples
   below into it.
3. In game, run `/reload` (needs op), or restart.
4. Check the log. A broken file is reported by name, for example
   `Wear rule trailwright:config/my_rules.json is invalid and was skipped: ...`, and the rest
   keep working.

Rules in `config/trailwright/rules/` always win over rules from data packs and mods for the same
block, so this folder is the place for personal tweaks.

**Data packs** work too: put the same files in `data/<your_namespace>/wear_rules/` inside a
pack. That is how mods and modpacks ship rules.

## What a rule looks like

```json
{
  "block": "minecraft:dirt",
  "base_threshold": 16,
  "jitter": 0.35,
  "advance_chance": 0.30,
  "next": [
    { "when": { "type": "near_water", "radius": 2 }, "to": "minecraft:mud" },
    { "when": { "type": "always" }, "to": "minecraft:coarse_dirt" }
  ]
}
```

Read it as: *dirt wears after about 16 footfalls. Once it is past that, each further
footfall has a 30% chance to change it. Within 2 blocks of water it becomes mud; otherwise it
becomes coarse dirt.*

How wear builds up: each footfall adds 1 point (1.5 sprinting; mobs 0.5; sneaking adds
nothing). When a block's points reach its threshold, every further footfall rolls
`advance_chance`. When it changes, its points reset. Points fade by 2 per in-game day if
nobody walks there (`decayPerDay` in `config/trailwright/trailwright.json`).

| Field | Required | Meaning |
|---|---|---|
| `block` | yes | The block(s) this rule covers: an ID (`"minecraft:dirt"`), a tag (`"#minecraft:dirt"`), or a list of either. |
| `base_threshold` | yes | Wear points needed before the block can change. Bigger is slower. |
| `advance_chance` | yes | Chance (0 to 1) per footfall, once over the threshold, that the block changes. |
| `next` | yes | What the block becomes, as an ordered list of branches; the first whose `when` matches is used. An empty list `[]` means the block never changes. |
| `next[].to` | yes | The new block. |
| `next[].when` | yes | A condition (below). Use `{ "type": "always" }` for "otherwise". |
| `jitter` | no, default `0.35` | How uneven the edges are (0 to 0.9). Each block's threshold varies by up to this fraction, so trails come out ragged instead of straight-edged. |
| `priority` | no, default `0` | When two rules cover the same block, the higher priority wins (see below). |
| `mobs_can_advance` | no, default `false` | `true` lets mob footfalls wear this block. Leave it `false` for blocks animals should not strip bare. |

Keys TrailWright does not know, such as `"_comment"`, are ignored, so you can leave notes.

**Plants on top** (`#trailwright:trampled_first`, see Related tags) wear by their own rules
before the ground under them does. A footfall's points go to the plant, and the ground only
starts wearing once the plant is gone. A plant's `to` of `"minecraft:air"` breaks it with
drops; any other `to` replaces it (the default knocks tall grass down to short grass).

When a block changes, anything on top that cannot live on the new block breaks and drops, as
in vanilla. For example, a flower on coarse dirt pops off if a rule turns that coarse dirt
into a dirt path.

## Conditions

| `type` | Extra fields | Matches when |
|---|---|---|
| `always` | none | Always. Put it last as the fallback. |
| `near_water` | `radius`: 1–4, default 2 | Water is within `radius` blocks sideways, level with the block or one below. |
| `biome_tag` | `tag`, e.g. `"#minecraft:is_forest"` | The block's biome is in that tag. |
| `raining` | none | It is raining on that spot right now. |

## Examples

**Worn coarse dirt turns into a dirt path** (the shipped `example.json.disabled`):

```json
{
  "block": "minecraft:coarse_dirt",
  "base_threshold": 20,
  "advance_chance": 0.25,
  "next": [
    { "when": { "type": "always" }, "to": "minecraft:dirt_path" }
  ]
}
```

**No mud: dirt always becomes coarse dirt** (replaces the default dirt rule):

```json
{
  "block": "minecraft:dirt",
  "base_threshold": 16,
  "advance_chance": 0.30,
  "next": [ { "when": { "type": "always" }, "to": "minecraft:coarse_dirt" } ]
}
```

**Grass wears twice as fast:**

```json
{
  "block": "minecraft:grass_block",
  "base_threshold": 6,
  "advance_chance": 0.35,
  "mobs_can_advance": true,
  "next": [ { "when": { "type": "always" }, "to": "minecraft:dirt" } ]
}
```

(To speed up *everything*, set `wearMultiplier` in `config/trailwright/trailwright.json` instead.)

**Stop a block from wearing at all:**

```json
{ "block": "minecraft:podzol", "base_threshold": 1, "advance_chance": 0, "next": [] }
```

**Red sand in badlands wears to coarse dirt; elsewhere it never changes:**

```json
{
  "block": "minecraft:red_sand",
  "base_threshold": 30,
  "advance_chance": 0.2,
  "next": [ { "when": { "type": "biome_tag", "tag": "#minecraft:is_badlands" }, "to": "minecraft:coarse_dirt" } ]
}
```

A block with no branch that matches simply keeps its wear and tries again on the next
footfall.

**Grass plants survive longer underfoot** (replaces the shipped rule that covers every plant
in the tag):

```json
{
  "block": "#trailwright:trampled_first",
  "base_threshold": 10,
  "advance_chance": 0.3,
  "mobs_can_advance": true,
  "next": [ { "when": { "type": "always" }, "to": "minecraft:air" } ]
}
```

**Tall grass breaks outright instead of becoming short grass:**

```json
{
  "block": "minecraft:tall_grass",
  "base_threshold": 3,
  "advance_chance": 0.5,
  "mobs_can_advance": true,
  "next": [ { "when": { "type": "always" }, "to": "minecraft:air" } ]
}
```

**A block from another mod:** use its ID exactly as F3 shows it in the targeted-block
section, for example `"examplemod:mossy_soil"`. If that mod is not installed, the rule is
skipped quietly.

## Which rule wins

When several rules cover the same block:

1. A rule in `config/trailwright/rules/` beats any data-pack or mod rule.
2. Otherwise the higher `priority` wins outright. Rules are never merged.
3. On a tie, a rule naming the block by ID beats one that matches it through a tag.
4. Still tied: the data pack loaded later wins (in the config folder: the file name later in
   alphabetical order).

## Related tags

Tags are lists that data packs can add to (`"replace": false`). They live in
`data/trailwright/tags/blocks/` or `data/trailwright/tags/entity_types/`.

| Tag | Kind | Default contents | Effect |
|---|---|---|---|
| `#trailwright:unwearable` | block | empty | Never worn, even if a rule covers it. |
| `#trailwright:trampled_first` | block | short and tall grass and ferns, plus grass plants from supported mods (no flowers) | A plant in this tag sitting on a wearable block wears by its own rule before the block does. Shipped rules: tall grass → short grass, large fern → fern, everything else in the tag → broken with drops. A plant in the tag with no rule is ignored. |
| `#trailwright:restores_ground_below` | block | flowers, saplings, short and tall grass, ferns, plus grass plants from supported mods | When one appears (planted, grown by bone meal, or spread), the block underneath has its wear cleared. |
| `#trailwright:light_footed` | entity type | chicken, rabbit, frog, tadpole, bee, silverfish, endermite, bat, parrot, allay, vex, phantom | These mobs never wear the ground. |

## Supported mods

TrailWright ships rules and tag entries for these mods' blocks. They load only when the mod is
installed, and are ignored otherwise.

| Mod (1.20.1 version checked) | Covered |
|---|---|
| Biomes O' Plenty (19.0.0.96) | origin grass → dirt; its short grass plants |
| Regions Unexplored (0.5.6) | alpha grass → dirt; peat and silt grass/podzol → their dirt → their coarse dirt, or their mud near water; grass on stone, deepslate, argillite, and chalk wears down to the bare stone; its grass plants |
| Oh The Biomes We've Gone (1.8.2) | lush grass → lush dirt → coarse dirt, or mud near water; podzol on (white) dacite wears to the bare dacite; its grass plants |
| Nature's Spirit (2.2.5) | uses vanilla grass and dirt, which already wear; its grass plants wear first and restore ground |

Soils with no clear worn form are left alone: BWG sandy dirt and peat, Nature's Spirit sandy
soil, and RU ashen dirt. Add rules for them in `config/trailwright/rules/` if you want them to
wear.

## Seeing wear in game

With TrailWright installed on your client too, ops see the targeted block's wear on the F3 screen:
`TrailWright wear: 7.30 / 12.41` (points so far / this block's threshold), or `TrailWright: no rule`.

## Troubleshooting

| Log message | Meaning |
|---|---|
| `Wear rule ... is invalid and was skipped: <reason>` | The file has a mistake, such as a missing field, a typo in a condition `type`, or `advance_chance` above 1. Only that file is skipped. |
| `Wear rule ... could not be read: ...` | The file is not valid JSON, often a missing comma or brace. |
| `No wear rules are active, so TrailWright will not wear any ground` | Every rule is disabled, or TrailWright's built-in rules are switched off (`/datapack list`). |
| `Wear rules active for N blocks from M rule files` | Normal. Rules for mods you do not have are counted as files but cover no blocks. |

---
<sub>Artifact ID: `trailwright-rules-001` · Last updated: 2026-10-24</sub>
