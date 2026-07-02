package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.render.CloudRenderer;
import jss.notfine.core.SettingsManager;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = EntityRenderer.class, priority = 990)
public abstract class MixinEntityRenderer {
    @ModifyArg(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false), index = 3)
    private float notFine$modifyFarPlane(float original) {
        float required = SettingsManager.minimumFarPlaneDistance;
        if (AngelicaConfig.enableVBOClouds) {
            required = Math.max(required, CloudRenderer.getCloudRenderer().getRequiredFarPlaneDistance());
        }
        return Math.max(original, required);
    }

}
