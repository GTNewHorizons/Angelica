package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import thaumcraft.client.renderers.tile.TileEldritchLockRenderer;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Z;

@Mixin(value = TileEldritchLockRenderer.class, remap = false)
public abstract class MixinTileEldritchLockRenderer {
    @Unique
    private static final float angelica$OFFSET = 0.5F;

    @Shadow
    private boolean inrange;

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "render", remap = true, require = 1)
    private void angelica$beginRender(TileEntity te, double x, double y, double z, float partialTicks) {
    }

    @Surround.Finally("render")
    private void angelica$endRender() {
        PortalRenderer.endTileEntity(true);
    }

    @Surround(method = "drawPlaneZPos(DDDFI)V", id = "zPos", require = 1)
    private void angelica$zPos(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zPos")
    private void angelica$zPosFace(double x, double y, double z, float f, int height) {
        final double plane = z + angelica$OFFSET;
        PortalRenderer.addFace(AXIS_Z, true, plane, x - 2.0, y + 3.0, plane, x - 2.0, y - 2.0, plane, x + 3.0, y + 3.0, plane);
    }

    @Surround(method = "drawPlaneZNeg(DDDFI)V", id = "zNeg", require = 1)
    private void angelica$zNeg(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("zNeg")
    private void angelica$zNegFace(double x, double y, double z, float f, int height) {
        final double plane = z + angelica$OFFSET;
        PortalRenderer.addFace(AXIS_Z, false, plane, x - 2.0, y - 2.0, plane, x - 2.0, y + 3.0, plane, x + 3.0, y - 2.0, plane);
    }

    @Surround(method = "drawPlaneXPos(DDDFI)V", id = "xPos", require = 1)
    private void angelica$xPos(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("xPos")
    private void angelica$xPosFace(double x, double y, double z, float f, int height) {
        final double plane = x + angelica$OFFSET;
        PortalRenderer.addFace(AXIS_X, true, plane, plane, y + 3.0, z - 2.0, plane, y + 3.0, z + 3.0, plane, y - 2.0, z - 2.0);
    }

    @Surround(method = "drawPlaneXNeg(DDDFI)V", id = "xNeg", require = 1)
    private void angelica$xNeg(double x, double y, double z, float f, int height) {
        @Surround.Skip final boolean skip = this.inrange && PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("xNeg")
    private void angelica$xNegFace(double x, double y, double z, float f, int height) {
        final double plane = x + angelica$OFFSET;
        PortalRenderer.addFace(AXIS_X, false, plane, plane, y - 2.0, z - 2.0, plane, y - 2.0, z + 3.0, plane, y + 3.0, z - 2.0);
    }
}
