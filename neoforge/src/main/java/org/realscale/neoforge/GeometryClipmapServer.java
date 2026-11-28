package org.realscale.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
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
import org.realscale.neoforge.client.clipmap.ClipmapBiomeMaterial;
import org.realscale.neoforge.client.clipmap.GeometryClipmapLayout;
import org.realscale.neoforge.client.clipmap.NaturalAutomateHeightMapping;
import org.realscale.neoforge.network.ClipmapHeightTilePayload;
import org.realscale.neoforge.network.ClipmapSurfaceProxyPayload;
import org.realscale.neoforge.network.ClipmapStatusPayload;

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
    private static final long STALL_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90L);

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

    private static final ThreadPoolExecutor PROXY_SAMPLER = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32),
            task -> {
                Thread thread = new Thread(task, "RealScale-Clipmap-Proxy");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private GeometryClipmapServer() {}

    public static void request(ServerPlayer player, int nearRenderDistanceChunks,
            int gridCells, long requestId) {
        if (nearRenderDistanceChunks < 2 || nearRenderDistanceChunks > 64
                || !org.realscale.neoforge.network.ClipmapRequestPayload
                        .isSupportedGridCells(gridCells)
                || requestId <= 0L) return;
        UUID id = player.getUUID();
        Session current = SESSIONS.get(id);
        // A restarted client can reset its local request counter. A new network
        // connection is a new session, so an old UUID session must not reject
        // the first request from that connection as stale.
        if (current != null && current.connectionToken != player.connection) {
            current.invalidate();
            SESSIONS.remove(id, current);
            current = null;
        }
        if (current != null && requestId < current.requestId) return;
        // Same-ID requests are client heartbeats. Resend cached data instead
        // of ignoring a retry or recomputing the complete six-ring snapshot.
        if (current != null && requestId == current.requestId) {
            if (current.nearChunks != nearRenderDistanceChunks
                    || current.gridCells != gridCells) return;
            if (current.cachedHeightPayloads != null) {
                sendSnapshot(player, current);
            } else {
                sendStatus(player, current, current.status, current.completedLevels,
                        current.statusDetail);
            }
            return;
        }
        if (current == null || current.nearChunks != nearRenderDistanceChunks
                || current.gridCells != gridCells) {
            if (current != null) current.invalidate();
            current = new Session(nearRenderDistanceChunks, gridCells,
                    requestId, player.connection);
            SESSIONS.put(id, current);
        } else {
            current.invalidate();
            current.requestId = requestId;
        }
        current.status = ClipmapStatusPayload.REQUEST_ACCEPTED;
        sendStatus(player, current, ClipmapStatusPayload.REQUEST_ACCEPTED, 0, "request accepted");
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
        PROXY_SAMPLER.getQueue().clear();
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
            sendStatus(player, session, ClipmapStatusPayload.UNSUPPORTED_WORLD, 0,
                    "active world generator is not a NoiseBasedChunkGenerator");
            removeSession(player.getUUID(), session);
            return;
        }

        ResourceLocation settingsId = generator.generatorSettings().unwrapKey()
                .map(ResourceKey::location).orElse(null);
        if (!isNaturalAutomateSettings(settingsId)) {
            sendStatus(player, session, ClipmapStatusPayload.UNSUPPORTED_WORLD, 0,
                    "active noise settings are " + String.valueOf(settingsId)
                            + "; expected realscale:natural_automate or realscale:natural_automate_v2");
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
        if (session.inFlight) {
            long stalledFor = System.nanoTime() - session.lastProgressNanos;
            boolean ringQueued = session.ringTask != null
                    && SAMPLER.getQueue().contains(session.ringTask);
            // Queue membership alone cannot distinguish a running task from
            // a completed ring waiting for its server-thread callback.
            boolean samplerHasStopped = session.ringTaskRunning
                    || (!ringQueued && session.pendingRingCallbacks.get() == 0);
            if (session.lastProgressNanos != 0L && stalledFor > STALL_TIMEOUT_NANOS
                    && samplerHasStopped) {
                session.cancelTasks();
                session.requestEpoch.advance();
                session.inFlight = false;
                session.status = ClipmapStatusPayload.STALLED;
                session.completedLevels = 0;
                session.cachedHeightPayloads = null;
                session.cachedProxyPayload = null;
                session.requestEpoch.defer(serverTickCounter, RETRY_BACKOFF_TICKS);
                String stalledDetail = session.sampleActive
                        ? String.format(java.util.Locale.ROOT,
                                "sampler made no progress for 90 seconds; density sample (%d,%d) active for %.1fs; cancelling and retrying",
                                session.lastSampleX, session.lastSampleZ,
                                (System.nanoTime() - session.lastSampleStartedNanos) / 1_000_000_000.0)
                        : String.format(java.util.Locale.ROOT,
                                "sampler made no progress for 90 seconds; last density sample (%d,%d) took %.1fms; cancelling and retrying",
                                session.lastSampleX, session.lastSampleZ,
                                session.lastSampleDurationNanos / 1_000_000.0);
                sendStatus(player, session, ClipmapStatusPayload.STALLED, 0, stalledDetail);
            }
            return;
        }
        if (!session.requestEpoch.mayStart(serverTickCounter)) return;

        GeometryClipmapLayout planner =
                GeometryClipmapLayout.forRenderDistanceChunks(
                        session.nearChunks, session.gridCells);
        GeometryClipmapLayout.Layout nextLayout = planner.compute(
                player.getX(), player.getZ(), session.layout);
        if (session.layout != null
                && sameCentre(session.layout, nextLayout)) {
            return;
        }

        // A proxy scan can perform thousands of climate queries against the
        // same worldgen graph. Stop it before a new height window begins.
        // This prevents proxy work from competing with the priority terrain sampler.
        cancelProxyTasksForHeightSampling();
        RandomState randomState = level.getChunkSource().randomState();
        DensityFunction terrainDensity = randomState.router().depth();
        List<ClipmapHeightGrid> previousGrids = session.grids;
        String targetDimension = dimensionKey;
        long targetSeed = seed;
        UUID playerId = player.getUUID();
        long requestEpoch = session.requestEpoch.current();
        long targetRequestId = session.requestId;
        session.inFlight = true;
        session.status = ClipmapStatusPayload.QUEUED;
        session.completedLevels = 0;
        session.lastProgressNanos = System.nanoTime();
        session.cachedHeightPayloads = new ArrayList<>(MAX_LEVELS);
        session.cachedProxyPayload = null;
        sendStatus(player, session, ClipmapStatusPayload.QUEUED, 0,
                "sampler queue depth " + SAMPLER.getQueue().size());

        ClipmapHeightGrid.SurfaceHeightSampler heightSampler = (x, z) -> {
            long sampleStarted = System.nanoTime();
            session.lastSampleX = x;
            session.lastSampleZ = z;
            session.lastSampleStartedNanos = sampleStarted;
            session.sampleActive = true;
            session.lastProgressNanos = sampleStarted;
            try {
                double signal = terrainDensity.compute(
                        new DensityFunction.SinglePointContext(x, SAMPLE_Y, z));
                double surface = NaturalAutomateHeightMapping.surfaceY(signal);
                if (!Double.isFinite(surface)) {
                    throw new IllegalStateException("non-finite surface");
                }
                return (float) Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, surface));
            } finally {
                long elapsed = System.nanoTime() - sampleStarted;
                session.lastSampleDurationNanos = elapsed;
                session.sampleActive = false;
                session.lastProgressNanos = System.nanoTime();
                if (DEBUG && elapsed > TimeUnit.MILLISECONDS.toNanos(250L)) {
                    System.out.printf(java.util.Locale.ROOT,
                            "[RealScale Geometry Clipmap] slow density sample x=%d z=%d elapsed=%.1fms%n",
                            x, z, elapsed / 1_000_000.0);
                }
            }
        };

        // One ring per queue turn: a second player need not wait for six rings.
        // A window is allowed to finish even if the camera has moved meanwhile.
        class RingTask implements Runnable {
            private int i;
            private final List<ClipmapHeightGrid> nextGrids = new ArrayList<>(MAX_LEVELS);
            @Override public void run() {
                session.ringTaskRunning = true;
                try {
                    if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                    long started = System.nanoTime();
                    session.lastProgressNanos = started;
                    final int levelIndex = i;
                    ClipmapHeightGrid previous = previousGrids == null
                            ? null : previousGrids.get(i);
                    int[] nextProgressThreshold = {0};
                    ClipmapHeightGrid grid = ClipmapHeightGrid.sample(
                            nextLayout.levels().get(i), nextLayout.gridCells(),
                            heightSampler, previous,
                            () -> session.requestEpoch.isCurrent(requestEpoch),
                            percent -> {
                                if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                                session.lastProgressNanos = System.nanoTime();
                                if (percent >= nextProgressThreshold[0]) {
                                    nextProgressThreshold[0] = percent + 10;
                                    server.execute(() -> {
                                        ServerPlayer live = livePlayer();
                                        if (live != null) sendStatus(live, session,
                                                ClipmapStatusPayload.SAMPLING, levelIndex,
                                                "sampling LOD " + (levelIndex + 1) + "/6: " + percent + "%");
                                    });
                                }
                            });
                    if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                    nextGrids.add(grid);
                    ClipmapHeightTilePayload payload = toPayload(targetDimension, targetRequestId,
                            session.nearChunks, nextLayout, i, grid,
                            generator.getBiomeSource(), randomState.sampler());
                    int completedLevels = ++i;
                    double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
                    int newVertices = grid.newlySampledVertexCount();
                    session.pendingRingCallbacks.incrementAndGet();
                    server.execute(() -> {
                        try {
                            ServerPlayer live = livePlayer();
                            if (live == null) return;
                            session.cachedHeightPayloads.add(payload);
                        PacketDistributor.sendToPlayer(live, payload);
                        String detail = String.format(java.util.Locale.ROOT,
                                "LOD %d/6 sent; %d new vertices; %.1fms; %.0f vertices/s; cancelled=%d",
                                completedLevels, newVertices, elapsedMs,
                                newVertices * 1000.0 / Math.max(0.001, elapsedMs),
                                session.cancelledTasks);
                        sendStatus(live, session, completedLevels == MAX_LEVELS
                                ? ClipmapStatusPayload.SNAPSHOT_SENT : ClipmapStatusPayload.SAMPLING,
                                completedLevels, detail);
                        if (completedLevels == MAX_LEVELS) {
                            session.layout = nextLayout;
                            session.grids = List.copyOf(nextGrids);
                            session.inFlight = false;
                            session.ringTask = null;
                            scheduleProxies(server, session, playerId, targetDimension,
                                    targetRequestId, targetSeed, requestEpoch, generator,
                                    randomState, nextLayout);
                            }
                        } finally {
                            session.pendingRingCallbacks.decrementAndGet();
                        }
                    });
                    if (completedLevels < MAX_LEVELS && session.requestEpoch.isCurrent(requestEpoch)) {
                        session.lastProgressNanos = System.nanoTime();
                        SAMPLER.execute(this);
                    }
                } catch (RuntimeException failure) {
                    if (!session.requestEpoch.isCurrent(requestEpoch)) return;
                    session.pendingRingCallbacks.incrementAndGet();
                    server.execute(() -> {
                        try {
                            ServerPlayer live = livePlayer();
                            if (live == null) return;
                            session.inFlight = false;
                            session.ringTask = null;
                            session.requestEpoch.defer(serverTickCounter, RETRY_BACKOFF_TICKS);
                            sendStatus(live, session, ClipmapStatusPayload.FAILED,
                                    session.completedLevels, failure.getClass().getSimpleName()
                                            + ": " + String.valueOf(failure.getMessage()));
                        } finally {
                            session.pendingRingCallbacks.decrementAndGet();
                        }
                    });
                } finally {
                    session.ringTaskRunning = false;
                }
            }

            private ServerPlayer livePlayer() {
                if (SESSIONS.get(playerId) != session || session.requestId != targetRequestId
                        || !session.requestEpoch.isCurrent(requestEpoch)) return null;
                ServerPlayer live = server.getPlayerList().getPlayer(playerId);
                if (live == null || live.connection != session.connectionToken) return null;
                ServerLevel world = live.serverLevel();
                if (!world.dimension().location().toString().equals(targetDimension)
                        || world.getSeed() != targetSeed
                        || world.getChunkSource().getGenerator() != generator) return null;
                return live;
            }
        }
        try {
            session.ringTask = new RingTask();
            SAMPLER.execute(session.ringTask);
        } catch (RuntimeException rejected) {
            session.ringTask = null;
            session.inFlight = false;
            session.requestEpoch.defer(serverTickCounter, RETRY_BACKOFF_TICKS);
            sendStatus(player, session, ClipmapStatusPayload.BUSY, 0,
                    "sampler queue full; retrying after backoff");
        }
    }

    private static void cancelProxyTasksForHeightSampling() {
        for (Session active : SESSIONS.values()) {
            if (active.invalidateProxyTask() && DEBUG) {
                System.out.println("[RealScale Geometry Clipmap] PROXY_TASK_CANCELLED reason=height-window");
            }
        }
    }

    private static void scheduleProxies(MinecraftServer server, Session session, UUID playerId,
            String dimension, long requestId, long seed, long epoch,
            NoiseBasedChunkGenerator generator, RandomState randomState,
            GeometryClipmapLayout.Layout layout) {
        session.invalidateProxyTask();
        long token = session.proxyGeneration;
        java.util.function.BooleanSupplier current = () ->
                session.requestEpoch.isCurrent(epoch) && session.proxyGeneration == token;
        Runnable task = () -> {
            if (!current.getAsBoolean()) return;
            long started = System.nanoTime();
            try {
                var proxies = VisibleSurfaceProxyGenerator.collect(generator, randomState,
                        layout, seed, current);
                if (!current.getAsBoolean()) return;
                var packet = new ClipmapSurfaceProxyPayload(dimension, requestId,
                        layout.levels().get(0).centerX(), layout.levels().get(0).centerZ(), proxies);
                server.execute(() -> {
                    if (!current.getAsBoolean() || SESSIONS.get(playerId) != session
                            || session.layout != layout) return;
                    ServerPlayer live = server.getPlayerList().getPlayer(playerId);
                    if (live == null || live.connection != session.connectionToken
                            || !live.serverLevel().dimension().location().toString().equals(dimension)
                            || live.serverLevel().getSeed() != seed) return;
                    // The next terrain window may be in progress. Do not cache
                    // proxies under that new window's partial height payloads.
                    if (!session.inFlight) session.cachedProxyPayload = packet;
                    PacketDistributor.sendToPlayer(live, packet);
                    if (DEBUG) System.out.printf(java.util.Locale.ROOT,
                            "[RealScale Geometry Clipmap] proxies=%d sample=%.1fms cancelledProxyTasks=%d%n",
                            proxies.size(), (System.nanoTime() - started) / 1_000_000.0,
                            session.cancelledProxyTasks);
                    session.proxyTask = null;
                });
            } catch (RuntimeException failure) {
                if (current.getAsBoolean()) System.err.println(
                        "[RealScale Geometry Clipmap] proxy sampling failed: " + failure);
            }
        };
        session.proxyTask = task;
        try { PROXY_SAMPLER.execute(task); }
        catch (java.util.concurrent.RejectedExecutionException rejected) {
            session.proxyTask = null;
        }
    }

    private static ClipmapHeightTilePayload toPayload(String dimension,long requestId,
            int nearChunks,GeometryClipmapLayout.Layout layout,int levelIndex,
            ClipmapHeightGrid grid,BiomeSource biomeSource,Climate.Sampler climateSampler){
        int side=grid.gridSide(),spacing=grid.cellSpacingBlocks();
        float[] heights=grid.copyHeights();short[] encoded=new short[heights.length];
        byte[] materials=new byte[heights.length];
        for(int z=0;z<side;z++){
            long worldZ=grid.originZ()+(long)z*spacing;
            for(int x=0;x<side;x++){
                int index=z*side+x;float height=heights[index];
                if(!Float.isFinite(height)){
                    encoded[index]=(short)ClipmapHeightQuantizer.encode(MIN_HEIGHT,MIN_HEIGHT,MAX_HEIGHT);
                    materials[index]=ClipmapBiomeMaterial.UNKNOWN;continue;
                }
                long worldX=grid.originX()+(long)x*spacing;
                int bx=Math.toIntExact(worldX),bz=Math.toIntExact(worldZ),by=Math.round(height);
                encoded[index]=(short)ClipmapHeightQuantizer.encode(height,MIN_HEIGHT,MAX_HEIGHT);
                String path=biomeSource.getNoiseBiome(QuartPos.fromBlock(bx),QuartPos.fromBlock(by),
                        QuartPos.fromBlock(bz),climateSampler).unwrapKey()
                        .map(ResourceKey::location).map(ResourceLocation::getPath).orElse("");
                materials[index]=ClipmapBiomeMaterial.fromBiomePath(path);
            }
        }
        return new ClipmapHeightTilePayload(dimension,requestId,nearChunks,layout.gridCells(),
                levelIndex,spacing,grid.originX(),grid.originZ(),MIN_HEIGHT,MAX_HEIGHT,encoded,materials);
    }

    private static void sendStatus(ServerPlayer player, Session session,
            int state, int completedLevels, String detail) {
        String dimension = player.serverLevel().dimension().location().toString();
        String safeDetail = detail == null ? "" : detail;
        if (safeDetail.length() > 256) safeDetail = safeDetail.substring(0, 256);
        session.status = state;
        session.completedLevels = Math.max(0, Math.min(MAX_LEVELS, completedLevels));
        session.statusDetail = safeDetail;
        PacketDistributor.sendToPlayer(player, new ClipmapStatusPayload(
                dimension, session.requestId, state,
                Math.max(0, Math.min(MAX_LEVELS, completedLevels)),
                MAX_LEVELS, Math.min(1024, SAMPLER.getQueue().size()), safeDetail));
        if (DEBUG) {
            System.out.printf(java.util.Locale.ROOT,
                    "[RealScale Geometry Clipmap] status=%d request=%d progress=%d/%d detail=%s%n",
                    state, session.requestId, completedLevels, MAX_LEVELS, safeDetail);
        }
    }

    private static void sendSnapshot(ServerPlayer player, Session session) {
        if (session.cachedHeightPayloads == null) return;
        for (ClipmapHeightTilePayload payload : session.cachedHeightPayloads) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        if (session.cachedProxyPayload != null)
            PacketDistributor.sendToPlayer(player, session.cachedProxyPayload);
        sendStatus(player, session, session.status, session.completedLevels, session.statusDetail);
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
        private final int gridCells;
        private final Object connectionToken;
        private long requestId;
        private String dimensionKey;
        private String settingsId;
        private long seed;
        private GeometryClipmapLayout.Layout layout;
        private List<ClipmapHeightGrid> grids;
        private boolean inFlight;
        private Runnable ringTask;
        private Runnable proxyTask;
        private volatile long proxyGeneration;
        private long cancelledTasks;
        private long cancelledProxyTasks;
        private volatile long lastProgressNanos;
        private volatile boolean ringTaskRunning;
        private final java.util.concurrent.atomic.AtomicInteger pendingRingCallbacks =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile long lastSampleStartedNanos;
        private volatile long lastSampleDurationNanos;
        private volatile boolean sampleActive;
        private volatile int lastSampleX;
        private volatile int lastSampleZ;
        private volatile int status = ClipmapStatusPayload.REQUEST_ACCEPTED;
        private volatile int completedLevels;
        private volatile String statusDetail = "request accepted";
        private List<ClipmapHeightTilePayload> cachedHeightPayloads;
        private ClipmapSurfaceProxyPayload cachedProxyPayload;
        private final ClipmapRequestEpoch requestEpoch = new ClipmapRequestEpoch();

        private Session(int nearChunks, int gridCells,
                long requestId, Object connectionToken) {
            this.nearChunks = nearChunks;
            this.gridCells = gridCells;
            this.requestId = requestId;
            this.connectionToken = connectionToken;
        }

        private boolean invalidateProxyTask() {
            proxyGeneration++;
            if (proxyTask == null) return false;
            PROXY_SAMPLER.remove(proxyTask);
            proxyTask = null;
            cancelledProxyTasks++;
            return true;
        }

        private void cancelTasks() {
            if (ringTask != null) { SAMPLER.remove(ringTask); cancelledTasks++; ringTask = null; }
            invalidateProxyTask();
        }

        private void invalidate() {
            cancelTasks();
            requestEpoch.advance();
            layout = null;
            grids = null;
            inFlight = false;
            cachedHeightPayloads = null;
            cachedProxyPayload = null;
            completedLevels = 0;
            status = ClipmapStatusPayload.REQUEST_ACCEPTED;
            statusDetail = "request invalidated";
            lastProgressNanos = 0L;
        }

        private void resetWorld(String dimension, long worldSeed,
                ResourceLocation settings) {
            dimensionKey = dimension;
            seed = worldSeed;
            settingsId = settings.toString();
            cancelTasks();
            requestEpoch.advance();
            layout = null;
            grids = null;
            inFlight = false;
            cachedHeightPayloads = null;
            cachedProxyPayload = null;
            completedLevels = 0;
            status = ClipmapStatusPayload.REQUEST_ACCEPTED;
            statusDetail = "world reset";
            lastProgressNanos = 0L;
        }
    }
}
