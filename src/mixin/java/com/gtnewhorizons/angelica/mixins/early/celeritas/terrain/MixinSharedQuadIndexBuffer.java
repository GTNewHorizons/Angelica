package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.TerrainDrawStats;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SharedQuadIndexBuffer.class, remap = false)
public abstract class MixinSharedQuadIndexBuffer {

    @Inject(method = "grow", at = @At("HEAD"))
    private void angelica$onGrow(CommandList commandList, int primitiveCount, CallbackInfo ci) {
        TerrainDrawStats.recordIndexBufferGrowth();
    }
}
