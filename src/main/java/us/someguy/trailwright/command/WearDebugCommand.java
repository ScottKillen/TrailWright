/*
 * Purpose:      Dev-only `/trailwright debug wear`: reports the stored wear of the block the
 *               player is looking at (TESTING.md T3.1). Op level 2.
 * Dependencies: Brigadier and Minecraft command classes; WearData, WearRules, WearEngine;
 *               TrailWrightConfig.
 *               Registered from TrailWrightFabric in development environments only.
 */
package us.someguy.trailwright.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearEngine;
import us.someguy.trailwright.wear.WearRules;

public final class WearDebugCommand {
    // Creative reach in 1.20.1; long enough to target the block underfoot or nearby.
    private static final double REACH = 5.0;

    private WearDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trailwright")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("wear").executes(ctx -> showWear(ctx.getSource())))));
    }

    private static int showWear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
            // translatableWithFallback: the mod is optional on clients, which may lack the lang file.
            source.sendFailure(Component.translatableWithFallback(
                    "commands.trailwright.wear.no_target", "Not looking at a block"));
            return 0;
        }
        BlockPos pos = bhr.getBlockPos();
        String where = pos.toShortString();
        WearData data = WearData.get(player.serverLevel());
        if (!data.has(pos.asLong())) {
            source.sendSuccess(() -> Component.translatableWithFallback(
                    "commands.trailwright.wear.none", "No stored wear at %s", where), false);
            return 0;
        }
        float wear = data.peek(pos.asLong(), player.serverLevel().getGameTime(),
                TrailWrightConfig.get().decayPerDay);
        String value = String.format("%.2f", wear);
        WearRules.Active active = WearRules.forState(player.serverLevel().getBlockState(pos));
        // An entry can outlive its rule until the next prune; show "-" for the threshold then.
        String threshold = active == null ? "-" : String.format("%.2f",
                WearEngine.threshold(active.rule(), pos.asLong(), player.serverLevel().getSeed()));
        source.sendSuccess(() -> Component.translatableWithFallback(
                "commands.trailwright.wear.value", "Wear at %s: %s / %s", where, value, threshold), false);
        return 1;
    }
}
