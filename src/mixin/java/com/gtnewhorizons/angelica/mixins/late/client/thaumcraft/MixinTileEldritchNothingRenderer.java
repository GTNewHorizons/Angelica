package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalFaces;
import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.client.renderers.tile.TileEldritchNothingRenderer;

/**
 * Replaced whole for the same reasons as {@link MixinTileHoleRenderer}.
 */
@Mixin(value = TileEldritchNothingRenderer.class, remap = false)
public abstract class MixinTileEldritchNothingRenderer {

    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V", id = "render", remap = true, require = 1)
    private void angelica$beginRender(TileEntity te, double x, double y, double z, float partialTicks) {
        @Surround.Skip final boolean skip = PortalRenderer.replacesLayers();
    }

    @Surround.Skipped("render")
    private void angelica$render(TileEntity te, double x, double y, double z, float partialTicks) {
        final int mask = PortalFaces.nothingMask(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
        PortalFaces.addNothingFaces(mask, PortalRenderer.inLayeredRange(te), x, y, z);
        PortalRenderer.endSkippedRender();
    }

    @Surround.Finally("render")
    private void angelica$endRender() {
        PortalRenderer.endTileEntity(true);
    }
}
