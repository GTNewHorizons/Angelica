package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import com.gtnewhorizons.angelica.compat.thaumcraft.PortalFaces;
import com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer;
import com.gtnewhorizons.angelica.rendering.tesr.SectionBuiltBlockEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import thaumcraft.common.tiles.TileEldritchNothing;

@Mixin(value = TileEldritchNothing.class, remap = false)
public abstract class MixinTileEldritchNothing implements SectionBuiltBlockEntity {
    @Unique
    private int angelica$faces = PortalFaces.NOT_BUILT;

    @Override
    public int angelica$prepareFromSection(IBlockAccess world) {
        final TileEntity te = (TileEntity) (Object) this;
        return PortalFaces.nothingMask(world, te.xCoord, te.yCoord, te.zCoord);
    }

    @Override
    public void angelica$applySectionResult(int prepared) {
        angelica$faces = prepared;
    }

    @Override
    public boolean angelica$drawBuilt(double x, double y, double z) {
        final int faces = angelica$faces;
        if (faces == PortalFaces.NOT_BUILT || !PortalRenderer.replacesLayers()) return false;
        PortalRenderer.drawBuilt((TileEntity) (Object) this, false, faces, x, y, z);
        return true;
    }
}
