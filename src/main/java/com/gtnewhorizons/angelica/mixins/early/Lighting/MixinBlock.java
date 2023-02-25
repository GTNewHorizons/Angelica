package com.gtnewhorizons.angelica.mixins.early.Lighting;

import net.minecraft.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(Block.class)
public class MixinBlock {

    @ModifyConstant(
            method = "Lnet/minecraft/block/Block;getAmbientOcclusionLightValue()F",
            constant = @Constant(floatValue = 0.2f))
    public float angelica$blockAoLight(float constant) {
        return Shaders.blockAoLight;
    }

}
