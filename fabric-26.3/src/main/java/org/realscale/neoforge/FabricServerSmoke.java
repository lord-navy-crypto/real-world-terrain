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

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(FabricServerSmoke::verify);
    }

    public static void verify(net.minecraft.server.MinecraftServer server) {
        if (!"server".equals(System.getenv("REALSCALE_FABRIC_CI_SMOKE"))) return;
        if (!(server.overworld().getChunkSource().getGenerator().getBiomeSource()
                instanceof SkyOceanBiomeSource)) {
            throw new IllegalStateException("RealScale preset not active");
        }

        var resourceManager = server.getResourceManager();
        var packagedResources = resourceManager.listResources(
                "structure", id -> id.getNamespace().equals("realscale"));
        var resourceIdsByTemplate = new java.util.HashMap<ResourceLocation, ResourceLocation>();
        for (var resourceId : packagedResources.keySet()) {
            String path = resourceId.getPath();
            if (path.startsWith("structure/")) path = path.substring("structure/".length());
            if (!path.endsWith(".nbt")) continue;
            String templatePath = path.substring(0, path.length() - ".nbt".length());
            if (!templatePath.startsWith("architecture/") && !templatePath.startsWith("urban/")) continue;
            var templateId = ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), templatePath);
            resourceIdsByTemplate.put(templateId, resourceId);
        }

        var ids = resourceIdsByTemplate.keySet().stream()
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .toList();
        long architecture = ids.stream().filter(id -> id.getPath().startsWith("architecture/")).count();
        long urban = ids.stream().filter(id -> id.getPath().startsWith("urban/")).count();
        if (architecture != 100 || urban != 4) {
            throw new IllegalStateException("Unexpected packaged template counts " + architecture + "/" + urban
                    + "; matching realscale resources=" + packagedResources.size()
                    + "; sample paths=" + packagedResources.keySet().stream().limit(8).toList());
        }

        var manager = server.getStructureManager();
        // Rebind against the final server resource manager before querying templates.
        // This is safe here because this verifier is enabled only in the isolated CI smoke run.
        manager.onResourceManagerReload(resourceManager);
        var managerIds = manager.listTemplates()
                .filter(id -> id.getNamespace().equals("realscale"))
                .toList();
        var managerIdsByPath = new java.util.HashMap<String, ResourceLocation>();
        for (var managerId : managerIds) {
            String path = managerId.getPath();
            if (path.startsWith("structure/")) path = path.substring("structure/".length());
            if (path.endsWith(".nbt")) path = path.substring(0, path.length() - ".nbt".length());
            managerIdsByPath.put(path, managerId);
        }
        System.out.println("FABRIC_TEMPLATE_MANAGER_DIAGNOSTIC count=" + managerIds.size()
                + " sample=" + managerIds.stream().limit(8).toList());

        var unsupportedBlocks = new java.util.ArrayList<String>();
        for (ResourceLocation id : ids) {
            var resourceId = resourceIdsByTemplate.get(id);
            var resource = packagedResources.get(resourceId);
            if (resource == null)
                throw new IllegalStateException("Missing packaged NBT resource " + resourceId);
            try (var stream = resource.open()) {
                var nbt = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
                if (nbt.getInt("DataVersion") != 3955)
                    throw new IllegalStateException("Wrong DataVersion in " + id);
                var palette = nbt.getList("palette", Tag.TAG_COMPOUND);
                if (palette.isEmpty())
                    throw new IllegalStateException("Empty palette in " + id);
                for (int i = 0; i < palette.size(); i++) {
                    var rawBlockId = palette.getCompound(i).getString("Name");
                    var blockId = ResourceLocation.tryParse(rawBlockId);
                    if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId))
                        unsupportedBlocks.add(rawBlockId + " in " + id);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + id, e);
            }
        }
        if (!unsupportedBlocks.isEmpty())
            throw new IllegalStateException("Unsupported palette blocks (" + unsupportedBlocks.size()
                    + "): " + unsupportedBlocks.stream().limit(80).toList());

        for (ResourceLocation id : ids) {
            var resourceId = resourceIdsByTemplate.get(id);
            var managerId = managerIdsByPath.get(id.getPath());
            if (managerId == null)
                throw new IllegalStateException("StructureTemplateManager did not enumerate " + id
                        + "; manager templates=" + managerIds.size()
                        + "; sample=" + managerIds.stream().limit(12).toList());
            var template = manager.get(managerId)
                    .orElseThrow(() -> new IllegalStateException("StructureTemplateManager could not load "
                            + managerId + "; resource=" + resourceId));
            var size = template.getSize();
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
                throw new IllegalStateException("Empty template " + id);
        }
        System.out.println("FABRIC_SERVER_TEMPLATE_SMOKE_OK architecture=" + architecture + " urban=" + urban);
        server.halt(false); // Clean shutdown after successful opt-in CI validation.
    }
}
