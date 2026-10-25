/*
 * Purpose:      Client entrypoint for the optional F3 wear readout (DESIGN.md §10): receives
 *               wear replies, throttles queries for the F3 targeted block, and formats the
 *               line DebugScreenOverlayMixin appends. The line's text comes from the lang
 *               keys trailwright.f3.* (DESIGN.md §11).
 * Dependencies: fabric-networking-api-v1 (client), WearInfoPackets, I18n. Client source set only.
 */
package us.someguy.trailwright.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import us.someguy.trailwright.net.WearInfoPackets;

import java.util.List;
import java.util.Locale;

public final class TrailWrightClient implements ClientModInitializer {
    // Throttle (DESIGN.md §10): a new target may be queried after 5 ticks, the same target is
    // refreshed every 20 ticks so the value follows footfalls.
    private static final long MIN_TICKS_BETWEEN = 5;
    private static final long REFRESH_TICKS = 20;

    // Render-thread state only: F3 runs on the render thread and replies are moved there
    // with client.execute.
    private static WearInfoPackets.Info latest;
    private static BlockPos lastQueried;
    private static long lastQueryTick = Long.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WearInfoPackets.INFO, (client, handler, buf, sender) -> {
            WearInfoPackets.Info info = WearInfoPackets.Info.read(buf);
            client.execute(() -> latest = info);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TrailWrightClient::clear));
    }

    private static void clear() {
        latest = null;
        lastQueried = null;
        lastQueryTick = Long.MIN_VALUE;
    }

    /** Called from DebugScreenOverlayMixin with F3's right-column lines and its targeted block. */
    public static void appendF3(List<String> lines, HitResult target) {
        Minecraft mc = Minecraft.getInstance();
        // Same rule vanilla applies to its own targeted-block lines.
        if (mc.showOnlyReducedInfo() || mc.level == null) return;
        if (!(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();

        maybeQuery(pos, mc.level.getGameTime());

        WearInfoPackets.Info info = latest;
        if (info == null || !info.pos().equals(pos)) return;   // never label the wrong block
        lines.add("");
        // F3 lines are plain strings, so the text is resolved here. Numbers use Locale.ROOT,
        // like vanilla's F3 lines, so every language shows a decimal point.
        lines.add(info.hasRule()
                ? I18n.get("trailwright.f3.wear",
                        String.format(Locale.ROOT, "%.2f", info.wear()),
                        String.format(Locale.ROOT, "%.2f", info.threshold()))
                : I18n.get("trailwright.f3.no_rule"));
    }

    private static void maybeQuery(BlockPos pos, long now) {
        // Server without TrailWright (or older): it never registered the channel, so stay silent.
        if (!ClientPlayNetworking.canSend(WearInfoPackets.QUERY)) return;
        long since = now - lastQueryTick;
        boolean changed = !pos.equals(lastQueried);
        if (lastQueryTick != Long.MIN_VALUE && (since < MIN_TICKS_BETWEEN || (!changed && since < REFRESH_TICKS))) return;
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(WearInfoPackets.QUERY, buf);
        lastQueried = pos.immutable();
        lastQueryTick = now;
    }
}
