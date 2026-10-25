/*
 * Purpose:      Applies a footfall: look up the block's rule, add wear (to a trampled-first
 *               plant on top if there is one, else to the block), compare to the jittered
 *               threshold, roll the advance chance, pick the next block by condition, and set
 *               it. See DESIGN.md §4.
 * Dependencies: WearData, WearRules, WearMath, TrailWrightConfig; Minecraft level/block/entity types.
 */
package us.someguy.trailwright.wear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import us.someguy.trailwright.TrailWright;
import us.someguy.trailwright.config.TrailWrightConfig;

public final class WearEngine {
    public static final TagKey<Block> UNWEARABLE =
            TagKey.create(Registries.BLOCK, new ResourceLocation(TrailWright.MOD_ID, "unwearable"));

    public static final TagKey<Block> TRAMPLED_FIRST =
            TagKey.create(Registries.BLOCK, new ResourceLocation(TrailWright.MOD_ID, "trampled_first"));

    public static final TagKey<EntityType<?>> LIGHT_FOOTED =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(TrailWright.MOD_ID, "light_footed"));

    // Reused for the block-above read on every footfall. Footfalls run only on the server
    // thread, so one instance is safe and keeps the common path allocation-free.
    private static final BlockPos.MutableBlockPos ABOVE = new BlockPos.MutableBlockPos();

    private WearEngine() {}

    /** Player (or counted mount) footfall from ServerPlayerMixin. */
    public static void onPlayerFootfall(ServerLevel level, BlockPos pos, ServerPlayer player) {
        TrailWrightConfig cfg = TrailWrightConfig.get();
        if (!cfg.enabled) return;
        applyFootfall(level, pos, WearMath.playerPoints(cfg, player.isSprinting()), false);
    }

    /**
     * Mob footfall from MobMixin (DESIGN.md §3.2). The filters run here, after a footfall,
     * so the per-tick mixin stays cheap and GameTests can drive them directly.
     */
    public static void onMobFootfall(ServerLevel level, BlockPos pos, Mob mob) {
        TrailWrightConfig cfg = TrailWrightConfig.get();
        if (!cfg.enabled || !cfg.countMobs) return;
        if (mob.getType().is(LIGHT_FOOTED)) return;
        // A player's mount is counted by the player hook (countMounts) or not at all.
        if (mob.hasPassenger(WearEngine::isPlayer)) return;
        if (cfg.respectMobGriefing && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        applyFootfall(level, pos, WearMath.mobPoints(cfg), true);
    }

    private static boolean isPlayer(Entity e) {
        return e instanceof Player;
    }

    /**
     * Hot path. Records wear only for blocks with a rule (DESIGN.md §4.1). No allocation once
     * the block (or the plant on it) has a wear entry, except on the rare advance path.
     *
     * @param fromMob true for mob footfalls; those count only where the rule sets
     *                {@code mobs_can_advance}
     */
    public static void applyFootfall(ServerLevel level, BlockPos pos, float points, boolean fromMob) {
        BlockState state = level.getBlockState(pos);
        WearRules.Active active = WearRules.forState(state);
        if (active == null) return;
        WearRule rule = active.rule();
        if (fromMob && !rule.mobsCanAdvance()) return;
        if (state.hasBlockEntity() || state.is(UNWEARABLE) || state.is(Blocks.FARMLAND)) return;

        // Stage 0: a plant on top (#trailwright:trampled_first) shields the ground and wears
        // by its own rule first. A tagged plant with no usable rule is ignored, so the ground
        // under it wears as if it were bare.
        BlockState above = level.getBlockState(ABOVE.setWithOffset(pos, Direction.UP));
        if (above.is(TRAMPLED_FIRST)) {
            WearRules.Active plant = WearRules.forState(above);
            if (plant != null) {
                if (!fromMob || plant.rule().mobsCanAdvance()) addAndRoll(level, ABOVE, plant, points, true);
                return;
            }
        }

        addAndRoll(level, pos, active, points, false);
    }

    /**
     * Adds wear at {@code pos}, and once past the jittered threshold rolls the advance chance
     * and applies the first matching branch. {@code pos} may be the shared mutable
     * {@link #ABOVE}; it is copied only on the rare advance.
     *
     * @param isPlant a branch to air breaks the plant with drops, the way vanilla trampling
     *                does; setBlock to air would drop nothing
     */
    private static void addAndRoll(ServerLevel level, BlockPos pos, WearRules.Active active, float points,
                                   boolean isPlant) {
        WearRule rule = active.rule();
        TrailWrightConfig cfg = TrailWrightConfig.get();
        WearData data = WearData.get(level);
        long packed = pos.asLong();
        float wear = data.addWear(packed, points, level.getGameTime(), cfg.decayPerDay);
        if (wear < threshold(rule, packed, level.getSeed())) return;
        if (level.getRandom().nextFloat() >= rule.advanceChance()) return;

        for (WearRules.ActiveBranch b : active.branches()) {
            if (!b.when().test(level, pos)) continue;
            BlockPos at = pos.immutable();
            if (isPlant && b.to().isAir()) {
                level.destroyBlock(at, true);
            } else {
                // UPDATE_ALL sends neighbor shape updates, so anything on top that cannot
                // survive on the new block (a flower on a path, or the upper half of tall grass
                // knocked down to short grass) breaks the vanilla way.
                level.setBlock(at, b.to(), Block.UPDATE_ALL);
            }
            data.reset(packed);
            return;
        }
    }

    /** {@code base_threshold} scaled by the stable per-position jitter (DESIGN.md §4.2). */
    public static float threshold(WearRule rule, long packedPos, long seed) {
        return WearMath.threshold(rule.baseThreshold(), rule.jitter(), packedPos, seed);
    }
}
