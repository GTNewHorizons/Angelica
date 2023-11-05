package com.gtnewhorizons.angelica.mixins.early.archaic.client.lighting;

import org.embeddedt.archaicfix.lighting.world.lighting.LightingEngine;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

@Mixin(World.class)
public abstract class MixinWorld {

    @SuppressWarnings("unused")
    private LightingEngine lightingEngine;

    @Inject(method = "finishSetup", at = @At("RETURN"), remap = false)
    private void onConstructed(CallbackInfo ci) {
        this.lightingEngine = new LightingEngine((World) (Object) this);
    }

    @Redirect(method = { "getSkyBlockTypeBrightness", "getSavedLightValue" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I"))
    private int useBlockIntrinsicBrightness(Chunk instance, EnumSkyBlock type, int x, int y, int z) {
        if(type == EnumSkyBlock.Block)
            return LightingHooks.getIntrinsicOrSavedBlockLightValue(instance, x, y, z);
        else
            return instance.getSavedLightValue(type, x, y, z);
    }
}
