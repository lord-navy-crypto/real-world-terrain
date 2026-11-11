package org.realscale.neoforge.lotm.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "realscale", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LotmKeyMappings {
    private static final String CATEGORY = "key.categories.realscale.lotm";
    public static final KeyMapping PRIMARY = new KeyMapping(
            "key.realscale.lotm.primary", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping SECONDARY = new KeyMapping(
            "key.realscale.lotm.secondary", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private LotmKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PRIMARY);
        event.register(SECONDARY);
    }
}
