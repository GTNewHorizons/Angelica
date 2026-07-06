package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.world;

import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProviderEnd;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.prupe.mcpatcher.cc.ColorizeWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(WorldProviderEnd.class)
public abstract class MixinWorldProviderEnd {

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason customized value
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public Vec3 getFogColor(float celestialAngle, float renderPartialTicks) {
        return Vec3.createVectorHelper(
            ColorizeWorld.endFogColor[0],
            ColorizeWorld.endFogColor[1],
            ColorizeWorld.endFogColor[2]);
    }
}
