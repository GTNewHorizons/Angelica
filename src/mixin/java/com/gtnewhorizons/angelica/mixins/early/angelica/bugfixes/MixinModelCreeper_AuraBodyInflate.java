package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.model.ModelCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ModelCreeper.class)
public class MixinModelCreeper_AuraBodyInflate {

    /**
     * Slightly reduce the body inflate so the aura body doesn't intersect the base model's head.
     * Only applies when inflate > 0 (the aura model), leaving the base model untouched.
     * The body addBox is the 3rd call (ordinal 2) in the constructor, and inflate is parameter index 6.
     */
    @ModifyArg(
        method = "<init>(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelRenderer;addBox(FFFIIIF)V", ordinal = 2), index = 6
    )
    private float angelica$shrinkAuraBody(float inflate) {
        if (inflate > 0.0F) {
            return Float.intBitsToFloat(Float.floatToIntBits(inflate) - 16384);
        }
        return inflate;
    }
}
