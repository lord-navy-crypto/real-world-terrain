package org.realscale.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.realscale.neoforge.client.clipmap.ClipmapHeightGrid;
import org.realscale.neoforge.client.clipmap.ClipmapHeightQuantizer;
import org.realscale.neoforge.client.clipmap.GeometryClipmapLayout;
import org.realscale.neoforge.client.clipmap.NaturalAutomateHeightMapping;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Generates sparse distant surface samples directly from Natural Automate's
 * seeded density graph. It never requests, loads, or constructs a far chunk.
 *
 * <p>Sampling runs on one bounded daemon worker. The active router's depth
 * function is immutable for the lifetime of the world; its RealScale horizontal
 * memo and morphology caches are worker-thread-local.
 */
@EventBusSubscriber(modid = "realscale")
public final class GeometryClipmapServer {
    private static final int MIN_HEIGHT = -64;
    private static final int MAX_HEIGHT = 2032;
    private static final int SAMPLE_Y = 63;
    private static final int MAX_LEVELS = 6;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int cleanupTicks;

    private static final ThreadPoolExecutor SAMPLER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "RealScale-Clipmap-Sampler");
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy());

    private GeometryClipmapServer() {}

    public static void request(ServerPlayer player, int nearRenderDistanceChunks) {
        if (nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64) return;
        UUID id = player.getUUID();
        Session current = SESSIONS.get(id);
        if (current == null || current.nearChunks != nearRenderDistanceChunks) {
            SESSIONS.put(id, new Session(nearRenderDistanceChunks));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) updatePlayer(server, player, session);
        }
        if (++cleanupTicks >= 200) {
            cleanupTicks = 0;
            SESSIONS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    private static void updatePlayer(MinecraftServer server,
            ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            SESSIONS.remove(player.getUUID(), session);
            return;
        }

        ResourceLocation settingsId = generator.generatorSettings().unwrapKey()
                .map(ResourceKey::location).orElse(null);
        if (!isNaturalAutomateSettings(settingsId)) {
            SESSIONS.remove(player.getUUID(), session);
            return;
        }

        String dimensionKey = level.dimension().location().toString();
        long seed = level.getSeed();
        if (!Objects.equals(session.dimensionKey, dimensionKey)
                || session.seed != seed
                || !Objects.equals(session.settingsId, settingsId)) {
            session.resetWorld(dimensionKey, seed, settingsId);
        }
        if (session.inFlight) return;

        GeometryClipmapLayout planner =
                GeometryClipmapLayout.forRenderDistanceChunks(session.nearChunks);
        GeometryClipmapLayout.Layout nextLayout = planner.compute(
                player.getX(), player.getZ(), session.layout);
        if (session.layout != null
                && sameCentre(session.layout, nextLayout)) {
            return;
        }

        RandomState randomState = level.getChunkSource().randomState();
        DensityFunction terrainDensity = randomState.router().depth();
        List<ClipmapHeightGrid> previousGrids = session.grids;
        String targetDimension = dimensionKey;
        long targetSeed = seed;
        session.inFlight = true;

        try {
            SAMPLER.execute(() -> {
                try {
                    ClipmapHeightGrid.SurfaceHeightSampler heightSampler = (x, z) -> {
                        // Natural Automate terrain_density is height_signal plus
                        // a Y-clamped gradient whose value is exactly zero at Y=63.
                        // Sampling this one point therefore recovers the original
                        // continuous height signal without a chunk or vertical scan.
                        double heightSignal = terrainDensity.compute(
                                new DensityFunction.SinglePointContext(x, SAMPLE_Y, z));
                        double surface = NaturalAutomateHeightMapping.surfaceY(heightSignal);
                        if (!Double.isFinite(surface)) {
                            throw new IllegalStateException(
                                    "Natural Automate returned a non-finite surface height");
                        }
                        return (float) Math.max(MIN_HEIGHT,
                                Math.min(MAX_HEIGHT, surface));
                    };

                    List<ClipmapHeightGrid> nextGrids = new ArrayList<>(MAX_LEVELS);
                    List<ClipmapHeightTilePayload> payloads = new ArrayList<>(MAX_LEVELS);
                    for (int i = 0; i < nextLayout.levels().size(); i++) {
                        ClipmapHeightGrid previous = previousGrids == null
                                ? null : previousGrids.get(i);
                        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(
                                nextLayout.levels().get(i), nextLayout.gridCells(),
                                heightSampler, previous);
                        nextGrids.add(grid);
                        payloads.add(toPayload(targetDimension, session.nearChunks,
                                nextLayout, i, grid));
                    }

                    server.execute(() -> {
                        if (SESSIONS.get(player.getUUID()) != session
                                || server.getPlayerList().getPlayer(player.getUUID()) != player
                                || !player.serverLevel().dimension().location().toString()
                                        .equals(targetDimension)
                                || player.serverLevel().getSeed() != targetSeed) {
                            return;
                        }
                        session.layout = nextLayout;
                        session.grids = List.copyOf(nextGrids);
                        session.inFlight = false;
                        for (ClipmapHeightTilePayload payload : payloads) {
                            PacketDistributor.sendToPlayer(player, payload);
                        }
                    });
                } catch (RuntimeException failure) {
                    server.execute(() -> {
                        if (SESSIONS.get(player.getUUID()) == session) {
                            session.inFlight = false;
                        }
                        System.err.println("[RealScale Geometry Clipmap] sample failed: "
                                + failure.getClass().getSimpleName() + ": "
                                + failure.getMessage());
                    });
                }
            });
        } catch (RuntimeException rejected) {
            session.inFlight = false;
            System.err.println("[RealScale Geometry Clipmap] sampler queue is full; "
                    + "deferring this player update");
        }
    }

    private static ClipmapHeightTilePayload toPayload(String dimension,
            int nearChunks, GeometryClipmapLayout.Layout layout,
            int levelIndex, ClipmapHeightGrid grid) {
        int side = grid.gridSide();
        float[] heights = grid.copyHeights();
        short[] encoded = new short[heights.length];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int index = z * side + x;
                float height = Float.isFinite(heights[index])
                        ? heights[index] : MIN_HEIGHT;
                encoded[index] = (short) ClipmapHeightQuantizer.encode(
                        height, MIN_HEIGHT, MAX_HEIGHT);
            }
        }
        return new ClipmapHeightTilePayload(dimension, nearChunks,
                layout.gridCells(), levelIndex,
                grid.cellSpacingBlocks(), grid.originX(), grid.originZ(),
                MIN_HEIGHT, MAX_HEIGHT, encoded);
    }

    private static boolean isNaturalAutomateSettings(ResourceLocation id) {
        return id != null && "realscale".equals(id.getNamespace())
                && ("natural_automate".equals(id.getPath())
                    || "natural_automate_v2".equals(id.getPath()));
    }

    private static boolean sameCentre(GeometryClipmapLayout.Layout first,
            GeometryClipmapLayout.Layout second) {
        return first.levels().get(0).centerX() == second.levels().get(0).centerX()
                && first.levels().get(0).centerZ() == second.levels().get(0).centerZ();
    }

    private static final class Session {
        private final int nearChunks;
        private String dimensionKey;
        private String settingsId;
        private long seed;
        private GeometryClipmapLayout.Layout layout;
        private List<ClipmapHeightGrid> grids;
        private boolean inFlight;

        private Session(int nearChunks) {
            this.nearChunks = nearChunks;
        }

        private void resetWorld(String dimension, long worldSeed,
                ResourceLocation settings) {
            dimensionKey = dimension;
            seed = worldSeed;
            settingsId = settings.toString();
            layout = null;
            grids = null;
            inFlight = false;
        }
    }
}
