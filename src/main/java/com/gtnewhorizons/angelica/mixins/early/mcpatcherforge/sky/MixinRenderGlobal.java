package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.sky;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.prupe.mcpatcher.sky.SkyRenderer;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Shadow
    public WorldClient theWorld;

    @Inject(method = "renderSky(F)V", at = @At("HEAD"))
    private void modifyRenderSky1(float partialTick, CallbackInfo ci) {
        SkyRenderer.setup(this.theWorld, partialTick, this.theWorld.getCelestialAngle(partialTick));
    }

    @Inject(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", remap = false, ordinal = 9))
    private void modifyRenderSky3(float partialTick, CallbackInfo ci) {
        SkyRenderer.renderAll();
    }

    // Ordinal 0 shouldn't be redirected unfortunately
    @ModifyArg(
        method = "renderSky(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 1))
    private ResourceLocation modifyRenderSky4(ResourceLocation location) {
        return SkyRenderer.setupCelestialObject(location);
    }

    @ModifyArg(
        method = "renderSky(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
            ordinal = 2))
    private ResourceLocation modifyRenderSky5(ResourceLocation location) {
        return SkyRenderer.setupCelestialObject(location);
    }

    @WrapWithCondition(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false, ordinal = 1))
    private boolean modifyRenderSky6(float f1, float f2, float f3, float f4) {
        return !SkyRenderer.active;
    }

    @WrapWithCondition(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false, ordinal = 1))
    private boolean modifyRenderSky7(int i) {
        return !SkyRenderer.active;
    }

    @ModifyArg(
        method = "renderSky(F)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", remap = false, ordinal = 2),
        index = 1)
    private float modifyRenderSky8(float input) {
        // -((d0 - 16.0D)) turned into -((d0 - SkyRenderer.horizonHeight))
        return (float) (input - 16f + SkyRenderer.horizonHeight);
    }
}
