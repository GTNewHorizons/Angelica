package com.gtnewhorizons.angelica.mixins.early.archaic.client.lighting;

import net.minecraft.world.ChunkCache;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkCache.class)
public class MixinChunkCache {
    @Redirect(method = "getSpecialBlockBrightness", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I"))
    private int getIntrinsicValue(Chunk instance, EnumSkyBlock p_76614_1_, int p_76614_2_, int p_76614_3_, int p_76614_4_) {
        return p_76614_1_ == EnumSkyBlock.Sky ?
                instance.getSavedLightValue(p_76614_1_, p_76614_2_, p_76614_3_, p_76614_4_) :
                LightingHooks.getIntrinsicOrSavedBlockLightValue(instance, p_76614_2_, p_76614_3_, p_76614_4_);
    }
}
