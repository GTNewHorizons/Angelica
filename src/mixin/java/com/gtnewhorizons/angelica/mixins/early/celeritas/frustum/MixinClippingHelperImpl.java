package com.gtnewhorizons.angelica.mixins.early.celeritas.frustum;

import com.gtnewhorizons.angelica.mixins.interfaces.ClippingHelperExt;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClippingHelperImpl.class)
public class MixinClippingHelperImpl {

    @Inject(method = "init", at = @At("RETURN"))
    private void celeritas$runUpdateFrustumIntersection(CallbackInfo ci) {
        ((ClippingHelperExt) this).celeritas$updateFrustumIntersection();
    }
}
