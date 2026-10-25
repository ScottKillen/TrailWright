/*
 * Purpose:      F3 wear readout protocol (DESIGN.md §10): channel IDs, the reply payload and
 *               its encoding, and the server's answer to a query. Loader-agnostic; the Fabric
 *               receivers are registered in TrailWrightFabric and TrailWrightClient.
 * Dependencies: Minecraft FriendlyByteBuf / level types; WearRules, WearData, WearEngine.
 */
package us.someguy.trailwright.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import us.someguy.trailwright.TrailWright;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearEngine;
import us.someguy.trailwright.wear.WearRules;

import java.util.Optional;

public final class WearInfoPackets {
    public static final ResourceLocation QUERY = new ResourceLocation(TrailWright.MOD_ID, "wear_query");
    public static final ResourceLocation INFO = new ResourceLocation(TrailWright.MOD_ID, "wear_info");

    /** F3's own targeted-block reach in 1.20.1 (DebugScreenOverlay picks at 20 blocks). */
    public static final double REACH = 20.0;
    // Distance is measured to the block's center; allow its half-diagonal on top of REACH.
    private static final double MAX_DIST_SQR = (REACH + 1.0) * (REACH + 1.0);

    private WearInfoPackets() {}

    /** Reply payload. {@code hasRule} false means "no rule" (or unwearable); wear and threshold are then 0. */
    public record Info(BlockPos pos, boolean hasRule, float wear, float threshold) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBoolean(hasRule);
            buf.writeFloat(wear);
            buf.writeFloat(threshold);
        }

        public static Info read(FriendlyByteBuf buf) {
            return new Info(buf.readBlockPos(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
        }
    }

    /**
     * The server's answer to a query, or empty when the asker gets no reply: not an op, block
     * out of reach, or chunk not loaded. Never loads a chunk.
     */
    public static Optional<Info> answer(ServerLevel level, Vec3 eye, boolean isOp, BlockPos pos) {
        if (!isOp) return Optional.empty();
        if (eye.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DIST_SQR) return Optional.empty();
        if (!level.isLoaded(pos)) return Optional.empty();

        BlockState state = level.getBlockState(pos);
        WearRules.Active active = WearRules.forState(state);
        // Same gates as WearEngine.applyFootfall, so "no rule" means "this block never wears".
        if (active == null || state.hasBlockEntity() || state.is(WearEngine.UNWEARABLE) || state.is(Blocks.FARMLAND)) {
            return Optional.of(new Info(pos.immutable(), false, 0f, 0f));
        }
        // Wear on a two-block plant (tall grass) is stored at its lower half, where footfalls
        // put it. The reply keeps the queried position so the client matches it to its target.
        BlockPos stored = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? pos.below() : pos;
        long packed = stored.asLong();
        float wear = WearData.get(level).peek(packed, level.getGameTime(), TrailWrightConfig.get().decayPerDay);
        float threshold = WearEngine.threshold(active.rule(), packed, level.getSeed());
        return Optional.of(new Info(pos.immutable(), true, wear, threshold));
    }
}
