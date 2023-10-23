package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Unique
    private WorldRenderingPipeline pipeline;

    @Inject(at = @At("HEAD"), method = "renderWorld(FJ)V")
    private void iris$beginRender(float tickDelta, long startTime, CallbackInfo ci) {
        // TODO: Iris
//        CapturedRenderingState.INSTANCE.setGbufferModelView(poseStack.last().pose());
//        CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
        CapturedRenderingState.INSTANCE.setTickDelta(tickDelta);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(startTime);

        Program.unbind();

        pipeline = Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());

        pipeline.beginLevelRendering();
    }
    @Inject(method = "renderWorld(FJ)V", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void iris$endLevelRender(float tickDelta, long limitTime, CallbackInfo callback) {
        // TODO: Iris
//        HandRenderer.INSTANCE.renderTranslucent(poseStack, tickDelta, camera, gameRenderer, pipeline);
        Minecraft.getMinecraft().mcProfiler.endStartSection("iris_final");
        pipeline.finalizeLevelRendering();
        pipeline = null;
        Program.unbind();
    }

}
