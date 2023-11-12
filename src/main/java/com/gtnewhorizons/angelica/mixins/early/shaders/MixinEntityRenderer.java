package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import java.nio.FloatBuffer;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Unique private static Runnable fogToggleListener;
    @Unique private static Runnable fogModeListener;
    @Unique private static Runnable fogStartListener;
    @Unique private static Runnable fogEndListener;
    @Unique private static Runnable fogDensityListener;


    private void sglFogf(int pname, float param) {
        GL11.glFogf(pname, param);
        switch (pname) {
            case GL11.GL_FOG_DENSITY:
                if (fogDensityListener != null) {
                    fogDensityListener.run();
                }
                break;
            case GL11.GL_FOG_START:
                if (fogStartListener != null) {
                    fogStartListener.run();
                }
                break;
            case GL11.GL_FOG_END:
                if (fogEndListener != null) {
                    fogEndListener.run();
                }
                break;
        }
    }

    private void sglFogi(int pname, int param) {
        GL11.glFogi(pname, param);
        if (pname == GL11.GL_FOG_MODE) {
            if (fogModeListener != null) {
                fogModeListener.run();
            }
        }
    }


    @Inject(at = @At(shift = At.Shift.AFTER, target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V", value = "INVOKE"), method = "renderWorld(FJ)V")
    private void iris$setCamera(float tickDelta, long startTime, CallbackInfo ci) {
        // TODO: Grab from Angelica
        CapturedRenderingState.INSTANCE.setCamera(tickDelta);
    }


    @Inject(at = @At("HEAD"), method = "renderWorld(FJ)V")
    private void iris$beginRender(float tickDelta, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        CapturedRenderingState.INSTANCE.setTickDelta(tickDelta);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(startTime);

        Program.unbind();

        pipeline.set(Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension()));

        pipeline.get().beginLevelRendering();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void iris$endLevelRender(float tickDelta, long limitTime, CallbackInfo callback, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // TODO: Iris
//        HandRenderer.INSTANCE.renderTranslucent(poseStack, tickDelta, camera, gameRenderer, pipeline);
        Minecraft.getMinecraft().mcProfiler.endStartSection("iris_final");
        pipeline.get().finalizeLevelRendering();
        pipeline.set(null);
        Program.unbind();
    }

    // setFogColorBuffer
    @Inject(at = @At("HEAD"), method = "setFogColorBuffer(FFFF)Ljava/nio/FloatBuffer;")
    private void iris$setFogColor(float red, float green, float blue, float alpha, CallbackInfoReturnable<FloatBuffer> cir) {
        CapturedRenderingState.INSTANCE.setFogColor(red, green, blue);
    }

    // updateFogColor

    @Redirect(at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glClearColor(FFFF)V", value = "INVOKE"), method = "updateFogColor(F)V")
    private void iris$setClearColor(float red, float green, float blue, float alpha) {
        CapturedRenderingState.INSTANCE.setClearColor(red, green, blue, alpha);
    }




    // setupFog

    @Redirect(at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V", value = "INVOKE"), method = "setupFog(IF)V")
    private void iris$sglFogi(int pname, int param) {
        sglFogi(pname, param);
    }

    @Redirect(at = @At(remap = false, target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V", value = "INVOKE"), method = "setupFog(IF)V")
    private void iris$sglFogf(int pname, float param) {
        sglFogf(pname, param);
    }




    static {
        StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
        StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
        StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
        StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
    }

}