/*
 * Purpose:      Duck interface mixed into ServerPlayer and Mob to hold per-entity footfall
 *               memory without maps or allocation. See DESIGN.md §3.
 * Dependencies: Implemented by mixin.ServerPlayerMixin and mixin.MobMixin.
 */
package us.someguy.trailwright.footfall;

public interface FootfallDuck {
    long trailwright$lastOnPos();
    void trailwright$setLastOnPos(long packedPos);
    boolean trailwright$wasOnGround();
    void trailwright$setWasOnGround(boolean onGround);
}
