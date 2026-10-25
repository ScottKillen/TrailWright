/*
 * Purpose:      Fabric GameTests for the F3 readout's server answer (TESTING.md T6.1):
 *               ops in range of a loaded block get an answer; non-ops, distant blocks, and
 *               unloaded chunks get none.
 * Dependencies: fabric-gametest-api-v1 (dev only); WearInfoPackets, WearData.
 */
package us.someguy.trailwright.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import us.someguy.trailwright.net.WearInfoPackets;
import us.someguy.trailwright.net.WearInfoPackets.Info;
import us.someguy.trailwright.wear.WearData;

import java.util.Optional;

public final class ReadoutGameTests implements FabricGameTest {
    private static final BlockPos SPOT = new BlockPos(2, 1, 2);

    @GameTest(template = EMPTY_STRUCTURE)
    public void serverAnswersOnlyOpsInRangeOfLoadedBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos grass = h.absolutePos(SPOT);
        BlockPos stone = h.absolutePos(SPOT.east());
        h.setBlock(SPOT, Blocks.GRASS_BLOCK);
        h.setBlock(SPOT.east(), Blocks.STONE);
        WearData.get(level).addWear(grass.asLong(), 3f, level.getGameTime(), 0f);
        Vec3 eye = Vec3.atCenterOf(grass).add(0, 2, 0);

        Optional<Info> worn = WearInfoPackets.answer(level, eye, true, grass);
        h.assertTrue(worn.isPresent() && worn.get().hasRule(), "op got no answer for worn grass");
        h.assertTrue(Math.abs(worn.get().wear() - 3f) < 0.01f, "wrong wear " + worn.map(Info::wear).orElse(-1f));
        h.assertTrue(worn.get().threshold() > 0f, "threshold missing");

        Optional<Info> noRule = WearInfoPackets.answer(level, eye, true, stone);
        h.assertTrue(noRule.isPresent() && !noRule.get().hasRule(), "stone should answer 'no rule'");

        h.assertTrue(WearInfoPackets.answer(level, eye, false, grass).isEmpty(), "non-op got an answer");
        h.assertTrue(WearInfoPackets.answer(level, eye.add(30, 0, 0), true, grass).isEmpty(), "out-of-range answer");

        // Far enough that the chunk cannot be loaded; the eye is moved along so only the
        // loaded-chunk check can reject it.
        BlockPos far = grass.offset(1_000_000, 0, 0);
        h.assertFalse(level.isLoaded(far), "test assumption: far chunk is loaded");
        h.assertTrue(WearInfoPackets.answer(level, Vec3.atCenterOf(far), true, far).isEmpty(), "unloaded chunk answered");
        h.assertFalse(level.isLoaded(far), "answer loaded a chunk");
        h.succeed();
    }
}
