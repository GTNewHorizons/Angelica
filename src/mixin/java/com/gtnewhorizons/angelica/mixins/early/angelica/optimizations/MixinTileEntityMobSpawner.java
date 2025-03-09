package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityMobSpawner.class)
public class MixinTileEntityMobSpawner extends TileEntity {

    @Override
    public double getMaxRenderDistanceSquared() {
        final double d = AngelicaConfig.mobSpawnerRenderDistance;
        return d * d;
    }

}
