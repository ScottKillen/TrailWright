/*
 * Purpose:      Fabric GameTests for rules and the wear engine (TESTING.md T4.1–T4.7, T4.9).
 *               Footfalls are driven through WearEngine.applyFootfall directly, so results do
 *               not depend on simulated player movement. Test-only rules and tags live in
 *               src/gametest/resources and never ship.
 * Dependencies: fabric-gametest-api-v1 (dev only); WearEngine, WearData.
 */
package us.someguy.trailwright.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearEngine;

public final class WearGameTests implements FabricGameTest {
    // Default rules roll a 25–35% chance against a threshold of up to ~27 points per stage;
    // 3000 one-point steps finish both stages with overwhelming probability.
    private static final int MANY = 3000;

    private static final BlockPos SPOT = new BlockPos(2, 1, 1);

    private static void step(GameTestHelper h, BlockPos rel, int times) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(rel);
        for (int i = 0; i < times; i++) WearEngine.applyFootfall(level, abs, 1f, false);
    }

    private static boolean hasWear(GameTestHelper h, BlockPos rel) {
        return WearData.get(h.getLevel()).has(h.absolutePos(rel).asLong());
    }

    /** Stone floor under the test area so nothing depends on what the structure sits on. */
    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    /** Steps one at a time and fails unless {@code expected} is reached through each stage in order. */
    private static void assertProgression(GameTestHelper h, BlockPos rel, Block... expected) {
        int stage = 0;
        for (int i = 0; i < MANY && stage < expected.length - 1; i++) {
            step(h, rel, 1);
            Block now = h.getBlockState(rel).getBlock();
            if (now == expected[stage + 1]) stage++;
            else if (now != expected[stage]) h.fail("unexpected " + now + " after " + expected[stage], rel);
        }
        h.assertBlockPresent(expected[expected.length - 1], rel);
    }

    // T4.1
    @GameTest(template = EMPTY_STRUCTURE)
    public void grassWearsToCoarseDirtAndStops(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        assertProgression(h, SPOT, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT);
        step(h, SPOT, MANY);
        h.assertBlockPresent(Blocks.COARSE_DIRT, SPOT);
        h.assertFalse(hasWear(h, SPOT), "coarse dirt stored wear");
        h.succeed();
    }

    // T4.2: water two blocks away, walled in so it cannot spread.
    @GameTest(template = EMPTY_STRUCTURE)
    public void grassNearWaterWearsToMudAndStops(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(2, 1, 2, Blocks.STONE);
        h.setBlock(1, 1, 3, Blocks.STONE);
        h.setBlock(3, 1, 3, Blocks.STONE);
        h.setBlock(2, 1, 4, Blocks.STONE);
        h.setBlock(2, 1, 3, Blocks.WATER);
        assertProgression(h, SPOT, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.MUD);
        step(h, SPOT, MANY);
        h.assertBlockPresent(Blocks.MUD, SPOT);
        h.assertFalse(hasWear(h, SPOT), "mud stored wear");
        h.succeed();
    }

    // T4.9
    @GameTest(template = EMPTY_STRUCTURE)
    public void nyliumWearsToNetherrackAndStops(GameTestHelper h) {
        floor(h);
        BlockPos crimson = new BlockPos(1, 1, 1), warped = new BlockPos(3, 1, 1);
        h.setBlock(crimson, Blocks.CRIMSON_NYLIUM);
        h.setBlock(warped, Blocks.WARPED_NYLIUM);
        assertProgression(h, crimson, Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK);
        assertProgression(h, warped, Blocks.WARPED_NYLIUM, Blocks.NETHERRACK);
        step(h, crimson, MANY);
        h.assertBlockPresent(Blocks.NETHERRACK, crimson);
        h.assertFalse(hasWear(h, crimson), "netherrack stored wear");
        h.succeed();
    }

    // T4.3
    @GameTest(template = EMPTY_STRUCTURE)
    public void blocksWithoutRulesStoreNothing(GameTestHelper h) {
        floor(h);
        Block[] none = {Blocks.STONE, Blocks.COARSE_DIRT, Blocks.MUD, Blocks.DIRT_PATH};
        for (int i = 0; i < none.length; i++) {
            BlockPos p = new BlockPos(i, 1, 1);
            h.setBlock(p, none[i]);
            step(h, p, 50);
            h.assertBlockPresent(none[i], p);
            h.assertFalse(hasWear(h, p), none[i] + " stored wear");
        }
        h.succeed();
    }

    // T4.4: both blocks have test rules, but a block entity or #trailwright:unwearable blocks them.
    @GameTest(template = EMPTY_STRUCTURE)
    public void blockEntitiesAndUnwearableUntouched(GameTestHelper h) {
        floor(h);
        BlockPos chest = new BlockPos(1, 1, 1), moss = new BlockPos(3, 1, 1);
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(moss, Blocks.MOSS_BLOCK);
        step(h, chest, 20);
        step(h, moss, 20);
        h.assertBlockPresent(Blocks.CHEST, chest);
        h.assertBlockPresent(Blocks.MOSS_BLOCK, moss);
        h.assertFalse(hasWear(h, chest), "chest stored wear");
        h.assertFalse(hasWear(h, moss), "unwearable block stored wear");
        h.succeed();
    }

    /**
     * Steps on {@code rel} until the block above it has gone through each of {@code plant} in
     * order, failing if the ground under it stores wear before the last stage. One step after
     * the plant is gone, the ground must start wearing.
     */
    private static void assertPlantWearsFirst(GameTestHelper h, BlockPos rel, Block ground, Block... plant) {
        BlockPos above = rel.above();
        int stage = 0;
        for (int i = 0; i < MANY && stage < plant.length - 1; i++) {
            step(h, rel, 1);
            Block now = h.getBlockState(above).getBlock();
            if (now == plant[stage + 1]) stage++;
            else if (now != plant[stage]) h.fail("unexpected " + now + " after " + plant[stage], above);
            h.assertFalse(hasWear(h, rel), "ground stored wear under a plant");
            h.assertBlockPresent(ground, rel);
        }
        h.assertBlockPresent(plant[plant.length - 1], above);
        step(h, rel, 1);
        h.assertTrue(hasWear(h, rel), "ground did not wear once the plant was gone");
    }

    // T4.5: a plant on top wears by its own rule and shields the ground until it breaks.
    @GameTest(template = EMPTY_STRUCTURE)
    public void plantOnTopWearsBeforeGround(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(SPOT.above(), Blocks.GRASS);
        step(h, SPOT, 1);
        h.assertBlockPresent(Blocks.GRASS, SPOT.above());
        h.assertTrue(hasWear(h, SPOT.above()), "plant stored no wear");
        h.assertFalse(hasWear(h, SPOT), "ground stored wear under a plant");
        assertPlantWearsFirst(h, SPOT, Blocks.GRASS_BLOCK, Blocks.GRASS, Blocks.AIR);
        h.succeed();
    }

    // T4.5: tall grass is knocked down to short grass (its upper half goes too), then breaks.
    @GameTest(template = EMPTY_STRUCTURE)
    public void tallGrassKnockedDownThenBroken(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        DoublePlantBlock.placeAt(h.getLevel(), Blocks.TALL_GRASS.defaultBlockState(),
                h.absolutePos(SPOT.above()), Block.UPDATE_ALL);
        h.assertBlockPresent(Blocks.TALL_GRASS, SPOT.above(2));
        assertPlantWearsFirst(h, SPOT, Blocks.GRASS_BLOCK, Blocks.TALL_GRASS, Blocks.GRASS, Blocks.AIR);
        h.assertBlockPresent(Blocks.AIR, SPOT.above(2));
        h.succeed();
    }

    // T4.5: the default plant rules set mobs_can_advance, so mobs wear plants on grass too.
    @GameTest(template = EMPTY_STRUCTURE)
    public void mobFootfallWearsPlant(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(SPOT.above(), Blocks.FERN);
        WearEngine.applyFootfall(h.getLevel(), h.absolutePos(SPOT), 0.5f, true);
        h.assertTrue(hasWear(h, SPOT.above()), "mob footfall stored no wear on the plant");
        h.assertFalse(hasWear(h, SPOT), "ground stored wear under a plant");
        h.succeed();
    }

    // T8.4: a pack extending #trailwright:trampled_first (the test mod adds dead_bush) gets the
    // same plant-first wear as vanilla grass through the tag's rule; this is the path mod
    // plants take.
    @GameTest(template = EMPTY_STRUCTURE)
    public void trampledFirstTagIsExtensible(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(SPOT.above(), Blocks.DEAD_BUSH);
        assertPlantWearsFirst(h, SPOT, Blocks.GRASS_BLOCK, Blocks.DEAD_BUSH, Blocks.AIR);
        h.succeed();
    }

    // T4.6: higher-priority pack rule overrides the default (podzol -> sand, not dirt).
    @GameTest(template = EMPTY_STRUCTURE)
    public void higherPriorityRuleOverridesDefault(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.PODZOL);
        step(h, SPOT, 1);
        h.assertBlockPresent(Blocks.SAND, SPOT);
        h.succeed();
    }

    // Tag rules apply to tag members; at equal priority an ID rule beats a tag rule.
    @GameTest(template = EMPTY_STRUCTURE)
    public void tagRuleAndIdBeatsTag(GameTestHelper h) {
        floor(h);
        BlockPos white = new BlockPos(1, 1, 1), red = new BlockPos(3, 1, 1);
        h.setBlock(white, Blocks.WHITE_WOOL);
        h.setBlock(red, Blocks.RED_WOOL);
        step(h, white, 1);
        step(h, red, 1);
        h.assertBlockPresent(Blocks.HAY_BLOCK, white);
        h.assertBlockPresent(Blocks.SPONGE, red);
        h.succeed();
    }

    // A list rule naming unregistered blocks, tags, and targets still applies to the rest.
    @GameTest(template = EMPTY_STRUCTURE)
    public void unknownIdsAreSkipped(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.CLAY);
        step(h, SPOT, 1);
        h.assertBlockPresent(Blocks.GRAVEL, SPOT);
        h.succeed();
    }

    // T4.7: config/trailwright/rules/override.json (priority 0) beats a priority-50 pack rule.
    @GameTest(template = EMPTY_STRUCTURE)
    public void configFolderRuleOverridesDataPack(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.SMOOTH_STONE);
        step(h, SPOT, 1);
        h.assertBlockPresent(Blocks.ANDESITE, SPOT);
        h.succeed();
    }

    // legacy_open_above.json has an unknown branch key: it must still load and convert, and a
    // flower on top that cannot live on the new block breaks.
    @GameTest(template = EMPTY_STRUCTURE)
    public void removedKeyIgnoredAndPlantOnTopBreaks(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.PACKED_MUD);
        h.setBlock(SPOT.above(), Blocks.POPPY);
        step(h, SPOT, 1);
        h.assertBlockPresent(Blocks.DIRT_PATH, SPOT);
        h.assertBlockPresent(Blocks.AIR, SPOT.above());
        h.succeed();
    }
}
