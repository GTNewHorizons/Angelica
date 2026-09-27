package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.rendering.celeritas.SkyColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class MixinWorld {
    @Unique private final SkyColorCache angelica$skyColorCache = new SkyColorCache();

    @WrapOperation(
        method = "getSkyColorBody",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;getSkyBlendColour(Lnet/minecraft/world/World;III)I",
            remap = false),
        remap = false)
    private int angelica$blendSkyColor(World world, int x, int y, int z, Operation<Integer> original, @Local(argsOnly = true) Entity camera, @Local(argsOnly = true) float tickDelta) {
        return angelica$skyColorCache.getColor(world,
            camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * tickDelta,
            camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * tickDelta,
            camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * tickDelta,
            SmoothBiomeColorCache.configuredRadius());
    }
}
