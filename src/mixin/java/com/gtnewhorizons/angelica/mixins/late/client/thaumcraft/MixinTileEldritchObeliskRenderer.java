package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.ObeliskPortal;
import com.gtnewhorizons.angelica.compat.thaumcraft.ObeliskPortal.Face;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import thaumcraft.client.renderers.tile.TileEldritchObeliskRenderer;

@Mixin(value = TileEldritchObeliskRenderer.class, remap = false)
public abstract class MixinTileEldritchObeliskRenderer {
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
        ObeliskPortal.close();
    }

    @ModifyExpressionValue(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V",
        at = @At(value = "CONSTANT", args = "doubleValue=512.0"), remap = true, require = 1)
    private double angelica$layeredPortalDistance(double original) {
        return this.angelica$maxRenderDistanceSq;
    }

    @Surround(method = "drawPlaneZNeg(DDDFI)V", id = "north", require = 1)
    private void angelica$captureNorth(double x, double y, double z, float partialTicks, int height) {
        if (this.inrange) ObeliskPortal.beginFace(Face.NORTH, x, y, z, height);
    }

    @Surround(method = "drawPlaneZPos(DDDFI)V", id = "south", require = 1)
    private void angelica$captureSouth(double x, double y, double z, float partialTicks, int height) {
        if (this.inrange) ObeliskPortal.beginFace(Face.SOUTH, x, y, z, height);
    }

    @Surround(method = "drawPlaneXNeg(DDDFI)V", id = "west", require = 1)
    private void angelica$captureWest(double x, double y, double z, float partialTicks, int height) {
        if (this.inrange) ObeliskPortal.beginFace(Face.WEST, x, y, z, height);
    }

    @Surround(method = "drawPlaneXPos(DDDFI)V", id = "east", require = 1)
    private void angelica$captureEast(double x, double y, double z, float partialTicks, int height) {
        if (this.inrange) ObeliskPortal.beginFace(Face.EAST, x, y, z, height);
    }

    @Surround.Return("east")
    private void angelica$compositeFaces() {
        ObeliskPortal.finish();
    }
}
