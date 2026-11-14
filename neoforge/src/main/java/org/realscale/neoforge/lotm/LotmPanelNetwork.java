package org.realscale.neoforge.lotm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One-way, zero-argument GUI request. No pathways, resources or cooldowns can be
 * modified by the client; all gameplay values are read on the server.
 */
public final class LotmPanelNetwork {
    private LotmPanelNetwork() {}

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
        event.registrar("1").playToServer(
                OpenPanelPayload.TYPE, OpenPanelPayload.STREAM_CODEC,
                LotmPanelNetwork::handleOpenPanel);
    }

    private static void handleOpenPanel(OpenPanelPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive()
                || !LotmConfig.ENABLED.get()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, openingPlayer) ->
                new LotmStatusMenu(id, inventory, LotmStatusMenu.dataFor(player)),
                Component.translatable("gui.realscale.lotm_panel.title")));
    }
}
