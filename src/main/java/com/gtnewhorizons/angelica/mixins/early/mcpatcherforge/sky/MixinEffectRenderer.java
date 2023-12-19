package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.sky;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.prupe.mcpatcher.sky.FireworksHelper;

@SuppressWarnings({ "rawtypes" })
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {

    @Shadow
    private List[] fxLayers;

    @Unique
    private int mcpatcher_forge$renderParticlesIndex;

    @Inject(
        method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/client/renderer/texture/TextureManager;)V",
        at = @At("RETURN"))
    private void modifyConstructor(World world, TextureManager manager, CallbackInfo ci) {
        this.fxLayers = new List[5];
        for (int i = 0; i < this.fxLayers.length; ++i) {
            this.fxLayers[i] = new ArrayList();
        }
    }

    @Redirect(
        method = "addEffect(Lnet/minecraft/client/particle/EntityFX;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EntityFX;getFXLayer()I"))
    private int modifyAddEffect(EntityFX instance) {
        return FireworksHelper.getFXLayer(instance);
    }

    @ModifyConstant(
        method = { "updateEffects()V", "clearEffects(Lnet/minecraft/world/World;)V" },
        constant = @Constant(intValue = 4))
    private int modifyListSize(int constant) {
        return 5;
    }

    @ModifyConstant(method = "renderParticles(Lnet/minecraft/entity/Entity;F)V", constant = @Constant(intValue = 3))
    private int modifyRenderParticles1(int constant) {
        return 5;
    }

    @Inject(
        method = "renderParticles(Lnet/minecraft/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void modifyRenderParticles2(Entity player, float partialTickTime, CallbackInfo ci, float f1, float f2,
        float f3, float f4, float f5, int k, int i) {
        this.mcpatcher_forge$renderParticlesIndex = i;
    }

    @Redirect(
        method = "renderParticles(Lnet/minecraft/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean modifyRenderParticles3(List layer) {
        return FireworksHelper.skipThisLayer(
            this.fxLayers[mcpatcher_forge$renderParticlesIndex].isEmpty(),
            this.mcpatcher_forge$renderParticlesIndex);
    }

    @Redirect(
        method = "renderParticles(Lnet/minecraft/entity/Entity;F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false))
    private void modifyRenderParticles4(int sfactor, int dfactor) {
        FireworksHelper.setParticleBlendMethod(this.mcpatcher_forge$renderParticlesIndex, 0, true);
    }
}
