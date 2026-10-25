/*
 * Purpose:      Pure decision logic: is this tick a footfall? No Minecraft types, so it can
 *               be unit-tested without bootstrapping the game. See DESIGN.md §3.
 * Dependencies: None.
 */
package us.someguy.trailwright.footfall;

public final class FootfallDetector {
    /** Sentinel for "no previous position recorded". */
    public static final long NO_POS = Long.MIN_VALUE;

    private FootfallDetector() {}

    /**
     * @param onGround      player is on the ground this tick
     * @param fallFlying    elytra gliding
     * @param mounted       riding a vehicle
     * @param spectator     spectator mode
     * @param sneaking      sneak key held; a sneaking step never wears the ground
     * @param onPos         packed BlockPos of the supporting block this tick
     * @param lastOnPos     packed BlockPos at the last grounded tick, or {@link #NO_POS}
     * @param wasOnGround   onGround value from the previous tick
     */
    public static boolean isFootfall(boolean onGround, boolean fallFlying, boolean mounted,
                                     boolean spectator, boolean sneaking, long onPos,
                                     long lastOnPos, boolean wasOnGround) {
        if (!onGround || fallFlying || mounted || spectator || sneaking) return false;
        // No memory yet (fresh ServerPlayer after login or respawn): the caller records this
        // position, and the player has not stepped anywhere, so it does not count.
        if (lastOnPos == NO_POS) return false;
        if (!wasOnGround) return true;        // landing counts, even on the same block
        return onPos != lastOnPos;            // walked onto a new block
    }
}
