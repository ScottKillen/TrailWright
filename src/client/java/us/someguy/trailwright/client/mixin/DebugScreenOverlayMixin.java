/*
 * Purpose:      Appends the TrailWright wear line to the right column of the F3 screen, below the
 *               targeted-block information (DESIGN.md §10).
 * Dependencies: Mixin; Mojang mappings for 1.20.1 (DebugScreenOverlay#getSystemInformation,
 *               which returns a mutable ArrayList, and the private `block` hit result that
 *               render() picks at 20 blocks); TrailWrightClient.
 */
package us.someguy.trailwright.client.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import us.someguy.trailwright.client.TrailWrightClient;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Shadow private HitResult block;

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void trailwright$appendWear(CallbackInfoReturnable<List<String>> cir) {
        TrailWrightClient.appendF3(cir.getReturnValue(), block);
    }
}
