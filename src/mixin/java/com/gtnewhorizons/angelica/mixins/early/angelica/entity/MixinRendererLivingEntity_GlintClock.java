package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.helpers.RendererLivingEntityHelper;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.tesr.GlintCapture;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity_GlintClock {
    @Shadow @Final private static ResourceLocation RES_ITEM_GLINT;
    @Unique private static final Tracy.ZoneId angelica$Z_ARMOR_BASE = Tracy.zoneId("armorBase", Tracy.COLOR_CLIENT);
    @Unique private static final Tracy.ZoneId angelica$Z_ARMOR_GLINT = Tracy.zoneId("armorGlint", Tracy.COLOR_CLIENT);
    @Unique private final GlintCapture angelica$armorGlint = new GlintCapture();
    @Unique private final GlintCapture angelica$armorBase = new GlintCapture();
    @Unique private static final int GLINT_LAYER_QUEUED = 0, GLINT_LAYER_DRAWN = 1, GLINT_LAYER_CAPTURED = 2;
    @Unique private ModelBase angelica$baseModel;
    @Unique private boolean angelica$capturingBase;
    @Unique private int angelica$armorPassFlags;
    @Unique private int angelica$glintLayer;
    @Unique private boolean angelica$secondArmorQueued;
    @Unique private boolean angelica$zoneOpen;

    @ModifyConstant(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;RES_ITEM_GLINT:Lnet/minecraft/util/ResourceLocation;")),
        constant = @Constant(intValue = 2, ordinal = 0))
    private int angelica$armorGlintLayers(int vanilla) {
        return angelica$secondArmorQueued ? 1 : vanilla;
    }

    @ModifyConstant(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;RES_ITEM_GLINT:Lnet/minecraft/util/ResourceLocation;")),
        constant = @Constant(intValue = 15, ordinal = 0))
    private int angelica$queueArmorGlintBlock(int mask) {
        final ModelBase model = ((RendererLivingEntity) (Object) this).renderPassModel;
        if ((angelica$armorPassFlags & 15) != 15 || model != angelica$baseModel || GLStateManager.getOverlayA() != 0.0F || RendererLivingEntityHelper.hasEyePass(this)
            || !ModelPartBatcher.INSTANCE.queueSkippedArmorGlint(angelica$armorBase, RES_ITEM_GLINT)) return mask;
        SkippedGlintBlock.applyArmorExitState();
        return 0;
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 1)
        })
    private void angelica$beginArmorBase(CallbackInfo ci, @Local(ordinal = 0) int passFlags) {
        if (Tracy.FINE_ZONES) {
            Tracy.beginZone(angelica$Z_ARMOR_BASE);
            angelica$zoneOpen = true;
        }
        angelica$armorPassFlags = passFlags;
        angelica$baseModel = null;
        angelica$capturingBase = (passFlags & 15) == 15 && ModelPartBatcher.INSTANCE.beginArmorCapture(angelica$armorBase);
        if (angelica$capturingBase) GLStateManager.glPushMatrix();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0, shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 1, shift = At.Shift.AFTER)
        })
    private void angelica$endArmorBase(CallbackInfo ci) {
        if (angelica$capturingBase) {
            angelica$capturingBase = false;
            GLStateManager.glPopMatrix();
            angelica$armorBase.endWithRestoredPose();
            angelica$baseModel = ((RendererLivingEntity) (Object) this).renderPassModel;
        }
        angelica$endZone();
    }

    @WrapWithCondition(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;RES_ITEM_GLINT:Lnet/minecraft/util/ResourceLocation;")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0))
    private boolean angelica$renderArmorGlintLayer(ModelBase model, Entity entity, float swing, float amount, float age, float yaw, float pitch, float scale, @Local(ordinal = 2) int layer) {
        if (Tracy.FINE_ZONES) {
            Tracy.beginZone(angelica$Z_ARMOR_GLINT);
            angelica$zoneOpen = true;
        }
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;
        if (layer == 0) angelica$secondArmorQueued = false;
        angelica$glintLayer = GLINT_LAYER_QUEUED;
        if (layer == 0 && model == angelica$baseModel && batcher.reuseArmorBase(angelica$armorBase, angelica$armorGlint)) {
            angelica$secondArmorQueued = batcher.queueSecondArmorGlint(angelica$armorGlint);
            if (!angelica$secondArmorQueued) angelica$armorGlint.reset();
            return false;
        }
        if (layer == 1 && batcher.replayGlint(angelica$armorGlint)) return false;
        angelica$glintLayer = layer == 0 && batcher.beginGlintCapture(angelica$armorGlint) ? GLINT_LAYER_CAPTURED : GLINT_LAYER_DRAWN;
        GLStateManager.glPushMatrix();
        return true;
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;RES_ITEM_GLINT:Lnet/minecraft/util/ResourceLocation;")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void angelica$endArmorGlintLayer(CallbackInfo ci) {
        if (angelica$glintLayer != GLINT_LAYER_QUEUED) {
            GLStateManager.glPopMatrix();
            if (angelica$glintLayer == GLINT_LAYER_CAPTURED) {
                angelica$armorGlint.endWithRestoredPose();
                angelica$secondArmorQueued = ModelPartBatcher.INSTANCE.queueSecondArmorGlint(angelica$armorGlint);
            }
            angelica$glintLayer = GLINT_LAYER_QUEUED;
        }
        angelica$endZone();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V", remap = false))
    private void angelica$closeAfterFailedRender(CallbackInfo ci) {
        if (angelica$capturingBase) {
            angelica$capturingBase = false;
            GLStateManager.glPopMatrix();
            angelica$armorBase.reset();
        }
        if (angelica$glintLayer != GLINT_LAYER_QUEUED) {
            angelica$glintLayer = GLINT_LAYER_QUEUED;
            GLStateManager.glPopMatrix();
            angelica$armorGlint.reset();
        }
        angelica$endZone();
    }

    @Unique
    private void angelica$endZone() {
        if (!angelica$zoneOpen) return;
        angelica$zoneOpen = false;
        Tracy.endZone();
    }

    @Group(name = "angelica$globalGlintPhase", min = 1, max = 1)
    @Redirect(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1, remap = false)
    )
    private void angelica$globalGlintPhase(float x, float y, float z) {
        GlintClock.translateArmorGlint();
    }

    @Group(name = "angelica$globalGlintPhase", min = 1, max = 1)
    @Redirect(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/glsm/GLStateManager;glTranslatef(FFF)V",
            ordinal = 1,
            remap = false
        )
    )
    private void angelica$globalGlintPhaseGlsm(float x, float y, float z) {
        GlintClock.translateArmorGlint();
    }
}
