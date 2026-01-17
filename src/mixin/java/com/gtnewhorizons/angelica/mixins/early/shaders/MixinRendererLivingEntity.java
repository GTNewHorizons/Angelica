package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.client.renderer.entity.RenderEnderman;
import net.minecraft.client.renderer.entity.RenderSpider;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity {
    @WrapOperation(
        method="doRender",
        at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RendererLivingEntity;getColorMultiplier(Lnet/minecraft/entity/EntityLivingBase;FF)I")
    )
    private int iris$setEntityColor(RendererLivingEntity instance, EntityLivingBase elb, float f0, float f1, Operation<Integer> original) {
        final int j = original.call(instance, elb, f0, f1);
        final float a = (j >> 24 & 255) / 255.0F;
        final float r = (j >> 16 & 255) / 255.0F;
        final float g = (j >> 8 & 255) / 255.0F;
        final float b = (j & 255) / 255.0F;
        CapturedRenderingState.INSTANCE.setCurrentEntityColor(r, g, b, a);
        return j;
    }

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;shouldRenderPass(Lnet/minecraft/entity/EntityLivingBase;IF)I")
        )
    )
    private void iris$specialRenderConditionEntityEyes(ModelBase instance, Entity entity, float p_78088_2_, float p_78088_3_, float p_78088_4_, float p_78088_5_, float p_78088_6_, float p_78088_7_, Operation<Void> original) {
        RendererLivingEntity self = (RendererLivingEntity) (Object) this;
        if (self instanceof RenderSpider || self instanceof RenderDragon || self instanceof RenderEnderman) {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.ENTITY_EYES);
            original.call(instance, entity, p_78088_2_, p_78088_3_, p_78088_4_, p_78088_5_, p_78088_6_, p_78088_7_);
            GbufferPrograms.teardownSpecialRenderCondition();
        } else {
            original.call(instance, entity, p_78088_2_, p_78088_3_, p_78088_4_, p_78088_5_, p_78088_6_, p_78088_7_);
        }
    }

    /**
     * Activate GLINT shader before rendering armor enchantment glint.
     * Injects at the first glDepthFunc inside the armor glint rendering block.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0),
        remap = false
    )
    private void iris$activateArmorGlintShader(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
    }

    /**
     * Deactivate GLINT shader after rendering armor enchantment glint.
     * Injects at the last glDepthFunc inside the armor glint rendering block.
     */
    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, shift = At.Shift.AFTER),
        remap = false
    )
    private void iris$deactivateArmorGlintShader(EntityLivingBase entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
