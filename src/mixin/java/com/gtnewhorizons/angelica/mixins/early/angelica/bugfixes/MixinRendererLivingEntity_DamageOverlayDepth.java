package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity_DamageOverlayDepth {

    @Shadow public ModelBase mainModel;

    @Unique private static final int ANGELICA$ENTITY_BITS = 0x70;
    @Unique private static final int ANGELICA$BODY_ID = 0x10;
    @Unique private static final int ANGELICA$PASS_ID = 0x20;
    @Unique private static final int ANGELICA$PAINTED_BIT = 0x40;

    @Unique private static final float ANGELICA$DEPTH_BIAS_FACTOR = -3.0F;
    @Unique private static final float ANGELICA$DEPTH_BIAS_UNITS = -5.0F;

    @Unique private boolean angelica$stencilMaskValid;

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V")
    )
    private void angelica$writePartMasks(RendererLivingEntity self, EntityLivingBase entity,
            float p1, float p2, float p3, float p4, float p5, float p6, Operation<Void> original) {
        angelica$stencilMaskValid = entity.hurtTime > 0 || entity.deathTime > 0;
        if (!angelica$stencilMaskValid) {
            original.call(self, entity, p1, p2, p3, p4, p5, p6);
            return;
        }
        GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
        GLStateManager.glStencilMask(ANGELICA$ENTITY_BITS);
        GLStateManager.glClearStencil(0);
        GLStateManager.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glStencilMask(ANGELICA$BODY_ID);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GLStateManager.glStencilFunc(GL11.GL_ALWAYS, ANGELICA$BODY_ID, ANGELICA$BODY_ID);
        original.call(self, entity, p1, p2, p3, p4, p5, p6);
        GLStateManager.glStencilMask(ANGELICA$PASS_ID);
        GLStateManager.glStencilFunc(GL11.GL_ALWAYS, ANGELICA$PASS_ID, ANGELICA$PASS_ID);
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
    )
    private void angelica$endPartMasks(CallbackInfo ci) {
        if (!angelica$stencilMaskValid) return;
        GLStateManager.glStencilMask(0x00);
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
    }

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
        )
    )
    private void angelica$paintPart(ModelBase model, Entity entity,
            float limbSwing, float limbSwingAmount, float ageInTicks,
            float headYaw, float headPitch, float scale, Operation<Void> original) {
        if (!angelica$stencilMaskValid) {
            original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
            return;
        }

        final int partId = (model == this.mainModel) ? ANGELICA$BODY_ID : ANGELICA$PASS_ID;

        GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(ANGELICA$DEPTH_BIAS_FACTOR, ANGELICA$DEPTH_BIAS_UNITS);

        if (partId == ANGELICA$BODY_ID) {
            ((AccessorRender) (Object) this).angelica$bindEntityTexture(entity);
        }
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);

        final int partTestMask = partId | ANGELICA$PAINTED_BIT;
        GLStateManager.glStencilMask(0x00);
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, partId, partTestMask);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);

        GLStateManager.glStencilMask(ANGELICA$PAINTED_BIT);
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, partId, partTestMask);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        original.call(model, entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);

        GLStateManager.glDisable(GL11.GL_ALPHA_TEST);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glPolygonOffset(0.0F, 0.0F);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glDepthMask(true);

        GLStateManager.glDepthFunc(GL11.GL_EQUAL);
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
        GLStateManager.glStencilMask(0xFF);
    }
}
