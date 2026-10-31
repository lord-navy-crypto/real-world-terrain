package org.realscale.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Read-only, live-position report using RealScale's single 1:20 latitude model. */
public final class GeographicClimateInspectorScreen extends Screen {
    private final Screen parent;
    private Report report;
    private String unavailableReason = "";

    private record Report(
            int x, int y, int z, String dimension, String biome,
            String latitudeLabel, String hemisphere, String belt,
            String boundary, String scale, String coordinateReference) { }

    public GeographicClimateInspectorScreen(Screen parent) {
        super(Component.literal("Geographic Climate Inspector"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                button -> refreshReport())
                .bounds(center - 150, y, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Announce to Chat"),
                button -> announceReport())
                .bounds(center - 58, y, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),
                button -> onClose())
                .bounds(center + 62, y, 88, 20).build());
        refreshReport();
    }

    private void refreshReport() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            report = null;
            unavailableReason = "No active player world is available.";
            return;
        }

        GeographicClimateInspectorClient.GeographicLatitudeProfile profile =
                GeographicClimateInspectorClient.readGeographicProfile(minecraft);
        if (profile == null) {
            report = null;
            unavailableReason = "The 1:20 geographic latitude report applies to the Overworld only.";
            return;
        }

        BlockPos pos = player.blockPosition();
        double latitude = profile.latitudeAt(pos.getZ());
        String biome = minecraft.level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString()).orElse("unregistered biome");
        ResourceLocation dimension = minecraft.level.dimension().location();

        report = new Report(
                pos.getX(), pos.getY(), pos.getZ(),
                dimension.toString(), biome,
                GeographicClimateMath.latitudeLabel(latitude),
                GeographicClimateMath.hemisphereLabel(latitude),
                GeographicClimateMath.beltLabel(latitude),
                GeographicClimateMath.boundaryLabel(latitude),
                profile.scaleLabel(), profile.coordinateReference());
        unavailableReason = "";
    }

    private void announceReport() {
        if (report == null) {
            refreshReport();
            if (report == null) return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        player.displayClientMessage(Component.literal(
                "[RealScale Geographic Report] " + report.latitudeLabel()
                        + " | " + report.belt()), false);
        player.displayClientMessage(Component.literal(
                "Position X/Y/Z: " + report.x() + " / " + report.y()
                        + " / " + report.z() + " | " + report.dimension()), false);
        player.displayClientMessage(Component.literal(
                "Hemisphere: " + report.hemisphere()
                        + " | Latitude model: " + report.scale()), false);
        player.displayClientMessage(Component.literal(
                "Current biome: " + report.biome()
                        + " | " + report.coordinateReference()), false);
        player.displayClientMessage(Component.literal(
                "Climate belt is a simplified latitude-based zone, not a Köppen classification or local weather forecast."),
                false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffffff);

        if (report == null) {
            graphics.drawCenteredString(font, Component.literal(unavailableReason),
                    width / 2, height / 2 - 12, 0xffffcc88);
            graphics.drawCenteredString(font,
                    Component.literal("No world-generation node is required for this geographic report."),
                    width / 2, height / 2 + 8, 0xffbbbbbb);
            return;
        }

        int y = 38;
        drawLine(graphics, "Current position (X / Y / Z)",
                report.x() + " / " + report.y() + " / " + report.z(), y);
        y += 17;
        drawLine(graphics, "Dimension", report.dimension(), y);
        y += 17;
        drawLine(graphics, "Current biome", report.biome(), y);
        y += 17;
        drawLine(graphics, "Calculated latitude", report.latitudeLabel(), y);
        y += 17;
        drawLine(graphics, "Hemisphere", report.hemisphere(), y);
        y += 17;
        drawLine(graphics, "Latitude climate belt", report.belt(), y);
        y += 17;
        drawLine(graphics, "Boundary assessment", report.boundary(), y);
        y += 17;
        drawLine(graphics, "Latitude scale", report.scale(), y);
        y += 17;
        drawLine(graphics, "Reference", report.coordinateReference(), y);

        int noteY = Math.min(height - 48, y + 18);
        graphics.drawCenteredString(font,
                Component.literal("X is the east–west map coordinate, not a true longitude."),
                width / 2, noteY, 0xffc8c8c8);
        graphics.drawCenteredString(font,
                Component.literal("Subtropical / subpolar boundaries are approximate; this is not local weather."),
                width / 2, noteY + 13, 0xffa8a8a8);
    }

    private void drawLine(GuiGraphics graphics, String label, String value, int y) {
        int left = Math.max(8, width / 2 - 180);
        int right = Math.min(width - 8, width / 2 + 180);
        graphics.drawString(font, Component.literal(label), left, y, 0xffb7c9d6);
        String clipped = font.plainSubstrByWidth(value, Math.max(40, right - left - 130));
        graphics.drawString(font, Component.literal(clipped), left + 132, y, 0xffffffff);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
