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
import net.neoforged.neoforge.event.tick.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Experimental GUI-only Chaotianmen GIS placement in INTEGRATED singleplayer.
 * Press G, review immutable DEM/source hashes, acknowledge backup, then
 * explicitly start a guarded 256-block/tick job; Close/ESC automatically pauses.
 * Does not use Minecraft chat or commands, never auto-applies
 * on start, and refuses all foreign worlds/multiplayer/dimensions.
 *
 * NO claim that an imported OSM footprint is a complete verified city.
 */
@Mod(value = "realscale", dist = Dist.CLIENT)
public final class RealScaleChongqingGisClient {
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

    private static BlockState sourceBlock(String kind, boolean wall) {
        if (wall) return Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        return switch (kind) {
            case "building_footprint" -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case "road" -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case "railway" -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case "water_reference" -> Blocks.BLUE_CONCRETE.defaultBlockState();
            default -> throw new IllegalArgumentException("Invalid GIS palette");
        };
    }

    private static boolean canReplace(BlockState old, BlockState newState,
                                      boolean wall) {
        if (old.is(newState.getBlock())) return true;
        if (old.isAir()) return true;
        if (wall) return false; // never flatten foreign buildings / cliffs
        return old.is(Blocks.GRASS_BLOCK) || old.is(Blocks.DIRT)
                || old.is(Blocks.STONE) || old.is(Blocks.COARSE_DIRT)
                || old.is(Blocks.GRAVEL);
    }

    /** One plan bound to one running integrated server: no remote mutations. */
    private static final class Session {
        final MinecraftServer server;
        final ChongqingGisPlan plan;
        volatile int rowIndex;
        int x;
        int yOffset;
        volatile long applied;
        volatile String status;
        volatile boolean running;
        volatile UUID authorizedPlayer;
        Session(MinecraftServer server, ChongqingGisPlan plan) {
            this.server=server; this.plan=plan; this.x=plan.rows.get(0).x0();
            status="Plan validated. Confirm an external world backup first.";
        }

        void start(UUID playerId) {
            if (rowIndex >= plan.rows.size()) {
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
                        && rowIndex < plan.rows.size()) {
                    ChongqingGisPlan.Row row=plan.rows.get(rowIndex);
                    if (x < row.x0() || x > row.x1()) {
                        x=row.x0();yOffset=0;
                    }
                    int y=row.y()+yOffset;
                    BlockPos pos=new BlockPos(x,y,row.z());
                    if (!level.getChunkSource().hasChunk(x>>4,row.z()>>4)) {
                        pause();
                        status="Missing loaded chunk "+(x>>4)+","+(row.z()>>4)
                                +"; pregenerate/visit first. Paused.";
                        return;
                    }
                    BlockState target=sourceBlock(row.kind(),yOffset>0);
                    BlockState old=level.getBlockState(pos);
                    if (!canReplace(old,target,yOffset>0)) {
                        pause();
                        status="Protected collision at "+x+","+y+","+row.z()
                                +"; paused; inspect the location before retry.";
                        return;
                    }
                    if (!old.is(target.getBlock())) {
                        if (!level.setBlock(pos,target,3)) {
                            pause();
                            status="World rejected placement at "+x+","+y+","+row.z();
                            return;
                        }
                    }
                    applied++;writes++;yOffset++;
                    if (yOffset > row.height()) {
                        yOffset=0;x++;
                        if (x > row.x1()) {
                            rowIndex++;
                            if (rowIndex < plan.rows.size())
                                x=plan.rows.get(rowIndex).x0();
                        }
                    }
                }
                if (rowIndex == plan.rows.size()) {
                    running=false;
                    status="Finished "+applied+" placements. Visually verify GIS fidelity.";
                } else {
                    status="RUNNING: "+applied+"/"+plan.blockWrites
                            +" block writes; close GUI to pause.";
                }
            } catch (RuntimeException ex) {
                pause();
                status="Placement stopped: "+ex.getClass().getSimpleName()
                        +". Inspect logs and world backup.";
            }
        }
    }

    private static final class GisScreen extends Screen {
        private String message="Load an offline, attributed GIS placement plan.";
        private boolean backupConfirmed=false;

        GisScreen() {
            super(Component.literal("Special Chongqing | GIS Placement Lab"));
        }

        @Override protected void init() {
            int cx=width/2;
            addRenderableWidget(Button.builder(Component.literal("Load & verify source plan"),
                    b -> load()).bounds(cx-125,height/2-31,250,20).build());
            addRenderableWidget(Button.builder(Component.literal(
                    "I backed up this NEW world: NO"),b -> {
                backupConfirmed=!backupConfirmed;
                b.setMessage(Component.literal(
                    "I backed up this NEW world: "+(backupConfirmed?"YES":"NO")));
            }).bounds(cx-125,height/2-6,250,20).build());
            addRenderableWidget(Button.builder(
                    Component.literal("Start / Pause 256 blocks per tick"),
                    b -> apply()).bounds(cx-125,height/2+19,250,20).build());
            addRenderableWidget(Button.builder(Component.literal("Close / Pause"),
                    b -> onClose()).bounds(cx-125,height/2+44,250,20).build());
        }

        private void load() {
            try {
                Files.createDirectories(ChongqingGisPlan.INPUT.getParent());
                ChongqingGisPlan plan=ChongqingGisPlan.read(ChongqingGisPlan.INPUT);
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
                message="Source "+plan.sourceHash.substring(0,12)
                        +"...; "+plan.rows.size()+" rows; "+plan.blockWrites+" writes."
                        +" Source geometry is NOT independently verified.";
            } catch (IOException | RuntimeException ex) {
                message="Plan rejected: "+ex.getMessage();
            }
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
            if (active.rowIndex >= active.plan.rows.size()) {
                message="All batches completed; do not replay.";
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
                    "File: config/realscale/chaotianmen-gis-plan.json",
                    cx,height/2-61,0xDDDDDD);
            String info = active != null ? active.status : "No plan loaded.";
            graphics.drawCenteredString(font,truncate(message,100),
                    cx,height/2+82,0xFFFFFF);
            graphics.drawCenteredString(font,truncate(info,108),
                    cx,height/2+100,0xCCCCCC);
            graphics.drawCenteredString(font,
                    "No automatic run, no 3D facade claims, no normal TVA changes.",
                    cx,height/2+120,0xFFDD88);
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
