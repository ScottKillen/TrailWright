/*
 * Purpose:      Unit tests for FootfallDetector (TESTING.md T2.x logic, no game bootstrap).
 * Dependencies: JUnit 5.
 */
package us.someguy.trailwright.footfall;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FootfallDetectorTest {
    private static final long A = 1L, B = 2L;
    private static final long NONE = FootfallDetector.NO_POS;

    /** Defaults: on the ground, not gliding, not mounted, not spectating, not sneaking. */
    private static boolean step(long onPos, long lastOnPos, boolean wasOnGround) {
        return FootfallDetector.isFootfall(true, false, false, false, false, onPos, lastOnPos, wasOnGround);
    }

    @Test void newBlockOnGroundCounts()   { assertTrue (step(B, A, true)); }
    @Test void sameBlockDoesNotCount()    { assertFalse(step(A, A, true)); }
    @Test void landingSameBlockCounts()   { assertTrue (step(A, A, false)); }
    @Test void airborneDoesNotCount()     { assertFalse(FootfallDetector.isFootfall(false, false, false, false, false, B, A, true)); }
    @Test void glidingDoesNotCount()      { assertFalse(FootfallDetector.isFootfall(true,  true,  false, false, false, B, A, true)); }
    @Test void mountedDoesNotCount()      { assertFalse(FootfallDetector.isFootfall(true,  false, true,  false, false, B, A, true)); }
    @Test void spectatorDoesNotCount()    { assertFalse(FootfallDetector.isFootfall(true,  false, false, true,  false, B, A, true)); }
    @Test void sneakingDoesNotCount()     { assertFalse(FootfallDetector.isFootfall(true,  false, false, false, true,  B, A, true)); }
    @Test void sneakingLandingDoesNotCount() { assertFalse(FootfallDetector.isFootfall(true, false, false, false, true, A, A, false)); }

    @Test void firstGroundedTickDoesNotCount()      { assertFalse(step(A, NONE, true)); }
    @Test void firstLandingAfterLoginDoesNotCount() { assertFalse(step(A, NONE, false)); }
}
