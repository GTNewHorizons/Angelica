package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import thaumcraft.client.renderers.tile.TileEldritchObeliskRenderer;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Z;

@Mixin(value = TileEldritchObeliskRenderer.class, remap = false)
public abstract class MixinTileEldritchObeliskRenderer {
    @Unique
    private static final float angelica$NEAR = 0.01F;
    @Unique
    private static final float angelica$FAR = 0.99F;

    @Shadow
    private boolean inrange;

    @Unique
    private double angelica$maxRenderDistanceSq;

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "render", remap = true, require = 1)
    private void angelica$beginRender(TileEntity te, double x, double y, double z, float partialTicks) {
        this.angelica$maxRenderDistanceSq = te.getMaxRenderDistanceSquared();
    }

    @Surround.Finally("render")
    private void angelica$endRender() {
        PortalRenderer.endTileEntity(false);
    }

    @ModifyExpressionValue(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V",
        at = @At(value = "CONSTANT", args = "doubleValue=512.0"), remap = true, require = 1)
    private double angelica$layeredPortalDistance(double original) {
        return this.angelica$maxRenderDistanceSq;
    }

    @Surround(method = "drawPlaneZNeg(DDDFI)V", id = "zNeg", require = 1)
    private void angelica$zNeg(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zNeg")
    private void angelica$zNegFace(double x, double y, double z, float f, int height) {
        final double plane = z + angelica$NEAR;
        PortalRenderer.addFace(AXIS_Z, false, plane, x, y, plane, x, y + height, plane, x + 1.0, y, plane);
    }

    @Surround(method = "drawPlaneZPos(DDDFI)V", id = "zPos", require = 1)
    private void angelica$zPos(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zPos")
    private void angelica$zPosFace(double x, double y, double z, float f, int height) {
        final double plane = z + angelica$FAR;
        PortalRenderer.addFace(AXIS_Z, true, plane, x, y + height, plane, x, y, plane, x + 1.0, y + height, plane);
    }

    @Surround(method = "drawPlaneXNeg(DDDFI)V", id = "xNeg", require = 1)
    private void angelica$xNeg(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("xNeg")
    private void angelica$xNegFace(double x, double y, double z, float f, int height) {
        final double plane = x + angelica$NEAR;
        PortalRenderer.addFace(AXIS_X, false, plane, plane, y, z, plane, y, z + 1.0, plane, y + height, z);
    }

    @Surround(method = "drawPlaneXPos(DDDFI)V", id = "xPos", require = 1)
    private void angelica$xPos(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    // TC draws this face last and the obelisk sides right after, which must land on top of the portal.
    @Surround.Skipped("xPos")
    private void angelica$xPosFace(double x, double y, double z, float f, int height) {
        final double plane = x + angelica$FAR;
        PortalRenderer.addFace(AXIS_X, true, plane, plane, y + height, z, plane, y + height, z + 1.0, plane, y, z);
        PortalRenderer.drawBeforeOverlay();
    }
}
