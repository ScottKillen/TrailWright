/*
 * Purpose:      Hooks the end of ServerPlayer#tick, asks FootfallDetector whether a footfall
 *               happened, and forwards it to WearEngine. Implements FootfallDuck.
 * Dependencies: Mixin; Mojang mappings for 1.20.1 (ServerPlayer#tick, Entity#onGround,
 *               #getOnPos, #isPassenger, #getVehicle, LivingEntity#isFallFlying);
 *               TrailWrightConfig (countMounts).
 */
package us.someguy.trailwright.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.someguy.trailwright.TrailWright;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.footfall.FootfallDetector;
import us.someguy.trailwright.footfall.FootfallDuck;
import us.someguy.trailwright.wear.WearEngine;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements FootfallDuck {
    @Unique private long trailwright$lastOnPos = FootfallDetector.NO_POS;
    @Unique private boolean trailwright$wasOnGround = true;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trailwright$onTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;

        // With countMounts, a ridden living mount stands in for the player: its ground state
        // and supporting block are what touch the ground. Boats and minecarts never count.
        Entity feet = self;
        boolean mounted = self.isPassenger();
        if (mounted && TrailWrightConfig.get().countMounts && self.getVehicle() instanceof LivingEntity mount) {
            feet = mount;
            mounted = false;
        }

        if (!feet.onGround()) {
            trailwright$wasOnGround = false;
            return;
        }

        // getOnPos allocates a BlockPos; call it once, and only on grounded ticks.
        BlockPos onPos = feet.getOnPos();
        long packed = onPos.asLong();

        // isShiftKeyDown is Mojang's name for the player's Sneak input: whatever key they bound
        // to Sneak (or toggle sneak), sent by the client. Not isCrouching: a low ceiling forces
        // the crouch pose without the player choosing to sneak, and that walk should still count.
        if (FootfallDetector.isFootfall(true, self.isFallFlying(), mounted, self.isSpectator(),
                self.isShiftKeyDown(), packed, trailwright$lastOnPos, trailwright$wasOnGround)) {
            if (TrailWright.DEBUG_FOOTFALLS) TrailWright.LOG.info("footfall {} at {}", self.getScoreboardName(), onPos);
            WearEngine.onPlayerFootfall(self.serverLevel(), onPos, self);
        }

        // Memory tracks the last grounded position every tick, so the first grounded tick
        // after login records a position without counting as a footfall.
        trailwright$lastOnPos = packed;
        trailwright$wasOnGround = true;
    }

    @Override public long trailwright$lastOnPos() { return trailwright$lastOnPos; }
    @Override public void trailwright$setLastOnPos(long p) { trailwright$lastOnPos = p; }
    @Override public boolean trailwright$wasOnGround() { return trailwright$wasOnGround; }
    @Override public void trailwright$setWasOnGround(boolean g) { trailwright$wasOnGround = g; }
}
