package org.realscale.neoforge.lotm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client can only request the panel with an empty payload. Progression and
 * discovery data remain server-owned; the client receives a bounded read-only journal snapshot.
 */
public final class LotmPanelNetwork {
    private static volatile List<String> clientResearchEntries = List.of();

    private LotmPanelNetwork() {}

    public static List<String> clientResearchEntries() {
        return clientResearchEntries;
    }

    /** Clear session-scoped client cache when leaving a server or singleplayer world. */
    public static void clearClientResearchLog() {
        clientResearchEntries = List.of();
    }

    /** Open the panel from either the keybind payload or the /lotm panel command. */
    public static void openPanel(ServerPlayer player) {
        if (player == null || !player.isAlive() || !LotmConfig.ENABLED.get()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, openingPlayer) ->
                new LotmStatusMenu(id, inventory, LotmStatusMenu.dataFor(player)),
                Component.translatable("gui.realscale.lotm_panel.title")));
        syncResearchLog(player);
    }

    /** Send only the latest bounded set of clues; discovery ownership stays on the server. */
    public static void syncResearchLog(ServerPlayer player) {
        if (player == null || !(player.containerMenu instanceof LotmStatusMenu)) return;
        List<String> all = LotmActing.journalEntries(player);
        int first = Math.max(0, all.size() - 128);
        List<String> latestFirst = new ArrayList<>(all.subList(first, all.size()));
        Collections.reverse(latestFirst);
        PacketDistributor.sendToPlayer(player, new ResearchLogPayload(latestFirst));
    }

    public record OpenPanelPayload() implements CustomPacketPayload {
        public static final OpenPanelPayload INSTANCE = new OpenPanelPayload();
        public static final Type<OpenPanelPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("realscale", "lotm_open_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPanelPayload> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(OpenPanelPayload.TYPE, OpenPanelPayload.STREAM_CODEC,
                LotmPanelNetwork::handleOpenPanel);
        registrar.playToClient(ResearchLogPayload.TYPE, ResearchLogPayload.STREAM_CODEC,
                LotmPanelNetwork::handleResearchLog);
    }

    private static void handleOpenPanel(OpenPanelPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) openPanel(player);
    }

    private static void handleResearchLog(ResearchLogPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientResearchEntries = payload.entries());
    }

    public record ResearchLogPayload(List<String> entries) implements CustomPacketPayload {
        public static final Type<ResearchLogPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("realscale", "lotm_research_log"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResearchLogPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(128)),
                        ResearchLogPayload::entries,
                        ResearchLogPayload::new);

        public ResearchLogPayload {
            entries = entries == null ? List.of() : entries.stream()
                    .filter(entry -> entry != null && !entry.isBlank())
                    .limit(128)
                    .map(entry -> entry.length() > 256 ? entry.substring(0, 256) : entry)
                    .toList();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
