package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalFaces;
import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.client.renderers.tile.TileHoleRenderer;

/**
 * TC's renderTileEntityAt only draws planes on the faces that border another opaque block, so it is replaced.
 */
@Mixin(value = TileHoleRenderer.class, remap = false)
public abstract class MixinTileHoleRenderer {

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "render", remap = true, require = 1)
    private void angelica$beginRender(TileEntity te, double x, double y, double z, float partialTicks) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("render")
    private void angelica$render(TileEntity te, double x, double y, double z, float partialTicks) {
        final int mask = PortalFaces.holeMask(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
        PortalFaces.addHoleFaces(mask, PortalRenderer.inLayeredRange(te), x, y, z);
        PortalRenderer.endSkippedRender();
    }

    @Surround.Finally("render")
    private void angelica$endRender() {
        PortalRenderer.endTileEntity(true);
    }
}
