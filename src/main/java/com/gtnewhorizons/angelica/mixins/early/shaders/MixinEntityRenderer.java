package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
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

}
