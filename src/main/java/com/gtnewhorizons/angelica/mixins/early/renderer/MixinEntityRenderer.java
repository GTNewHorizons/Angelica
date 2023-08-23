package com.gtnewhorizons.angelica.mixins.early.renderer;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.gtnewhorizon.mixinextras.injector.ModifyExpressionValue;
import com.gtnewhorizons.angelica.client.Shaders;
import com.gtnewhorizons.angelica.client.ShadersRender;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @Shadow
    private Minecraft mc;

    @Shadow
    public abstract void disableLightmap(double p_78483_1_);

    // renderHand

    @Inject(
            method = "renderHand(FI)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false))
    private void angelica$applyHandDepth(CallbackInfo ci) {
        Shaders.applyHandDepth();
    }

    @Inject(
            method = "renderHand(FI)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I",
                    ordinal = 1),
            cancellable = true)
    private void angelica$checkCompositeRendered(float p_78476_1_, int p_78476_2_, CallbackInfo ci) {
        if (!Shaders.isCompositeRendered) {
            ci.cancel();
        }
        this.disableLightmap(p_78476_1_);
    }

    @Redirect(
            method = "renderHand(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    private void angelica$renderItemInFirstPerson(ItemRenderer itemRenderer, float partialTicks) {
        ShadersRender.renderItemFP(itemRenderer, partialTicks);
    }

    // disableLightmap

    @Inject(at = @At("RETURN"), method = "disableLightmap(D)V")
    private void angelica$disableLightmap(CallbackInfo ci) {
        Shaders.disableLightmap();
    }

    // enableLightmap

    @Inject(at = @At("RETURN"), method = "enableLightmap(D)V")
    private void angelica$enableLightmap(CallbackInfo ci) {
        Shaders.enableLightmap();
    }

    // renderWorld

    @Inject(at = @At("HEAD"), method = "renderWorld(FJ)V")
    private void angelica$beginRender(float p_78471_1_, long p_78471_2_, CallbackInfo ci) {
        Shaders.beginRender(this.mc, p_78471_1_, p_78471_2_);
    }

    @Redirect(
            at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V", value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$setViewport(int x, int y, int width, int height) {
        Shaders.setViewport(x, y, width, height);
    }

    @Inject(
            at = @At(
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glClear(I)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V",
            slice = @Slice(
                    from = @At(
                            args = "intValue=" + (GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT),
                            value = "CONSTANT"),
                    to = @At(args = "intValue=" + GL11.GL_CULL_FACE, ordinal = 1, value = "CONSTANT")))
    private void angelica$clearRenderBuffer(CallbackInfo ci) {
        Shaders.clearRenderBuffer();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$setCamera(float p_78471_1_, long p_78471_2_, CallbackInfo ci) {
        Shaders.setCamera(p_78471_1_);
    }

    @Redirect(
            at = @At(
                    opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I",
                    value = "FIELD"),
            method = "renderWorld(FJ)V")
    private int angelica$isShadowPass(GameSettings gameSettings) {
        // A better way would be replacing the if-expression completely but I don't know how. If you figure it how,
        // please let me (glowredman) know. Unless that happens, we just redirect the field access and return either 3
        // or 4 to achieve the same.
        return Shaders.isShadowPass ? 3 : 4;
    }

    @Inject(
            at = @At(target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(F)V", value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginSky(CallbackInfo ci) {
        Shaders.beginSky();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$endSky(CallbackInfo ci) {
        Shaders.endSky();
    }

    @Redirect(
            at = @At(target = "Lnet/minecraft/client/renderer/culling/Frustrum;setPosition(DDD)V", value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$setFrustrumPosition(Frustrum frustrum, double x, double y, double z) {
        ShadersRender.setFrustrumPosition(frustrum, x, y, z);
    }

    @Redirect(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;clipRenderersByFrustum(Lnet/minecraft/client/renderer/culling/ICamera;F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$clipRenderersByFrustrum(RenderGlobal renderGlobal, ICamera p_72729_1_, float p_72729_2_) {
        ShadersRender.clipRenderersByFrustrum(renderGlobal, (Frustrum) p_72729_1_, p_72729_2_);
    }

    @Inject(
            at = @At(
                    ordinal = 7,
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginUpdateChunks(CallbackInfo ci) {
        Shaders.beginUpdateChunks();
    }

    @Inject(
            at = @At(
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1,
                    target = "Lnet/minecraft/entity/EntityLivingBase;posY:D",
                    value = "FIELD"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION,
            method = "renderWorld(FJ)V")
    private void angelica$endUpdateChunks(float p_78471_1_, long p_78471_2_, CallbackInfo ci,
            EntityLivingBase entitylivingbase, RenderGlobal renderglobal, EffectRenderer effectrenderer, double d0,
            double d1, double d2, int j) {
        // A better solution would be to inject this directly at the end of the "if (j == 0)" block so checking j == 0
        // again here isn't necessary. If you figure out how to do that, please let me (glowredman) know.
        if (j == 0) {
            Shaders.endUpdateChunks();
        }
    }

    @Inject(
            at = @At(
                    ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginTerrain(CallbackInfo ci) {
        Shaders.beginTerrain();
    }

    @Inject(
            at = @At(
                    ordinal = 0,
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$endTerrain(CallbackInfo ci) {
        Shaders.endTerrain();
    }

    @Inject(
            at = @At(
                    target = "Lnet/minecraft/client/particle/EffectRenderer;renderLitParticles(Lnet/minecraft/entity/Entity;F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginLitParticles(CallbackInfo ci) {
        Shaders.beginLitParticles();
    }

    @Inject(
            at = @At(
                    target = "Lnet/minecraft/client/particle/EffectRenderer;renderParticles(Lnet/minecraft/entity/Entity;F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginParticles(CallbackInfo ci) {
        Shaders.beginParticles();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/particle/EffectRenderer;renderParticles(Lnet/minecraft/entity/Entity;F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$endParticles(CallbackInfo ci) {
        Shaders.endParticles();
    }

    @Inject(
            at = @At(target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V", value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$beginWeather(CallbackInfo ci) {
        Shaders.beginWeather();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$endWeather(CallbackInfo ci) {
        Shaders.endWeather();
    }

    @Inject(
            at = @At(
                    ordinal = 1,
                    remap = false,
                    shift = At.Shift.AFTER,
                    target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V",
                    value = "INVOKE"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION,
            method = "renderWorld(FJ)V")
    private void angelica$renderHand0AndPreWater(float p_78471_1_, long p_78471_2_, CallbackInfo ci,
            EntityLivingBase entitylivingbase, RenderGlobal renderglobal, EffectRenderer effectrenderer, double d0,
            double d1, double d2, int j) {
        ShadersRender.renderHand0((EntityRenderer) (Object) this, p_78471_1_, j);
        Shaders.preWater();
    }

    @Inject(
            at = @At(
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V",
            slice = @Slice(from = @At(args = "stringValue=water", ordinal = 0, value = "CONSTANT")))
    private void angelica$beginWater(CallbackInfo ci) {
        Shaders.beginWater();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V",
            slice = @Slice(from = @At(args = "stringValue=water", ordinal = 0, value = "CONSTANT")))
    private void angelica$endWater(CallbackInfo ci) {
        Shaders.endWater();
    }

    @Inject(
            at = @At(
                    opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/entity/EntityLivingBase;posY:D",
                    value = "FIELD"),
            method = "renderWorld(FJ)V",
            slice = @Slice(
                    from = @At(args = "stringValue=entities", value = "CONSTANT"),
                    to = @At(args = "stringValue=aboveClouds", value = "CONSTANT")))
    private void angelica$disableFog(CallbackInfo ci) {
        Shaders.disableFog();
    }

    @ModifyExpressionValue(
            at = @At(
                    remap = false,
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;renderFirstPersonHand(Lnet/minecraft/client/renderer/RenderGlobal;FI)Z",
                    value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private boolean angelica$isShadowPass(boolean renderFirstPersonHand) {
        return renderFirstPersonHand || Shaders.isShadowPass;
    }

    @Inject(
            at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glClear(I)V", value = "INVOKE"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION,
            method = "renderWorld(FJ)V",
            slice = @Slice(from = @At(args = "stringValue=hand", value = "CONSTANT")))
    private void angelica$renderHand1AndRenderCompositeFinal(float p_78471_1_, long p_78471_2_, CallbackInfo ci,
            EntityLivingBase entitylivingbase, RenderGlobal renderglobal, EffectRenderer effectrenderer, double d0,
            double d1, double d2, int j) {
        ShadersRender.renderHand1((EntityRenderer) (Object) this, p_78471_1_, j);
        Shaders.renderCompositeFinal();
    }

    @Redirect(
            at = @At(target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V", value = "INVOKE"),
            method = "renderWorld(FJ)V")
    private void angelica$renderFPOverlay(EntityRenderer thizz, float p_78476_1_, int p_78476_2_) {
        ShadersRender.renderFPOverlay(thizz, p_78476_1_, p_78476_2_);
    }

    @Inject(
            at = @At(
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;mc:Lnet/minecraft/client/Minecraft;",
                    value = "FIELD"),
            method = "renderWorld(FJ)V",
            slice = @Slice(from = @At(args = "stringValue=hand", value = "CONSTANT")))
    private void angelica$endRender(CallbackInfo ci) {
        Shaders.endRender();
    }

    // renderCloudsCheck

    @Redirect(
            at = @At(target = "Lnet/minecraft/client/settings/GameSettings;shouldRenderClouds()Z", value = "INVOKE"),
            method = "renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V")
    private boolean angelica$shouldRenderClouds(GameSettings gameSettings) {
        return Shaders.shouldRenderClouds(gameSettings);
    }

    @Inject(
            at = @At(target = "Lnet/minecraft/client/renderer/RenderGlobal;renderClouds(F)V", value = "INVOKE"),
            method = "renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V")
    private void angelica$beginClouds(CallbackInfo ci) {
        Shaders.beginClouds();
    }

    @Inject(
            at = @At(
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderClouds(F)V",
                    value = "INVOKE"),
            method = "renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V")
    private void angelica$endClouds(CallbackInfo ci) {
        Shaders.endClouds();
    }

    // updateFogColor

    @Redirect(
            at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glClearColor(FFFF)V", value = "INVOKE"),
            method = "updateFogColor(F)V")
    private void angelica$setClearColor(float red, float green, float blue, float alpha) {
        Shaders.setClearColor(red, green, blue, alpha);
    }

    // setupFog

    @Redirect(
            at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V", value = "INVOKE"),
            method = "setupFog(IF)V")
    private void angelica$sglFogi(int pname, int param) {
        Shaders.sglFogi(pname, param);
    }

    // setFogColorBuffer

    @Inject(at = @At("HEAD"), method = "setFogColorBuffer(FFFF)Ljava/nio/FloatBuffer;")
    private void angelica$setFogColor(float red, float green, float blue, float alpha,
            CallbackInfoReturnable<FloatBuffer> cir) {
        Shaders.setFogColor(red, green, blue);
    }
}
