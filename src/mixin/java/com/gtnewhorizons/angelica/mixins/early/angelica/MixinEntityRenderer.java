package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "setupCameraTransform", at = @At(value = "TAIL"))
    private void angelica$captureCameraMatrix(float partialTicks, int startTime, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase viewEntity = mc.renderViewEntity;

        RenderingState.INSTANCE.setCameraPosition(
            viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks,
            viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks,
            viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks
        );
    }

    @ModifyArg(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0, remap = false), index = 0)
    private float captureFov(float fov) {
        RenderingState.INSTANCE.setFov(fov);
        return fov;
    }
}
