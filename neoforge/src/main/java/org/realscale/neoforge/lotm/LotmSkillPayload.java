package org.realscale.neoforge.lotm;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Small serverbound request; all ability checks and effects happen on the server. */
public record LotmSkillPayload(int slot) implements CustomPacketPayload {
    public static final Type<LotmSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("realscale", "lotm_skill"));
    public static final StreamCodec<ByteBuf, LotmSkillPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, LotmSkillPayload::slot, LotmSkillPayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, LotmSkillPayload::handle);
    }

    private static void handle(LotmSkillPayload payload, IPayloadContext context) {
        if (payload.slot < 1 || payload.slot > 2) return;
        if (context.player() instanceof ServerPlayer player) {
            LotmSkills.activate(player, payload.slot);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
