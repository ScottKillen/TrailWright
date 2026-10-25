/*
 * Purpose:      Caches the level's WearData (WearDataHolder) and forwards every block change
 *               to WearResets (DESIGN.md §4.6).
 * Dependencies: Mixin; Mojang mappings for 1.20.1 (ServerLevel#onBlockStateChange, which
 *               Level#setBlock calls after the chunk is updated).
 */
package us.someguy.trailwright.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearDataHolder;
import us.someguy.trailwright.wear.WearResets;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements WearDataHolder {
    @Unique private WearData trailwright$wearData;

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void trailwright$onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        WearResets.onBlockStateChange((ServerLevel) (Object) this, pos, oldState, newState);
    }

    @Override public WearData trailwright$wearData() { return trailwright$wearData; }
    @Override public void trailwright$setWearData(WearData data) { trailwright$wearData = data; }
}
