/*
 * Purpose:      Fabric entry point. Loads config, registers the wear-rule reloader and the
 *               rule rebuild hooks, server tick pruning, the F3 readout query receiver, and
 *               the dev debug command.
 * Dependencies: fabric-lifecycle-events-v1, fabric-resource-loader-v0, fabric-command-api-v2,
 *               fabric-networking-api-v1.
 *               Loader-specific; everything it calls is loader-agnostic.
 */
package us.someguy.trailwright;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.packs.PackType;
import us.someguy.trailwright.command.WearDebugCommand;
import us.someguy.trailwright.config.PlayerGuide;
import us.someguy.trailwright.config.TrailWrightConfig;
import us.someguy.trailwright.net.WearInfoPackets;
import us.someguy.trailwright.wear.WearData;
import us.someguy.trailwright.wear.WearRuleReloader;
import us.someguy.trailwright.wear.WearRules;

import java.nio.file.Path;

public final class TrailWrightFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        // Everything TrailWright reads lives in config/trailwright/: trailwright.json and rules/, plus the
        // player guide RULES.md that it writes there.
        Path trailwrightDir = loader.getConfigDir().resolve(TrailWright.MOD_ID);
        TrailWrightConfig.load(trailwrightDir);
        String version = loader.getModContainer(TrailWright.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        PlayerGuide.install(trailwrightDir, version);

        Path rulesDir = trailwrightDir.resolve("rules");
        WearRuleReloader.ensureConfigFolder(rulesDir);
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new WearRuleReloader(rulesDir));

        // Tags are bound only after reload listeners finish, so tag-based rules resolve here.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WearRules.rebuild());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> WearRules.rebuild());

        ServerTickEvents.END_WORLD_TICK.register(level -> WearData.get(level).maybePrune(level));

        // F3 readout (DESIGN.md §10). The buffer is read on the network thread; the answer is
        // computed on the server thread, where level access is safe.
        ServerPlayNetworking.registerGlobalReceiver(WearInfoPackets.QUERY, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> WearInfoPackets.answer(player.serverLevel(), player.getEyePosition(),
                    player.hasPermissions(2), pos).ifPresent(info -> {
                FriendlyByteBuf out = PacketByteBufs.create();
                info.write(out);
                sender.sendPacket(WearInfoPackets.INFO, out);
            }));
        });

        // Debug command is for development and testing only (TESTING.md T3.1).
        if (loader.isDevelopmentEnvironment()) {
            CommandRegistrationCallback.EVENT.register(
                    (dispatcher, registryAccess, environment) -> WearDebugCommand.register(dispatcher));
        }

        TrailWright.LOG.info("TrailWright initialized");
    }
}
