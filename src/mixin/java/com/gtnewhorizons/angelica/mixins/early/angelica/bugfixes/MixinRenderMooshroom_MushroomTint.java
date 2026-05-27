package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderMooshroom;
import net.minecraft.entity.passive.EntityMooshroom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Make it so that the mushrooms on the mooshroom are also tinted when the mob is damaged.
 */
@Mixin(RenderMooshroom.class)
public class MixinRenderMooshroom_MushroomTint {

    @Unique private static final float ANGELICA$RED_MIX = 1.0F - (0xB2 / 255.0F);

    @Unique private boolean angelica$mushroomTintActive;
    @Unique private boolean angelica$mushroomTintShaderPath;

    @Inject(
        method = "renderEquippedItems(Lnet/minecraft/entity/passive/EntityMooshroom;F)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V",
            ordinal = 0),
        require = 1, expect = 1
    )
    private void angelica$applyMushroomTint(EntityMooshroom entity, float partialTick, CallbackInfo ci) {
        if (entity.hurtTime <= 0 && entity.deathTime <= 0) return;
        angelica$mushroomTintShaderPath = angelica$isShaderPackActive();
        if (angelica$mushroomTintShaderPath) {
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
        } else {
            GLStateManager.setOverlayColor(1.0F, 0.0F, 0.0F, ANGELICA$RED_MIX);
        }
        angelica$mushroomTintActive = true;
    }

    @Inject(
        method = "renderEquippedItems(Lnet/minecraft/entity/passive/EntityMooshroom;F)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V",
            ordinal = 2,
            shift = At.Shift.AFTER),
        require = 1, expect = 1
    )
    private void angelica$clearMushroomTint(EntityMooshroom entity, float partialTick, CallbackInfo ci) {
        if (!angelica$mushroomTintActive) return;
        if (angelica$mushroomTintShaderPath) {
            CapturedRenderingState.INSTANCE.setCurrentEntityColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            GLStateManager.setOverlayColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        angelica$mushroomTintActive = false;
    }

    @Unique
    private static boolean angelica$isShaderPackActive() {
        if (!AngelicaConfig.enableIris) return false;
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline != null && !(pipeline instanceof FixedFunctionWorldRenderingPipeline);
    }
}
