package org.realscale.neoforge.compat.dhchunky;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Read-only lifecycle diagnostics retained from the former standalone addon.
 * Registered on NeoForge's global event bus by RealScaleMod.
 */
public final class DhChunkyGuardCommands {
    private DhChunkyGuardCommands() {}

    public static void registerCommands(RegisterCommandsEvent event) {
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
