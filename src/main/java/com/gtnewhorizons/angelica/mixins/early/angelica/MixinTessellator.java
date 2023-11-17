package com.gtnewhorizons.angelica.mixins.early.angelica;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.Tessellator;

@Mixin(Tessellator.class)
abstract class MixinTessellator {
    /** @reason Allow using multiple tessellator instances concurrently by removing static field access in alternate
     * instances. */
    @Redirect(method = "reset", at = @At(value = "INVOKE", target = "Ljava/nio/ByteBuffer;clear()Ljava/nio/Buffer;"))
    private Buffer removeStaticBufferAccessOutsideSingleton(ByteBuffer buffer) {
        if(((Object)this) == Tessellator.instance) {
            return buffer.clear();
        }
        return buffer;
    }
}
