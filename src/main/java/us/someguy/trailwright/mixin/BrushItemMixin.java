/*
 * Purpose:      Clears wear on the brushed block once per brush stroke (DESIGN.md §4.6).
 *               Repeats vanilla's stroke test (every 10 use-ticks, at 5) at the head of
 *               onUseTick and raycasts only on that tick. A @Redirect on vanilla's own
 *               getBlockState call would be shorter but conflicts with any other mod that
 *               redirects the same call.
 * Dependencies: Mixin; Mojang mappings for 1.20.1 (BrushItem#onUseTick,
 *               #calculateHitResult, #getUseDuration); WearResets.
 */
package us.someguy.trailwright.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.someguy.trailwright.wear.WearResets;

@Mixin(BrushItem.class)
public abstract class BrushItemMixin {
    @Shadow public abstract int getUseDuration(ItemStack stack);
    @Shadow private HitResult calculateHitResult(LivingEntity entity) { throw new AssertionError(); }

    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void trailwright$onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining, CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || remaining < 0 || !(entity instanceof Player)) return;
        // Same stroke tick vanilla uses for the brushing sound and dust (useTicks % 10 == 5).
        int useTicks = getUseDuration(stack) - remaining + 1;
        if (useTicks % 10 != 5) return;
        if (calculateHitResult(entity) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            WearResets.onBrushStroke(server, hit.getBlockPos());
        }
    }
}
