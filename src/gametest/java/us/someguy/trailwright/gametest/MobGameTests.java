/*
 * Purpose:      Fabric GameTests for mob trails (TESTING.md T7.1–T7.3, T7.5, T7.7) and for running
 *               with no rules at all (T7.6). Filters are driven through
 *               WearEngine.onMobFootfall; one test moves a real cow so the MobMixin path runs.
 *               Tests that change shared state (gamerules, config, the active rule set) run
 *               in their own batches and restore it before finishing.
 * Dependencies: fabric-gametest-api-v1 (dev only); WearEngine, WearData, WearRules,
 *               WearInfoPackets, TrailWrightConfig.
 */
package us.someguy.trailwright.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.net.WearInfoPackets;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearEngine;
import us.someguy.trailwright.wear.WearRules;

import java.util.List;
import java.util.Optional;

public final class MobGameTests implements FabricGameTest {
    private static final int MANY = 3000;
    private static final BlockPos SPOT = new BlockPos(2, 1, 2);

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(x, 0, z, Blocks.STONE);
    }

    private static boolean hasWear(GameTestHelper h, BlockPos rel) {
        return WearData.get(h.getLevel()).has(h.absolutePos(rel).asLong());
    }

    private static void mobSteps(GameTestHelper h, BlockPos rel, Mob mob, int times) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(rel);
        for (int i = 0; i < times; i++) WearEngine.onMobFootfall(level, abs, mob);
    }

    // T7.1
    @GameTest(template = EMPTY_STRUCTURE)
    public void cowWearsGrassToDirtAndStops(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        Cow cow = h.spawn(EntityType.COW, SPOT.above(2));
        for (int i = 0; i < MANY && !h.getBlockState(SPOT).is(Blocks.DIRT); i++) mobSteps(h, SPOT, cow, 1);
        h.assertBlockPresent(Blocks.DIRT, SPOT);
        mobSteps(h, SPOT, cow, MANY);
        h.assertBlockPresent(Blocks.DIRT, SPOT);
        h.assertFalse(hasWear(h, SPOT), "mob footfalls recorded wear on dirt");
        cow.discard();
        h.succeed();
    }

    // T7.2
    @GameTest(template = EMPTY_STRUCTURE)
    public void lightFootedMobStoresNothing(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        Mob chicken = h.spawn(EntityType.CHICKEN, SPOT.above(2));
        mobSteps(h, SPOT, chicken, 50);
        h.assertFalse(hasWear(h, SPOT), "chicken recorded wear");
        chicken.discard();
        h.succeed();
    }

    // T7.7: the nylium rules leave mobs_can_advance off, so Nether mobs never wear it.
    @GameTest(template = EMPTY_STRUCTURE)
    public void mobOnNyliumStoresNothing(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.CRIMSON_NYLIUM);
        Mob hoglin = h.spawn(EntityType.HOGLIN, SPOT.above(2));
        mobSteps(h, SPOT, hoglin, MANY);
        h.assertBlockPresent(Blocks.CRIMSON_NYLIUM, SPOT);
        h.assertFalse(hasWear(h, SPOT), "hoglin recorded wear on nylium");
        hoglin.discard();
        h.succeed();
    }

    // A player's mount is counted by the player hook or not at all, never as a mob.
    @GameTest(template = EMPTY_STRUCTURE)
    public void playerRiddenMobStoresNothing(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        Pig pig = h.spawn(EntityType.PIG, SPOT.above(2));
        Player rider = h.makeMockPlayer();
        rider.startRiding(pig, true);
        h.assertTrue(pig.hasPassenger(rider), "test assumption: rider is mounted");
        mobSteps(h, SPOT, pig, 50);
        h.assertFalse(hasWear(h, SPOT), "player-ridden pig recorded wear");
        rider.stopRiding();
        pig.discard();
        h.succeed();
    }

    // End to end through MobMixin: a real cow teleported between two grass blocks.
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void realCowWearsThroughMixin(GameTestHelper h) {
        floor(h);
        BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(3, 1, 2);
        h.setBlock(a, Blocks.GRASS_BLOCK);
        h.setBlock(b, Blocks.GRASS_BLOCK);
        Cow cow = h.spawn(EntityType.COW, a.above());
        for (int i = 1; i <= 60; i++) {
            BlockPos target = (i % 2 == 0) ? a : b;
            h.runAtTickTime(i * 4L, () -> {
                Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(target.above()));
                cow.teleportTo(v.x, v.y, v.z);
            });
        }
        h.runAtTickTime(260, () -> {
            for (BlockPos p : List.of(a, b)) {
                boolean worn = hasWear(h, p) || h.getBlockState(p).is(Blocks.DIRT);
                h.assertTrue(worn, "cow left no wear at " + p);
            }
            cow.discard();
            h.succeed();
        });
    }

    // A higher-priority rule with an empty `next` disables a lower one and records nothing.
    @GameTest(template = EMPTY_STRUCTURE)
    public void emptyNextRuleDisablesAndStoresNothing(GameTestHelper h) {
        floor(h);
        h.setBlock(SPOT, Blocks.TERRACOTTA);
        ServerLevel level = h.getLevel();
        for (int i = 0; i < 20; i++) WearEngine.applyFootfall(level, h.absolutePos(SPOT), 1f, false);
        h.assertBlockPresent(Blocks.TERRACOTTA, SPOT);
        h.assertFalse(hasWear(h, SPOT), "a rule that changes nothing recorded wear");
        h.succeed();
    }

    // T7.3: mobGriefing off stops mob wear only; countMobs off stops it too.
    @GameTest(template = EMPTY_STRUCTURE, batch = "trailwright_settings")
    public void griefingAndCountMobsGateMobsOnly(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        BlockPos mobSpot = new BlockPos(1, 1, 2), playerSpot = new BlockPos(3, 1, 2);
        h.setBlock(mobSpot, Blocks.GRASS_BLOCK);
        h.setBlock(playerSpot, Blocks.GRASS_BLOCK);
        Cow cow = h.spawn(EntityType.COW, mobSpot.above(2));
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean savedGriefing = griefing.get();
        TrailWrightConfig cfg = TrailWrightConfig.get();
        boolean savedCountMobs = cfg.countMobs;
        try {
            griefing.set(false, level.getServer());
            mobSteps(h, mobSpot, cow, 20);
            h.assertFalse(hasWear(h, mobSpot), "mob wore ground with mobGriefing off");
            WearEngine.applyFootfall(level, h.absolutePos(playerSpot), 1f, false);
            h.assertTrue(hasWear(h, playerSpot), "player wear blocked by mobGriefing");

            griefing.set(true, level.getServer());
            cfg.countMobs = false;
            mobSteps(h, mobSpot, cow, 20);
            h.assertFalse(hasWear(h, mobSpot), "mob wore ground with countMobs off");
        } finally {
            griefing.set(savedGriefing, level.getServer());
            cfg.countMobs = savedCountMobs;
            cow.discard();
        }
        h.succeed();
    }

    // T7.6: with no active rules the mod does nothing and nothing fails.
    @GameTest(template = EMPTY_STRUCTURE, batch = "trailwright_no_rules")
    public void noRulesFailsGracefully(GameTestHelper h) {
        floor(h);
        ServerLevel level = h.getLevel();
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(SPOT.above(), Blocks.GRASS);
        BlockPos abs = h.absolutePos(SPOT);
        Cow cow = h.spawn(EntityType.COW, new BlockPos(0, 1, 0));
        List<WearRules.Loaded> saved = WearRules.loaded();
        try {
            WearRules.setLoaded(List.of());
            WearRules.rebuild();
            h.assertTrue(WearRules.isEmpty(), "rules not cleared");

            for (int i = 0; i < 50; i++) {
                WearEngine.applyFootfall(level, abs, 1f, false);
                WearEngine.onMobFootfall(level, abs, cow);
            }
            h.assertBlockPresent(Blocks.GRASS_BLOCK, SPOT);
            h.assertBlockPresent(Blocks.GRASS, SPOT.above());   // not trampled either
            h.assertFalse(hasWear(h, SPOT), "wear recorded with no rules");

            Optional<WearInfoPackets.Info> info =
                    WearInfoPackets.answer(level, Vec3.atCenterOf(abs).add(0, 2, 0), true, abs);
            h.assertTrue(info.isPresent() && !info.get().hasRule(), "F3 answer should be 'no rule'");

            // Reset and prune paths must cope too.
            h.setBlock(SPOT.above(), Blocks.POPPY);
            WearData.get(level).prune(level.getGameTime(), 2f);
        } finally {
            WearRules.setLoaded(saved);
            WearRules.rebuild();
            cow.discard();
        }
        h.assertFalse(WearRules.isEmpty(), "rules not restored");
        h.succeed();
    }
}
