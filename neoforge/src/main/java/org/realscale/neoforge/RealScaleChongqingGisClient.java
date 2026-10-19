package org.realscale.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Experimental GUI-only Chaotianmen GIS placement in INTEGRATED singleplayer.
 * Press G, review immutable DEM/source hashes, acknowledge backup, then
 * explicitly start a guarded 256-block/tick job; Close/ESC automatically pauses.
 * Native V2 uses schematic walls/roof only for source-backed towers:
 * interiors remain air rather than being filled as millions of concrete blocks.
 * Does not use Minecraft chat or commands, never auto-applies
 * on start, and refuses all foreign worlds/multiplayer/dimensions.
 *
 * NO claim that an imported OSM footprint is a complete verified city.
 */
@Mod(value = "realscale", dist = Dist.CLIENT)
public final class RealScaleChongqingGisClient {
    // The in-game G-key UI is the PRIMARY player interaction path.
    // No external conversion scripts, downloads or chat commands required
    // for the included authentic 500m pilot geometry.
    private static final KeyMapping OPEN = new KeyMapping(
            "key.realscale.chongqing_gis", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.realscale");
    private static final int WRITES_PER_TICK = 256;
    private static volatile Session active;

    public RealScaleChongqingGisClient(IEventBus modBus) {
        modBus.addListener(RealScaleChongqingGisClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(RealScaleChongqingGisClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(RealScaleChongqingGisClient::onServerTick);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.level != null && mc.screen == null)
                mc.setScreen(new GisScreen());
        }
        if (active != null && active.server != mc.getSingleplayerServer())
            active = null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Session session = active;
        if (session != null && session.server == event.getServer()
                && session.running && event.hasTime())
            session.applyTick();
    }

    private static boolean validWorld(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null || !player.getAbilities().instabuild
                || player.level().dimension() != Level.OVERWORLD)
            return false;
        ServerLevel level = server.overworld();
        if (!(level.getChunkSource().getGenerator() instanceof
                NoiseBasedChunkGenerator generator))
            return false;
        return generator.generatorSettings().unwrapKey()
                .map(key -> key.location().toString()
                        .equals("realscale:special_chongqing_sky"))
                .orElse(false);
    }

    private static BlockState sourceBlock(String kind, boolean wall, boolean roof) {
        if (roof) return Blocks.GRAY_CONCRETE.defaultBlockState();
        if (wall) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        return switch (kind) {
            case "building_footprint" -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case "road" -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case "railway" -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case "water_reference" -> Blocks.BLUE_CONCRETE.defaultBlockState();
            default -> throw new IllegalArgumentException("Invalid GIS palette");
        };
    }

    private static boolean isBareTerrain(BlockState old) {
        return old.is(Blocks.GRASS_BLOCK) || old.is(Blocks.DIRT)
                || old.is(Blocks.STONE) || old.is(Blocks.COARSE_DIRT)
                || old.is(Blocks.GRAVEL);
    }

    private static boolean canReplace(BlockState old, BlockState target,
                                      boolean wall) {
        if (old.is(target.getBlock())) return true;
        if (old.isAir()) return true;
        // Never break water, trees, chests, existing buildings or player
        // constructions. Bare stone/soil can only be replaced at the top.
        return !wall && isBareTerrain(old);
    }

    private static boolean terrainMatches(ServerLevel level, BlockPos pos,
                                          BlockState old, BlockState target) {
        // The external DEM grid is NOT a voxel heightmap. Real saves can
        // have a different surface Y. Refuse misaligned columns, don't
        // place floating roads or bury buildings in a wall of terrain.
        int firstAirY = level.getHeight(Heightmap.Types.WORLD_SURFACE,
                                        pos.getX(),pos.getZ());
        if (firstAirY == pos.getY()) {
            return old.isAir() && isBareTerrain(level.getBlockState(pos.below()));
        }
        if (firstAirY == pos.getY() + 1) {
            return isBareTerrain(old) || old.is(target.getBlock());
        }
        return false;
    }

    /** One plan bound to one running integrated server: no remote mutations. */
    private static final class Session {
        final MinecraftServer server;
        final ChongqingGisPlan plan;
        final ChongqingGisChunkQueue queue;
        private int lastChunkX=Integer.MIN_VALUE;
        private int lastChunkZ=Integer.MIN_VALUE;
        volatile long applied;
        volatile String status;
        volatile boolean running;
        volatile boolean claimed;
        volatile boolean previewGood;
        volatile UUID authorizedPlayer;
        Session(MinecraftServer server, ChongqingGisPlan plan) {
            this.server=server; this.plan=plan;
            this.queue=new ChongqingGisChunkQueue(plan);
            status="Plan validated. Confirm an external world backup first.";
        }

        void start(UUID playerId) {
            if (!previewGood) {
                status="Survey the REAL loaded surface first. No placement started.";
                return;
            }
            if (queue.isComplete()) {
                status="All source-aligned batches were already placed.";
                return;
            }
            authorizedPlayer=playerId;
            running=true;
            status="Placement RUNNING: <=256 writes/tick. Close screen to pause.";
        }

        void pause() {
            running=false;
            status="PAUSED at "+applied+"/"+plan.blockWrites+" writes.";
        }

        /**
         * Native button-driven DRY RUN, limited to at most 64 nearby
         * already loaded samples. Never calls setBlock or loads chunks.
         * This is not a substitute for future per-column collision checks.
         */
        void survey(UUID playerId) {
            if (running) {
                status="Pause placement before running a no-write survey.";
                return;
            }
            previewGood=false;
            status="Checking nearby saved-world surface without writes...";
            server.execute(() -> {
                ServerPlayer player=server.getPlayerList().getPlayer(playerId);
                if (!validWorld(server,player)) {
                    status="Survey requires local Creative Special Chongqing.";
                    return;
                }
                ServerLevel level=server.overworld();
                int px=player.blockPosition().getX();
                int pz=player.blockPosition().getZ();
                int checked=0,good=0,bad=0,notLoaded=0;
                for (ChongqingGisPlan.Row row : plan.rows) {
                    if (checked>=64) break;
                    if (Math.abs((long)row.z()-pz)>48
                            || row.x0()>px+48 || row.x1()<px-48) continue;
                    int sx=Math.max(row.x0(),Math.min(px,row.x1()));
                    if (!level.getChunkSource().hasChunk(sx>>4,row.z()>>4)) {
                        notLoaded++;
                        continue;
                    }
                    checked++;
                    BlockPos pos=new BlockPos(sx,row.y(),row.z());
                    BlockState target=sourceBlock(row.kind(),false,false);
                    BlockState old=level.getBlockState(pos);
                    if (terrainMatches(level,pos,old,target)
                            && canReplace(old,target,false)) good++;
                    else bad++;
                }
                previewGood=good>0 && bad==0;
                status=previewGood
                    ? "SURVEY PASS: "+good+" actual nearby columns aligned; "
                      +notLoaded+" chunks unavailable. No blocks changed."
                    : "SURVEY FAIL: "+good+" aligned / "+bad
                      +" misaligned; "+notLoaded+" unavailable. "
                      +"No writes. Move to mapped terrain or inspect DEM Y.";
            });
        }

        /**
         * A loaded chunk near the player is selected first. This avoids
         * ancient sorted-row starvation where the first GIS row is far
         * outside currently loaded chunks. NEVER generate chunks here.
         */
        private int[] nearestLoadedWork(ServerLevel level, ServerPlayer player) {
            int cx=Math.floorDiv(player.blockPosition().getX(),16);
            int cz=Math.floorDiv(player.blockPosition().getZ(),16);
            if (Math.abs((long)lastChunkX-cx)<=5
                    && Math.abs((long)lastChunkZ-cz)<=5
                    && queue.hasPending(lastChunkX,lastChunkZ)
                    && level.getChunkSource().hasChunk(lastChunkX,lastChunkZ))
                return new int[]{lastChunkX,lastChunkZ};
            for (int radius=0;radius<=5;radius++) {
                for (int dz=-radius;dz<=radius;dz++) {
                    for (int dx=-radius;dx<=radius;dx++) {
                        if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                        int x=cx+dx,z=cz+dz;
                        if (queue.hasPending(x,z)
                                && level.getChunkSource().hasChunk(x,z)) {
                            lastChunkX=x;lastChunkZ=z;
                            return new int[]{x,z};
                        }
                    }
                }
            }
            lastChunkX=Integer.MIN_VALUE;
            lastChunkZ=Integer.MIN_VALUE;
            return null;
        }

        // Runs only on the logical integrated-server tick thread.
        void applyTick() {
            try {
                ServerPlayer player=server.getPlayerList().getPlayer(authorizedPlayer);
                if (!validWorld(server,player)) {
                    running=false;
                    status="Blocked: local creative Special Chongqing world only.";
                    return;
                }
                ServerLevel level=server.overworld();
                int writes=0;
                while (running && writes < WRITES_PER_TICK
                        && !queue.isComplete()) {
                    int[] chunk=nearestLoadedWork(level,player);
                    if (chunk==null) {
                        pause();
                        previewGood=false;
                        status="No remaining GIS construction in loaded nearby chunks. "
                                +"Walk/fly toward unfinished geometry; re-survey and Start. "
                                +"Already completed "+applied+" writes are preserved.";
                        return;
                    }
                    int cx=chunk[0],cz=chunk[1];
                    ChongqingGisChunkQueue.Placement work=queue.peek(cx,cz);
                    if (work==null) {
                        pause();
                        status="GIS chunk queue inconsistent; world is protected.";
                        return;
                    }
                    ChongqingGisPlan.Row row=plan.rows.get(work.rowIndex());
                    int x=work.x(),offset=work.yOffset(),y=row.y()+offset;
                    BlockPos pos=new BlockPos(x,y,row.z());
                    if (!level.getChunkSource().hasChunk(cx,cz)) {
                        pause();
                        status="GIS target chunk unloaded before placement; paused.";
                        return;
                    }
                    BlockState target=sourceBlock(row.kind(),offset>0,
                            row.height()>0 && offset==row.height());
                    BlockState old=level.getBlockState(pos);
                    if (offset==0 && !terrainMatches(level,pos,old,target)) {
                        pause();
                        status="Actual saved surface differs from DEM at "
                                +x+","+row.z()+" y="+y+". No Y guessing.";
                        return;
                    }
                    if (!canReplace(old,target,offset>0)) {
                        pause();
                        status="Protected block collision at "+x+","+y+","+row.z()
                                +"; inspect/restore backup; no overwrite.";
                        return;
                    }
                    if (!claimed) {
                        // Create the persistent one-shot marker before ANY
                        // world mutation, even after choosing a later chunk.
                        try {
                            Path worldRoot=server.getWorldPath(LevelResource.ROOT);
                            ChongqingPlacementLock.claim(worldRoot,
                                    plan.planHash,plan.sourceHash);
                            claimed=true;
                        } catch (IOException ex) {
                            pause();
                            status="Save already has a GIS lock or lock cannot be "
                                    +"created. Restore a clean backup.";
                            return;
                        }
                    }
                    if (!old.is(target.getBlock())
                            && !level.setBlock(pos,target,3)) {
                        pause();
                        status="World refused placement at "+x+","+y+","+row.z();
                        return;
                    }
                    queue.acknowledge(cx,cz);
                    applied=queue.completedWrites();
                    writes++;
                }
                if (queue.isComplete()) {
                    running=false;
                    status="Completed "+applied+" OSM source-grounded block "
                            +"operations. Visually verify / save backup.";
                } else {
                    status="RUNNING: "+applied+"/"+plan.blockWrites
                            +" writes; "+queue.remainingChunks()
                            +" GIS chunks remain; ESC pauses.";
                }
            } catch (RuntimeException ex) {
                pause();
                status="GIS stopped: "+ex.getClass().getSimpleName()
                        +". Inspect save and world backup.";
            }
        }
    }

    private static final class GisScreen extends Screen {
        private String message="Load an offline, attributed GIS placement plan.";
        private boolean backupConfirmed=false;
        private ChongqingGisMiniMap miniMap;
        private int mapFilter=0;
        private static final String[] MAP_LAYERS={
            "All", "Buildings", "Roads", "Rail", "Water"
        };

        GisScreen() {
            super(Component.literal("Special Chongqing | GIS Placement Lab"));
        }

        @Override protected void init() {
            int cx=width/2;
            addRenderableWidget(Button.builder(Component.literal("Load & verify source plan"),
                    b -> load()).bounds(cx-125,height/2-39,250,20).build());
            addRenderableWidget(Button.builder(
                    Component.literal("Survey nearby terrain (NO writes)"),
                    b -> survey()).bounds(cx-125,height/2-14,250,20).build());
            addRenderableWidget(Button.builder(Component.literal(
                    "I backed up this NEW world: NO"),b -> {
                backupConfirmed=!backupConfirmed;
                b.setMessage(Component.literal(
                    "I backed up this NEW world: "+(backupConfirmed?"YES":"NO")));
            }).bounds(cx-125,height/2+11,250,20).build());
            addRenderableWidget(Button.builder(
                    Component.literal("Start / Pause 256 blocks per tick"),
                    b -> apply()).bounds(cx-125,height/2+36,250,20).build());
            addRenderableWidget(Button.builder(Component.literal("Close / Pause"),
                    b -> onClose()).bounds(cx-125,height/2+61,250,20).build());
            if (width >= 740 && height >= 320) {
                addRenderableWidget(Button.builder(
                        Component.literal("GIS map: "+MAP_LAYERS[mapFilter]),
                        b -> {
                            mapFilter=(mapFilter+1)%MAP_LAYERS.length;
                            b.setMessage(Component.literal(
                                    "GIS map: "+MAP_LAYERS[mapFilter]));
                        }).bounds(cx+150,height/2+64,144,20).build());
            }
        }

        private void load() {
            try {
                // A matching authentic GIS plan ships INSIDE the JAR.
                // A locally supplied, explicit override is supported for
                // later surveyed sources; never silently fall back when
                // such a file is invalid.
                boolean custom=Files.exists(ChongqingGisPlan.INPUT);
                ChongqingGisPlan plan=custom
                        ? ChongqingGisPlan.read(ChongqingGisPlan.INPUT)
                        : ChongqingGisPlan.readBundled();
                MinecraftServer server=Minecraft.getInstance().getSingleplayerServer();
                if (server == null) {
                    message="Only integrated singleplayer worlds are supported.";
                    return;
                }
                if (active == null || active.server != server
                        || !active.plan.planHash.equals(plan.planHash)) {
                    if (active != null && active.server == server && active.applied != 0) {
                        message="Another plan already wrote blocks in this session; restart safely.";
                        return;
                    }
                    active=new Session(server,plan);
                }
                miniMap=ChongqingGisMiniMap.of(plan);
                if (!active.claimed && ChongqingPlacementLock.alreadyClaimed(
                        server.getWorldPath(LevelResource.ROOT))) {
                    message="This world has an existing GIS placement lock. "
                            +"Restore the PRE-CONSTRUCTION backup to restart.";
                    return;
                }
                message=(custom ? "Local GIS" : "BUILT-IN authentic OSM")
                        +" source "+plan.sourceHash.substring(0,12)
                        +"...; "+plan.rows.size()+" rows; "+plan.blockWrites
                        +" writes. Survey nearby surface before construction.";
            } catch (IOException | RuntimeException ex) {
                message="Plan rejected: "+ex.getMessage();
            }
        }

        private void survey() {
            Minecraft mc=Minecraft.getInstance();
            if (active == null || active.server != mc.getSingleplayerServer()
                    || mc.player == null) {
                message="Load GIS plan in local Special Chongqing first.";
                return;
            }
            active.survey(mc.player.getUUID());
            message="No-write survey requested; check results below.";
        }

        private void apply() {
            Minecraft mc=Minecraft.getInstance();
            if (active == null || active.server != mc.getSingleplayerServer()) {
                message="Load a valid local GIS plan first.";
                return;
            }
            if (!backupConfirmed) {
                message="No writes: explicitly confirm your NEW world backup.";
                return;
            }
            if (mc.player == null) {
                message="No player attached.";
                return;
            }
            if (active.queue.isComplete()) {
                message="All batches completed; do not replay.";
                return;
            }
            if (!active.claimed && ChongqingPlacementLock.alreadyClaimed(
                    active.server.getWorldPath(LevelResource.ROOT))) {
                message="Blocked by persistent world lock. Restore earlier backup.";
                return;
            }
            if (active.running) {
                active.pause();
                message="Placement paused from UI.";
            } else {
                active.start(mc.player.getUUID());
                message="Placement started. Close this window to pause.";
            }
        }

        @Override public void render(GuiGraphics graphics,
                                     int mouseX,int mouseY,float partialTick) {
            renderBackground(graphics,mouseX,mouseY,partialTick);
            super.render(graphics,mouseX,mouseY,partialTick);
            int cx=width/2;
            graphics.drawCenteredString(font,"CHONGQING / REAL GIS / EXPERIMENTAL",
                    cx,height/2-115,0xFFFFFF);
            graphics.drawCenteredString(font,
                    "1 m = 1 block | Overworld only | G key | no chat commands",
                    cx,height/2-97,0xADD8E6);
            graphics.drawCenteredString(font,
                    "ONLY: singleplayer CREATIVE Special Chongqing world",
                    cx,height/2-79,0xFFAA88);
            graphics.drawCenteredString(font,
                    "Built-in 2026-10-19 OSM V2 | optional local GIS JSON override",
                    cx,height/2-61,0xDDDDDD);
            String info = active != null ? active.status : "No plan loaded.";
            graphics.drawCenteredString(font,truncate(message,100),
                    cx,height/2+104,0xFFFFFF);
            graphics.drawCenteredString(font,truncate(info,108),
                    cx,height/2+123,0xCCCCCC);
            graphics.drawCenteredString(font,
                    "No automatic run, no surveyed 3D facade claims, no TVA changes.",
                    cx,height/2+143,0xFFDD88);
            drawGisPreview(graphics);
        }
        /** 2D orthographic GIS source footprint, never a satellite map. */
        private void drawGisPreview(GuiGraphics graphics) {
            if (miniMap==null || width<740 || height<320) return;
            int left=width/2+150;
            int top=height/2-105;
            int size=ChongqingGisMiniMap.SIZE;
            graphics.fill(left-2,top-2,left+size*2+2,top+size*2+2,
                    0xCC1A1B20);
            int mask=switch (mapFilter) {
                case 1 -> ChongqingGisMiniMap.BUILDINGS;
                case 2 -> ChongqingGisMiniMap.ROADS;
                case 3 -> ChongqingGisMiniMap.RAILWAYS;
                case 4 -> ChongqingGisMiniMap.WATER;
                default -> 15;
            };
            for (int z=0;z<size;z++) {
                for (int x=0;x<size;x++) {
                    int bits=miniMap.at(x,z)&mask;
                    if (bits==0) continue;
                    int color=(bits&ChongqingGisMiniMap.BUILDINGS)!=0
                            ? 0xFFDFBCD1
                            : (bits&ChongqingGisMiniMap.RAILWAYS)!=0
                            ? 0xFFE8C078
                            : (bits&ChongqingGisMiniMap.ROADS)!=0
                            ? 0xFFBDBDBD : 0xFF489ACA;
                    graphics.fill(left+x*2,top+z*2,
                            left+x*2+2,top+z*2+2,color);
                }
            }
            graphics.drawCenteredString(font,"N ^  500m source XY",
                    left+size,top-14,0xDDDDDD);
            graphics.drawCenteredString(font,"Buildings / Roads / Rail / Water",
                    left+size,top+size*2+8,0xAAAAAA);
        }
        private static String truncate(String text, int max) {
            return text.length() <= max ? text : text.substring(0,max-3)+"...";
        }
        @Override public void onClose() {
            if (active != null && active.running) active.pause();
            super.onClose();
        }
        @Override public boolean isPauseScreen() { return false; }
    }
}
