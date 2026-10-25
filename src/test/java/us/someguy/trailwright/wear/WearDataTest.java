/*
 * Purpose:      Unit tests for WearData decay, add, prune, and NBT round-trip, and for
 *               WearMath's point scaling and jitter (TESTING.md T3.x logic, no world needed).
 * Dependencies: JUnit 5; Minecraft NBT classes on the test classpath.
 */
package us.someguy.trailwright.wear;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import us.someguy.trailwright.config.TrailWrightConfig;

import static org.junit.jupiter.api.Assertions.*;

class WearDataTest {
    private static final long DAY = WearData.TICKS_PER_DAY;
    private static final float EPS = 1e-4f;

    @Test void decayIsLinearPerDay() {
        assertEquals(8f, WearData.decayed(10f, DAY, 2f), EPS);
        assertEquals(9f, WearData.decayed(10f, DAY / 2, 2f), EPS);
    }

    @Test void decayFloorsAtZero() {
        assertEquals(0f, WearData.decayed(1f, 10 * DAY, 2f), EPS);
    }

    @Test void decayIgnoresNonPositiveElapsed() {
        assertEquals(5f, WearData.decayed(5f, 0, 2f), EPS);
        assertEquals(5f, WearData.decayed(5f, -100, 2f), EPS);
    }

    @Test void addWearAccumulatesWithDecay() {
        WearData d = new WearData();
        assertEquals(1f, d.addWear(42L, 1f, 0, 2f), EPS);
        assertEquals(2f, d.addWear(42L, 1f, 0, 2f), EPS);
        // Half a day later: 2 - 1 decay + 1 new.
        assertEquals(2f, d.addWear(42L, 1f, DAY / 2, 2f), EPS);
        assertTrue(d.isDirty());
    }

    @Test void peekDoesNotModify() {
        WearData d = new WearData();
        d.addWear(7L, 4f, 0, 2f);
        assertEquals(2f, d.peek(7L, DAY, 2f), EPS);
        assertEquals(4f, d.peek(7L, 0, 2f), EPS);
        assertEquals(0f, d.peek(8L, 0, 2f), EPS);
    }

    @Test void resetRemovesEntry() {
        WearData d = new WearData();
        d.addWear(7L, 4f, 0, 2f);
        d.reset(7L);
        assertFalse(d.has(7L));
    }

    @Test void pruneDropsDecayedAndKeepsOthers() {
        WearData d = new WearData();
        d.addWear(1L, 1f, 0, 2f);    // gone after one day
        d.addWear(2L, 5f, 0, 2f);    // 3 left after one day
        assertEquals(1, d.prune(DAY, 2f));
        assertFalse(d.has(1L));
        assertEquals(3f, d.peek(2L, DAY, 2f), EPS);
        assertEquals(1, d.size());
    }

    @Test void nbtRoundTrip() {
        WearData d = new WearData();
        d.addWear(Long.MAX_VALUE, 3.25f, 100, 2f);
        d.addWear(-5L, 0.5f, 200, 2f);
        WearData back = WearData.load(d.save(new CompoundTag()));
        assertEquals(2, back.size());
        assertEquals(3.25f, back.peek(Long.MAX_VALUE, 100, 2f), EPS);
        assertEquals(0.5f, back.peek(-5L, 200, 2f), EPS);
    }

    @Test void loadWithMismatchedArraysStartsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("pos", new long[] {1, 2});
        tag.putIntArray("wear", new int[] {1});
        tag.putLongArray("tick", new long[] {1, 2});
        assertEquals(0, WearData.load(tag).size());
    }

    @Test void pointsScaleWithMultipliers() {
        TrailWrightConfig cfg = new TrailWrightConfig();
        assertEquals(1.0f, WearMath.playerPoints(cfg, false), EPS);
        assertEquals(1.5f, WearMath.playerPoints(cfg, true), EPS);
        cfg.wearMultiplier = 20f;
        assertEquals(30f, WearMath.playerPoints(cfg, true), EPS);
    }

    @Test void mobPointsUseMobMultiplier() {
        TrailWrightConfig cfg = new TrailWrightConfig();
        assertEquals(0.5f, WearMath.mobPoints(cfg), EPS);
        cfg.wearMultiplier = 20f;
        assertEquals(10f, WearMath.mobPoints(cfg), EPS);
    }

    @Test void jitterStaysInRangeAndIsStable() {
        for (long p = -1000; p < 1000; p += 7) {
            float f = WearMath.jitterFactor(p, 12345L, 0.35f);
            assertTrue(f >= 0.65f && f <= 1.35f, "factor " + f);
            assertEquals(f, WearMath.jitterFactor(p, 12345L, 0.35f));
        }
        assertEquals(1.0f, WearMath.jitterFactor(42L, 1L, 0f), EPS);
    }
}
