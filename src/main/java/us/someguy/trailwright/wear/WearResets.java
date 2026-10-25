/*
 * Purpose:      Clears stored wear when ground is restored or replaced (DESIGN.md §4.6):
 *               any change of block type, new growth on top (a block from
 *               #trailwright:restores_ground_below appearing above, whether planted, grown by
 *               bone meal, or spread), and a brush stroke.
 * Dependencies: WearData; Minecraft block tags. Called from ServerLevelMixin and
 *               BrushItemMixin.
 */
package us.someguy.trailwright.wear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import us.someguy.trailwright.TrailWright;

public final class WearResets {
    public static final TagKey<Block> RESTORES_GROUND_BELOW =
            TagKey.create(Registries.BLOCK, new ResourceLocation(TrailWright.MOD_ID, "restores_ground_below"));

    private WearResets() {}

    /**
     * Runs for every block change on the server, so it must stay nearly free: one cached
     * field read, then an early return unless wear is stored and the block type changed.
     */
    public static void onBlockStateChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        WearData data = WearData.get(level);
        if (data.isEmpty() || oldState.getBlock() == newState.getBlock()) return;
        long packed = pos.asLong();
        data.reset(packed);
        // New growth on worn ground restores it, whatever caused it: planting, bone meal
        // (grass bone meal places plants on the clicked block first), or natural spread.
        if (newState.is(RESTORES_GROUND_BELOW)) data.reset(BlockPos.offset(packed, Direction.DOWN));
    }

    /** One brush stroke on the block at {@code pos}. */
    public static void onBrushStroke(ServerLevel level, BlockPos pos) {
        WearData.get(level).reset(pos.asLong());
    }
}
