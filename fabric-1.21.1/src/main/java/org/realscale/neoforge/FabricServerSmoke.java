package org.realscale.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import java.io.IOException;

/** CI-only integration checks; inert during normal gameplay. */
public final class FabricServerSmoke {
    private FabricServerSmoke() {}

    public static void register() { net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(FabricServerSmoke::verify); }
    public static void verify(net.minecraft.server.MinecraftServer server) {
        if (!"server".equals(System.getenv("REALSCALE_FABRIC_CI_SMOKE"))) return;
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
            var resourceId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
            var resource = server.getResourceManager().getResource(resourceId)
                    .orElseThrow(() -> new IllegalStateException("Missing packaged NBT " + resourceId));
            try (var stream = resource.open()) {
                var nbt = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                if (nbt.getInt("DataVersion") != 3955)
                    throw new IllegalStateException("Wrong DataVersion in " + id);
                var palette = nbt.getList("palette", Tag.TAG_COMPOUND);
                if (palette.isEmpty())
                    throw new IllegalStateException("Empty palette in " + id);
                for (int i = 0; i < palette.size(); i++) {
                    var blockId = ResourceLocation.tryParse(palette.getCompound(i).getString("Name"));
                    if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId))
                        throw new IllegalStateException("Unsupported palette block " + blockId + " in " + id);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + id, e);
            }
            var template = manager.get(id).orElseThrow(() -> new IllegalStateException("Missing template " + id));
            var size = template.getSize();
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
                throw new IllegalStateException("Empty template " + id);
        }
        System.out.println("FABRIC_SERVER_TEMPLATE_SMOKE_OK architecture=" + architecture + " urban=" + urban);
        server.halt(false); // Clean shutdown after successful opt-in CI validation.
    }
}
