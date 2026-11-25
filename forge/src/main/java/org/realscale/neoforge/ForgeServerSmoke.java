package org.realscale.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** CI-only integration checks; inert during normal gameplay. */
@Mod.EventBusSubscriber(modid = "realscale")
public final class ForgeServerSmoke {
    private ForgeServerSmoke() {}

    @SubscribeEvent
    public static void verify(ServerStartedEvent event) {
        if (!"server".equals(System.getenv("REALSCALE_FORGE_CI_SMOKE"))) return;
        var server = event.getServer();
        if (!(server.overworld().getChunkSource().getGenerator().getBiomeSource()
                instanceof SkyOceanBiomeSource)) {
            throw new IllegalStateException("RealScale preset not active");
        }
        var manager = server.getStructureManager();
        var ids = manager.listTemplates().filter(id -> id.getNamespace().equals("realscale"))
                .filter(id -> id.getPath().startsWith("architecture/") || id.getPath().startsWith("urban/"))
                .distinct().toList();
        long architecture = ids.stream().filter(id -> id.getPath().startsWith("architecture/")).count();
        long urban = ids.size() - architecture;
        if (architecture != 100 || urban != 4)
            throw new IllegalStateException("Unexpected template counts " + architecture + "/" + urban);
        for (ResourceLocation id : ids) {
            var template = manager.get(id).orElseThrow(() -> new IllegalStateException("Missing template " + id));
            var size = template.getSize();
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
                throw new IllegalStateException("Empty template " + id);
        }
        System.out.println("FORGE_SERVER_TEMPLATE_SMOKE_OK architecture=" + architecture + " urban=" + urban);
    }
}
