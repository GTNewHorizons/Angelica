package com.gtnewhorizons.angelica.mixins.early.angelica.lighting;

import com.gtnewhorizons.angelica.rendering.tesr.TesrLightCache;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher_LightCache {

    @Redirect(
        method = "renderTileEntity(Lnet/minecraft/tileentity/TileEntity;F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getLightBrightnessForSkyBlocks(IIII)I"))
    private int angelica$cacheTesrLightmap(World world, int x, int y, int z, int minBlockLight) {
        return TesrLightCache.getLightBrightnessForSkyBlocks(world, x, y, z, minBlockLight);
    }

    @Inject(method = "func_147543_a(Lnet/minecraft/world/World;)V", at = @At("HEAD"))
    private void angelica$dropTesrLightmapCache(World world, CallbackInfo ci) {
        TesrLightCache.clear();
    }
}
