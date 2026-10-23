package org.realscale.compat.dhchunky;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Standalone NeoForge 1.21.1 DH/Chunky lifecycle guard.
 * Commands only expose launch-lifetime counters, without touching
 * Minecraft worldgen, DH rendering, or Chunky's generator scheduler.
 */
@Mod("dhchunkyguard")
public final class DhChunkyGuardMod {
    public DhChunkyGuardMod() {
        NeoForge.EVENT_BUS.addListener(DhChunkyGuardMod::registerCommands);
        System.err.println("REALSCALE_DH_CHUNKY_GUARD_MOD_LOADED (experimental v0.3.1)");
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dhchunkyguard")
            .then(Commands.literal("status").executes(context -> {
                context.getSource().sendSuccess(
                    () -> Component.literal(ChunkyBridgeMetrics.status()), false);
                return 1;
            }))
            .then(Commands.literal("advice").executes(context -> {
                context.getSource().sendSuccess(
                    () -> Component.literal(ChunkyBridgeMetrics.advice()), false);
                return 1;
            })));
    }
}
