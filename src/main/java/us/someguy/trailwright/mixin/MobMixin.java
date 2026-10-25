/*
 * Purpose:      Hooks the end of Mob#tick, detects mob footfalls with FootfallDetector, and
 *               forwards them to WearEngine.onMobFootfall (DESIGN.md §3.2). Implements
 *               FootfallDuck.
 * Dependencies: Mixin; Mojang mappings for 1.20.1 (Mob#tick, Entity#onGround, #getOnPos,
 *               #blockPosition, #isPassenger); TrailWrightConfig (countMobs), WearRules.
 */
package us.someguy.trailwright.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.footfall.FootfallDetector;
import us.someguy.trailwright.footfall.FootfallDuck;
import us.someguy.trailwright.wear.WearEngine;
import us.someguy.trailwright.wear.WearRules;

@Mixin(Mob.class)
public abstract class MobMixin implements FootfallDuck {
    @Unique private long trailwright$lastOnPos = FootfallDetector.NO_POS;
    @Unique private boolean trailwright$wasOnGround = true;
    // Feet block at the last grounded tick. blockPosition() is a cached field, so comparing it
    // costs nothing, while getOnPos() allocates; see the gate below.
    @Unique private long trailwright$lastFeet = FootfallDetector.NO_POS;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trailwright$onTick(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        // Runs for every loaded mob every tick: cheapest rejections first.
        if (!(self.level() instanceof ServerLevel level) || !TrailWrightConfig.get().countMobs) return;
        // No rules, no wear possible: skip the per-tick work entirely.
        if (WearRules.isEmpty()) return;
        if (!self.onGround() || self.isPassenger()) {
            trailwright$wasOnGround = false;
            return;
        }

        // Still on the ground in the same feet block as last tick: the supporting block has
        // not changed either, so skip getOnPos() and its allocation. Idle and wandering mobs
        // stay allocation-free; the cost is paid once per block crossed.
        long feet = self.blockPosition().asLong();
        if (trailwright$wasOnGround && feet == trailwright$lastFeet) return;

        BlockPos onPos = self.getOnPos();
        long packed = onPos.asLong();
        if (FootfallDetector.isFootfall(true, false, false, false, false,
                packed, trailwright$lastOnPos, trailwright$wasOnGround)) {
            WearEngine.onMobFootfall(level, onPos, self);
        }
        trailwright$lastOnPos = packed;
        trailwright$lastFeet = feet;
        trailwright$wasOnGround = true;
    }

    @Override public long trailwright$lastOnPos() { return trailwright$lastOnPos; }
    @Override public void trailwright$setLastOnPos(long p) { trailwright$lastOnPos = p; }
    @Override public boolean trailwright$wasOnGround() { return trailwright$wasOnGround; }
    @Override public void trailwright$setWasOnGround(boolean g) { trailwright$wasOnGround = g; }
}
