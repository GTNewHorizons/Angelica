package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import net.minecraft.client.renderer.GLAllocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GLAllocation.class)
public class MixinGLAllocation {
    @Inject(method = "deleteDisplayLists", at = @At("HEAD"), cancellable = true)
    private static void deleteDisplayLists(int list, CallbackInfo ci) {
        if(list < 0) ci.cancel();
    }
}
