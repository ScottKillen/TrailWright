/*
 * Purpose:      Pure wear arithmetic: points per footfall and the jittered per-position
 *               threshold (DESIGN.md §4.1, §4.2). No Minecraft types beyond the config, so it
 *               is unit-tested without bootstrapping the game.
 * Dependencies: TrailWrightConfig.
 */
package us.someguy.trailwright.wear;

import us.someguy.trailwright.config.TrailWrightConfig;

public final class WearMath {
    private WearMath() {}

    /** Player formula; a sneaking step never reaches here (DESIGN.md §3.1). */
    public static float playerPoints(TrailWrightConfig cfg, boolean sprinting) {
        return sprinting ? cfg.wearMultiplier * cfg.sprintMultiplier : cfg.wearMultiplier;
    }

    /** Mob formula (DESIGN.md §4.1). */
    public static float mobPoints(TrailWrightConfig cfg) {
        return cfg.wearMultiplier * cfg.mobMultiplier;
    }

    /** {@code base_threshold} scaled by the stable per-position jitter. */
    public static float threshold(float baseThreshold, float jitter, long packedPos, long seed) {
        return baseThreshold * jitterFactor(packedPos, seed, jitter);
    }

    /** Stable per-position factor in [1 - jitter, 1 + jitter]. */
    static float jitterFactor(long packedPos, long seed, float jitter) {
        long h = packedPos * 0x9E3779B97F4A7C15L ^ seed;
        h ^= (h >>> 33); h *= 0xFF51AFD7ED558CCDL; h ^= (h >>> 33);
        double unit = (h >>> 11) * 0x1.0p-53;          // [0, 1)
        return (float) (1.0 + jitter * (unit * 2.0 - 1.0));
    }
}
