package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.PlayerReflectionCapture;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ModelBiped.class)
public abstract class MixinModelBiped {

    @Unique private static final Tracy.ZoneId Z_PLAYER_REFLECTION_CAPTURE = Tracy.zoneId("playerReflectionCapture", Tracy.COLOR_IRIS);

    @Inject(method = "render(Lnet/minecraft/entity/Entity;FFFFFF)V", at = @At("RETURN"))
    private void angelica$capturePlayerReflection(Entity entity, float limbSwing, float limbSwingAmount,
        float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {

        if (!PlayerReflectionCapture.shouldCapture()) return;
        if ((Object) this != PlayerReflectionCapture.getTarget()) return;
        if (entity != PlayerReflectionCapture.getTargetEntity()) return;

        if (Tracy.ENABLED) Tracy.beginZone(Z_PLAYER_REFLECTION_CAPTURE);
        try {
            PlayerReflectionCapture.emitAndSubmit((ModelBiped) (Object) this, scale);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }
}
