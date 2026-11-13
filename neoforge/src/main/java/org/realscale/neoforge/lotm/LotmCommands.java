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
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.status",
                        Component.translatable("lotm.realscale.path." + path.id()),
                        LotmState.sequence(player), LotmState.spirit(player),
                        LotmBalance.MAX_SPIRIT, LotmState.digestion(player)), false);
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.instability", LotmState.instability(player)), false);
                return 1;
            }))
            .then(Commands.literal("materials").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                LotmPath path = LotmPath.of(player);
                if (path == null) {
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                int next = LotmState.sequence(player) - 1;
                if (next < 8) {
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.max_rank"), false);
                    return 1;
                }
                context.getSource().sendSuccess(() -> Component.translatable(
                        "lotm.realscale.materials." + path.id() + "." + next), false);
                return 1;
            }))
            .then(Commands.literal("acting").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (LotmPath.of(player) == null) {
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.acting.header",
                        LotmState.digestion(player), LotmBalance.MAX_PRACTICE), false);
                for (String category : new String[] {"exploration", "skill", "research", "fieldwork"}) {
                    int used = LotmActing.dailyUsed(player, category);
                    int cap = LotmActing.dailyCap(category);
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.acting." + category, used, cap), false);
                }
                return 1;
            }))
            .then(Commands.literal("skills").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                LotmPath path = LotmPath.of(player);
                if (path == null) {
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                int sequence = LotmState.sequence(player);
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.skills.header",
                        Component.translatable("lotm.realscale.path." + path.id()), sequence), false);
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.skills.controls"), false);
                for (int rank = 9; rank >= Math.max(8, sequence); rank--) {
                    final int activeRank = rank;
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.skills." + path.id() + "." + activeRank), false);
                }
                if (sequence < 8) {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.skills.archived"), false);
                }
                return 1;
            }))
            .then(Commands.literal("journal").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                if (LotmPath.of(player) == null) {
                    context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.no_path"), false);
                    return 0;
                }
                java.util.List<String> discoveries = LotmActing.researchDiscoveries(player);
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.journal.header", discoveries.size(),
                        LotmActing.dailyUsed(player, "research"), LotmActing.dailyCap("research")), false);
                int first = Math.max(0, discoveries.size() - 8);
                for (int i = first; i < discoveries.size(); i++) {
                    Component name = clueName(discoveries.get(i));
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.journal.entry", name), false);
                }
                if (discoveries.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.journal.empty"), false);
                } else if (first > 0) {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "message.realscale.lotm.journal.more", first), false);
                }
                return 1;
            }))
            .then(Commands.literal("config").executes(context -> {
                context.getSource().sendSuccess(() -> Component.translatable(
                        "message.realscale.lotm.config", LotmConfig.VARIANT_RARITY_PERCENT.get(),
                        LotmConfig.RELIC_RARITY.get(), LotmConfig.POTION_PRACTICE.get(),
                        LotmConfig.CHARACTERISTIC_PRACTICE.get()), false);
                return 1;
            }))
            .then(Commands.literal("guide").executes(context -> {
                context.getSource().sendSuccess(() -> Component.translatable("message.realscale.lotm.guide"), false);
                return 1;
            })));
    }
    /** Translate registered creature and block IDs for the bilingual discovery log. */
    private static Component clueName(String source) {
        int separator = source.indexOf(':');
        if (separator < 0) return Component.literal(source);
        String kind = source.substring(0, separator);
        String resource = source.substring(separator + 1);
        int namespaced = resource.indexOf(':');
        if (namespaced < 0) return Component.literal(source);
        String namespace = resource.substring(0, namespaced);
        String id = resource.substring(namespaced + 1);
        if ("entity".equals(kind) || "block".equals(kind)) {
            return Component.translatable(kind + "." + namespace + "." + id);
        }
        return Component.literal(source);
    }

}
