package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.world;

import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProviderHell;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.prupe.mcpatcher.cc.ColorizeWorld;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mixin(WorldProviderHell.class)
public abstract class MixinWorldProviderHell {

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason customized value
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public Vec3 getFogColor(float celestialAngle, float renderPartialTicks) {
        return Vec3.createVectorHelper(
            ColorizeWorld.netherFogColor[0],
            ColorizeWorld.netherFogColor[1],
            ColorizeWorld.netherFogColor[2]);
    }
}
