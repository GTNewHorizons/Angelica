package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents z-fighting between the skeleton's leg tops and the pelvis (bipedBody) bottom face.
 */
@Mixin(ModelSkeleton.class)
public class MixinModelSkeleton_LegPelvisZFight {

    @Unique
    private static final float LEG_PIVOT_Y = Float.intBitsToFloat(Float.floatToIntBits(12.0F) + 128);

    @Inject(
        method = "setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V",
        at = @At("TAIL")
    )
    private void angelica$nudgeLegsBelowPelvis(float swing, float swingAmount, float ticks,
                                               float headYaw, float headPitch, float scale,
                                               Entity entity, CallbackInfo ci) {
        final ModelSkeleton self = (ModelSkeleton) (Object) this;
        self.bipedRightLeg.rotationPointY = LEG_PIVOT_Y;
        self.bipedLeftLeg.rotationPointY = LEG_PIVOT_Y;
    }
}
