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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.realscale.neoforge.client.clipmap.ClipmapHeightGrid;
import org.realscale.neoforge.client.clipmap.ClipmapHeightQuantizer;
import org.realscale.neoforge.client.clipmap.GeometryClipmapLayout;
import org.realscale.neoforge.client.clipmap.NaturalAutomateHeightMapping;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;
import org.realscale.neoforge.network.ClipmapSurfaceProxyPayload;

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
    private static final boolean DEBUG =
            Boolean.getBoolean("realscale.experimental.geometryclipmap.debug");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long serverTickCounter;
    private static final long RETRY_BACKOFF_TICKS = 20L;

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

    public static void request(ServerPlayer player, int nearRenderDistanceChunks,
            long requestId) {
        if (nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64
                || requestId <= 0L) return;
        UUID id = player.getUUID();
        Session current = SESSIONS.get(id);
        if (current != null && requestId <= current.requestId) return;
        if (current == null || current.nearChunks != nearRenderDistanceChunks) {
            if (current != null) current.invalidate();
            SESSIONS.put(id, new Session(nearRenderDistanceChunks, requestId));
            return;
        }
        current.requestEpoch.advance();
        current.requestId = requestId;
        current.layout = null;
        current.inFlight = false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // The worker and session map are static so they can survive an
        // integrated-server world transition. Explicitly invalidate every
        // outstanding epoch before clearing queued work; otherwise a task
        // from the previous world could retain its generator graph or a
        // UUID session could be mistaken for a new server's request.
        for (Session session : SESSIONS.values()) session.invalidate();
        SESSIONS.clear();
        SAMPLER.getQueue().clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        serverTickCounter++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) updatePlayer(server, player, session);
        }
        SESSIONS.entrySet().removeIf(entry -> {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) return false;
            entry.getValue().invalidate();
            return true;
        });
    }

    private static void updatePlayer(MinecraftServer server,
            ServerPlayer player, Session session) {
        ServerLevel level = player.serverLevel();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            removeSession(player.getUUID(), session);
            return;
        }

        ResourceLocation settingsId = generator.generatorSettings().unwrapKey()
                .map(ResourceKey::location).orElse(null);
        if (!isNaturalAutomateSettings(settingsId)) {
            removeSession(player.getUUID(), session);
            return;
        }

        String dimensionKey = level.dimension().location().toString();
        long seed = level.getSeed();
        if (!Objects.equals(session.dimensionKey, dimensionKey)
                || session.seed != seed
                || !Objects.equals(session.settingsId, settingsId.toString())) {
            session.resetWorld(dimensionKey, seed, settingsId);
        }
        if (session.inFlight || !session.requestEpoch.mayStart(serverTickCounter)) return;

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
        String targetSettings = settingsId.toString();
        long targetSeed = seed;
        UUID playerId = player.getUUID();
        long requestEpoch = session.requestEpoch.current();
        long targetRequestId = session.requestId;
        session.inFlight = true;
        long enqueuedNanos = DEBUG ? System.nanoTime() : 0L;

        try {
            SAMPLER.execute(() -> {
                try {
                    long workerStartedNanos = DEBUG ? System.nanoTime() : 0L;
                    long newlySampledVertices = 0L;
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
                        // The sole sampler thread can be reused immediately
                        // after a world/session change instead of finishing
                        // all six rings for a player who has already left.
                        if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                        ClipmapHeightGrid previous = previousGrids == null
                                ? null : previousGrids.get(i);
                        ClipmapHeightGrid grid = ClipmapHeightGrid.sample(
                                nextLayout.levels().get(i), nextLayout.gridCells(),
                                heightSampler, previous,
                                () -> session.requestEpoch.isCurrent(requestEpoch));
                        nextGrids.add(grid);
                        if (DEBUG) newlySampledVertices += grid.newlySampledVertexCount();
                        payloads.add(toPayload(targetDimension, targetRequestId,
                                session.nearChunks, nextLayout, i, grid));
                    }

                    if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                    List<org.realscale.neoforge.client.clipmap.VisibleSurfaceProxy> proxies =
                            VisibleSurfaceProxyGenerator.collect(generator, randomState,
                                    nextLayout, targetSeed);
                    ClipmapSurfaceProxyPayload proxyPayload =
                            new ClipmapSurfaceProxyPayload(targetDimension, targetRequestId,
                                    nextLayout.levels().get(0).centerX(),
                                    nextLayout.levels().get(0).centerZ(), proxies);
                    long queueWaitNanos = DEBUG
                            ? workerStartedNanos - enqueuedNanos : 0L;
                    long samplingNanos = DEBUG
                            ? System.nanoTime() - workerStartedNanos : 0L;
                    long sampleCountForLog = newlySampledVertices;
                    int proxyCountForLog = proxies.size();
                    long centerXForLog = nextLayout.levels().get(0).centerX();
                    long centerZForLog = nextLayout.levels().get(0).centerZ();
                    server.execute(() -> {
                        // A respawn can replace the ServerPlayer instance while
                        // retaining the UUID and session. Resolve the live player
                        // instead of comparing against the worker's captured object.
                        if (SESSIONS.get(playerId) != session
                                || session.requestId != targetRequestId
                                || !session.requestEpoch.isCurrent(requestEpoch)) {
                            return;
                        }
                        ServerPlayer currentPlayer =
                                server.getPlayerList().getPlayer(playerId);
                        if (currentPlayer == null) {
                            session.inFlight = false;
                            return;
                        }
                        ServerLevel currentLevel = currentPlayer.serverLevel();
                        if (!currentLevel.dimension().location().toString()
                                    .equals(targetDimension)
                                || currentLevel.getSeed() != targetSeed
                                || !(currentLevel.getChunkSource().getGenerator()
                                        instanceof NoiseBasedChunkGenerator currentGenerator)
                                || !currentGenerator.generatorSettings().unwrapKey()
                                        .map(ResourceKey::location)
                                        .map(ResourceLocation::toString)
                                        .filter(targetSettings::equals)
                                        .isPresent()) {
                            session.inFlight = false;
                            return;
                        }

                        // Do not publish a completed but already-obsolete window
                        // if the player crossed a snap boundary during sampling.
                        GeometryClipmapLayout.Layout currentLayout = planner.compute(
                                currentPlayer.getX(), currentPlayer.getZ(), session.layout);
                        if (!sameCentre(nextLayout, currentLayout)) {
                            session.inFlight = false;
                            return;
                        }

                        session.layout = nextLayout;
                        session.grids = List.copyOf(nextGrids);
                        session.inFlight = false;
                        for (ClipmapHeightTilePayload payload : payloads) {
                            PacketDistributor.sendToPlayer(currentPlayer, payload);
                        }
                        PacketDistributor.sendToPlayer(currentPlayer, proxyPayload);
                        if (DEBUG) {
                            System.out.printf(java.util.Locale.ROOT,
                                    "[RealScale Geometry Clipmap] sample accepted dim=%s center=(%d,%d) "
                                            + "newVertices=%d proxies=%d queueWait=%.1fms sample=%.1fms "
                                            + "queueDepth=%d%n",
                                    targetDimension, centerXForLog, centerZForLog,
                                    sampleCountForLog, proxyCountForLog,
                                    queueWaitNanos / 1_000_000.0,
                                    samplingNanos / 1_000_000.0,
                                    SAMPLER.getQueue().size());
                        }
                    });
                } catch (RuntimeException failure) {
                    if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                    server.execute(() -> {
                        if (SESSIONS.get(playerId) == session
                                && session.requestEpoch.isCurrent(requestEpoch)) {
                            session.inFlight = false;
                            session.requestEpoch.defer(serverTickCounter,
                                    RETRY_BACKOFF_TICKS);
                        }
                        System.err.println("[RealScale Geometry Clipmap] sample failed: "
                                + failure.getClass().getSimpleName() + ": "
                                + failure.getMessage());
                    });
                }
            });
        } catch (RuntimeException rejected) {
            session.inFlight = false;
            session.requestEpoch.defer(serverTickCounter, RETRY_BACKOFF_TICKS);
            System.err.println("[RealScale Geometry Clipmap] sampler queue is full; "
                    + "deferring this player update for " + RETRY_BACKOFF_TICKS
                    + " ticks");
        }
    }

    private static ClipmapHeightTilePayload toPayload(String dimension,
            long requestId, int nearChunks, GeometryClipmapLayout.Layout layout,
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
        return new ClipmapHeightTilePayload(dimension, requestId, nearChunks,
                layout.gridCells(), levelIndex,
                grid.cellSpacingBlocks(), grid.originX(), grid.originZ(),
                MIN_HEIGHT, MAX_HEIGHT, encoded);
    }

    private static void removeSession(UUID playerId, Session session) {
        session.invalidate();
        SESSIONS.remove(playerId, session);
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
        private long requestId;
        private String dimensionKey;
        private String settingsId;
        private long seed;
        private GeometryClipmapLayout.Layout layout;
        private List<ClipmapHeightGrid> grids;
        private boolean inFlight;
        private final ClipmapRequestEpoch requestEpoch = new ClipmapRequestEpoch();

        private Session(int nearChunks, long requestId) {
            this.nearChunks = nearChunks;
            this.requestId = requestId;
        }

        private void invalidate() {
            requestEpoch.advance();
            layout = null;
            grids = null;
            inFlight = false;
        }

        private void resetWorld(String dimension, long worldSeed,
                ResourceLocation settings) {
            dimensionKey = dimension;
            seed = worldSeed;
            settingsId = settings.toString();
            requestEpoch.advance();
            layout = null;
            grids = null;
            inFlight = false;
        }
    }
}
