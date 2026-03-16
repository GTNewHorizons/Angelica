package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.gl.blending.DepthColorStorage;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = GlProgram.class, remap = false)
public abstract class MixinGlProgram extends GlObject {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void iris$registerOwnedProgram(int program, Function<?, ?> interfaceFactory, CallbackInfo ci) {
        DepthColorStorage.registerOwnedProgram(program);
    }

    @Inject(method = "destroyInternal", at = @At("HEAD"))
    private void iris$unregisterOwnedProgram(CallbackInfo ci) {
        DepthColorStorage.unregisterOwnedProgram(this.handle());
    }
}
