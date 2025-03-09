package com.gtnewhorizons.angelica.mixins.early.shaders.sodium;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import static org.joml.Math.lerp;

// Let other mixins apply, and then overwrite them
@Mixin(value = RenderGlobal.class, priority = 2000)
public class MixinRenderGlobal_iris {
    @Shadow public Minecraft mc;

    @Unique
    private void iris$beginTranslucents(WorldRenderingPipeline pipeline, Camera camera) {
        pipeline.beginHand();
        HandRenderer.INSTANCE.renderSolid(camera.getPartialTicks(), camera, mc.renderGlobal, pipeline);
        mc.mcProfiler.endStartSection("iris_pre_translucent");
        pipeline.beginTranslucents();
    }

    @WrapOperation(method="renderEntities", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean angelica$renderEntitySimple(RenderManager instance, Entity entity, float partialTicks, Operation<Boolean> original) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(entity.getEntityId());
        GbufferPrograms.beginEntities();
        try {
            return original.call(instance, entity, partialTicks);
        } finally {
            CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
            GbufferPrograms.endEntities();
        }
    }

}
