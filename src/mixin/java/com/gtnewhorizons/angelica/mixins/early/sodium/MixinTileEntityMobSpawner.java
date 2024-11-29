package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityMobSpawner.class)
public class MixinTileEntityMobSpawner extends TileEntity {

    @Override
    public double getMaxRenderDistanceSquared() {
        return 256d; // 16 blocks
    }

}
