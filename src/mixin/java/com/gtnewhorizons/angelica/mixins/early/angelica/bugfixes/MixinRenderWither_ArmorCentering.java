package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.rendering.DeferredEntityOverlay;
import com.gtnewhorizons.angelica.rendering.WitherArmorState;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelWither;
import net.minecraft.client.renderer.entity.RenderWither;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.boss.EntityWither;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the Wither's glScalef(1.1) armor approach with a per-box inflated model.
 * This fixes the armor being visually off-center on off-center and rotating parts.
 * Not exactly mod friendly but we can figure that out later.
 */
@Mixin(RenderWither.class)
public abstract class MixinRenderWither_ArmorCentering {

    @Shadow
    protected abstract int shouldRenderPass(EntityWither entity, int pass, float partialTick);

    @Unique
    private ModelWither angelica$armorModel;

    @Unique
    private DeferredEntityOverlay.ShouldRenderPassFn angelica$cachedPassFn;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void angelica$createArmorModel(CallbackInfo ci) {
        WitherArmorState.pendingInflate = true;
        angelica$armorModel = new ModelWither();
    }

    // Use the inflated armor model instead of mainModel.
    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/entity/boss/EntityWither;IF)I",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderWither;setRenderPassModel(Lnet/minecraft/client/model/ModelBase;)V")
    )
    private void angelica$useInflatedModel(RenderWither instance, ModelBase model) {
        ((RenderWither) (Object) this).setRenderPassModel(angelica$armorModel);
    }

    // No-op the model offset translate (ordinal 1)
    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/entity/boss/EntityWither;IF)I",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 1, remap = false)
    )
    private void angelica$skipTranslate(float x, float y, float z) {
    }

    // No-op glScalef
    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/entity/boss/EntityWither;IF)I",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V", remap = false)
    )
    private void angelica$skipScale(float x, float y, float z) {
    }

    // Mark the Wither's armor pass for deferred rendering.
    @Inject(method = "shouldRenderPass(Lnet/minecraft/entity/boss/EntityWither;IF)I",
        at = @At("HEAD"))
    private void angelica$markArmorPass(EntityWither wither, int pass, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (pass == 1 && wither.isArmored() && !DeferredEntityOverlay.isReplaying()) {
            if (angelica$cachedPassFn == null) {
                angelica$cachedPassFn = (entity, pass2, tick) -> this.shouldRenderPass((EntityWither) entity, pass2, tick);
            }
            DeferredEntityOverlay.markOverlayPass(
                angelica$cachedPassFn,
                (RendererLivingEntity) (Object) this,
                wither, partialTick
            );
        }
    }
}
