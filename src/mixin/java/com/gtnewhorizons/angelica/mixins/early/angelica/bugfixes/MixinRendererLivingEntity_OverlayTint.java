package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Single-pass damage-overlay tint, modeled on modern Minecraft's OverlayTexture.
 */
@Mixin(RendererLivingEntity.class)
public class MixinRendererLivingEntity_OverlayTint {

    @Unique private static final float ANGELICA$RED_MIX = 1.0F - (0xB2 / 255.0F);

    @Unique private boolean angelica$skipReRender;
    @Unique private boolean angelica$ffpOverlayActive;
    @Unique private boolean angelica$shaderEntityColorActive;

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V"),
        require = 1, expect = 1
    )
    private void angelica$setupOverlay(EntityLivingBase entity, double x, double y, double z,
                                       float yaw, float partialTick, CallbackInfo ci) {
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);

        if (entity.hurtTime <= 0 && entity.deathTime <= 0) return;
        if (angelica$isShaderPackActive()) {
            // Iris path
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
            angelica$shaderEntityColorActive = true;
        } else {
            // FFP path
            GLStateManager.setOverlayColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
            angelica$ffpOverlayActive = true;
        }
        angelica$skipReRender = true;
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V"),
        require = 1, expect = 1
    )
    private void angelica$teardownOverlay(EntityLivingBase entity, double x, double y, double z,
                                          float yaw, float partialTick, CallbackInfo ci) {
        if (angelica$ffpOverlayActive) {
            GLStateManager.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
            angelica$ffpOverlayActive = false;
        }
        if (angelica$shaderEntityColorActive) {
            // Don't tint held items
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(0.0F, 0.0F, 0.0F, 0.0F);
            angelica$shaderEntityColorActive = false;
        }
    }

    /**
     * Skip the vanilla re-render block.
     */
    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(
            from = @At(value = "FIELD",
                target = "Lnet/minecraft/entity/EntityLivingBase;hurtTime:I",
                ordinal = 1)
        ),
        require = 1, expect = 1
    )
    private void angelica$skipOverlayReRender(ModelBase model, Entity entity,
            float p1, float p2, float p3, float p4, float p5, float p6, Operation<Void> original) {
        if (angelica$skipReRender) return;
        original.call(model, entity, p1, p2, p3, p4, p5, p6);
    }

    /**
     * Skip the overlay on additive passes (emissive eyes, enchantment glint).
     */
    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"),
        slice = @Slice(
            from = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;shouldRenderPass(Lnet/minecraft/entity/EntityLivingBase;IF)I"),
            to = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderEquippedItems(Lnet/minecraft/entity/EntityLivingBase;F)V")
        ),
        require = 1, expect = 1
    )
    private void angelica$untintAdditivePass(ModelBase model, Entity entity,
            float p1, float p2, float p3, float p4, float p5, float p6, Operation<Void> original) {
        if (!angelica$skipReRender || !angelica$isAdditivePass()) {
            original.call(model, entity, p1, p2, p3, p4, p5, p6);
            return;
        }
        if (angelica$ffpOverlayActive) {
            GLStateManager.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
            original.call(model, entity, p1, p2, p3, p4, p5, p6);
            GLStateManager.setOverlayColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
        } else if (angelica$shaderEntityColorActive) {
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(0.0F, 0.0F, 0.0F, 0.0F);
            original.call(model, entity, p1, p2, p3, p4, p5, p6);
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
        } else {
            original.call(model, entity, p1, p2, p3, p4, p5, p6);
        }
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
        at = @At("RETURN")
    )
    private void angelica$reset(CallbackInfo ci) {
        if (angelica$ffpOverlayActive) {
            GLStateManager.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
            angelica$ffpOverlayActive = false;
        }
        if (angelica$shaderEntityColorActive) {
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(0.0F, 0.0F, 0.0F, 0.0F);
            angelica$shaderEntityColorActive = false;
        }
        angelica$skipReRender = false;
    }

    @Unique
    private static boolean angelica$isAdditivePass() {
        if (!GLStateManager.glIsEnabled(GL11.GL_BLEND)) return false;
        if (GLStateManager.glGetInteger(GL14.GL_BLEND_DST_RGB) != GL11.GL_ONE) return false;
        final int src = GLStateManager.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        return src == GL11.GL_ONE || src == GL11.GL_SRC_COLOR;
    }

    @Unique
    private static boolean angelica$isShaderPackActive() {
        if (!AngelicaConfig.enableIris) return false;
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline != null && !(pipeline instanceof FixedFunctionWorldRenderingPipeline);
    }
}
