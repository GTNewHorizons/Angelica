package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import thaumcraft.client.renderers.tile.TileMirrorRenderer;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Y;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Z;

/**
 * The mirror draws its glass pane over the portal right after, so each plane is flushed on its own.
 */
@Mixin(value = TileMirrorRenderer.class, remap = false)
public abstract class MixinTileMirrorRenderer {
    @Unique
    private static final float angelica$NEAR = 0.01F;
    @Unique
    private static final float angelica$FAR = 0.99F;
    @Unique
    private static final float angelica$INSET = 0.1875F;

    @Surround(method = "drawPlaneYPos(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "yPos", require = 1)
    private void angelica$yPos(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("yPos")
    private void angelica$yPosFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = y + angelica$FAR;
        PortalRenderer.addFace(AXIS_Y, false, plane, x + angelica$INSET, plane, z + 1.0 - angelica$INSET, x + angelica$INSET, plane, z + angelica$INSET, x + 1.0 - angelica$INSET, plane, z + 1.0 - angelica$INSET);
        PortalRenderer.drawBeforeOverlay();
    }

    @Surround(method = "drawPlaneYNeg(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "yNeg", require = 1)
    private void angelica$yNeg(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("yNeg")
    private void angelica$yNegFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = y + angelica$NEAR;
        PortalRenderer.addFace(AXIS_Y, true, plane, x + angelica$INSET, plane, z + angelica$INSET, x + angelica$INSET, plane, z + 1.0 - angelica$INSET, x + 1.0 - angelica$INSET, plane, z + angelica$INSET);
        PortalRenderer.drawBeforeOverlay();
    }

    @Surround(method = "drawPlaneZNeg(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "zNeg", require = 1)
    private void angelica$zNeg(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zNeg")
    private void angelica$zNegFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = z + angelica$NEAR;
        PortalRenderer.addFace(AXIS_Z, true, plane, x + angelica$INSET, y + 1.0 - angelica$INSET, plane, x + angelica$INSET, y + angelica$INSET, plane, x + 1.0 - angelica$INSET, y + 1.0 - angelica$INSET, plane);
        PortalRenderer.drawBeforeOverlay();
    }

    @Surround(method = "drawPlaneZPos(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "zPos", require = 1)
    private void angelica$zPos(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zPos")
    private void angelica$zPosFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = z + angelica$FAR;
        PortalRenderer.addFace(AXIS_Z, false, plane, x + angelica$INSET, y + angelica$INSET, plane, x + angelica$INSET, y + 1.0 - angelica$INSET, plane, x + 1.0 - angelica$INSET, y + angelica$INSET, plane);
        PortalRenderer.drawBeforeOverlay();
    }

    @Surround(method = "drawPlaneXNeg(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "xNeg", require = 1)
    private void angelica$xNeg(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("xNeg")
    private void angelica$xNegFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = x + angelica$NEAR;
        PortalRenderer.addFace(AXIS_X, true, plane, plane, y + 1.0 - angelica$INSET, z + angelica$INSET, plane, y + 1.0 - angelica$INSET, z + 1.0 - angelica$INSET, plane, y + angelica$INSET, z + angelica$INSET);
        PortalRenderer.drawBeforeOverlay();
    }

    @Surround(method = "drawPlaneXPos(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "xPos", require = 1)
    private void angelica$xPos(TileEntity te, double x, double y, double z, float f) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("xPos")
    private void angelica$xPosFace(TileEntity te, double x, double y, double z, float f) {
        final double plane = x + angelica$FAR;
        PortalRenderer.addFace(AXIS_X, false, plane, plane, y + angelica$INSET, z + angelica$INSET, plane, y + angelica$INSET, z + 1.0 - angelica$INSET, plane, y + 1.0 - angelica$INSET, z + angelica$INSET);
        PortalRenderer.drawBeforeOverlay();
    }
}
