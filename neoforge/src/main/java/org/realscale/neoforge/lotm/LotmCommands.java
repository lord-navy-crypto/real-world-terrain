package org.realscale.neoforge.lotm;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Read-only player controls work on multiplayer servers without cheats. */
public final class LotmCommands {
    private LotmCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lotm")
            .then(Commands.literal("status").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                LotmPath path = LotmPath.of(player);
                if (path == null) {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.status", path.id(),
                        player.getPersistentData().getInt(LotmPath.NBT_SEQUENCE),
                        LotmState.spirit(player), LotmBalance.MAX_SPIRIT,
                        LotmState.practice(player)), false);
                return 1;
            }))
            .then(Commands.literal("guide").executes(context -> {
                context.getSource().sendSuccess(
                        () -> Component.translatable("message.realscale.lotm.guide"), false);
                return 1;
            })));
    }
}
