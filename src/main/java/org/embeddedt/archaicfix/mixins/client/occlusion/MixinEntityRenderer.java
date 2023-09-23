package org.embeddedt.archaicfix.mixins.client.occlusion;

import net.minecraft.client.renderer.EntityRenderer;
import org.embeddedt.archaicfix.occlusion.OcclusionHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class)
public class MixinEntityRenderer {

    /**
     * @reason MixinRenderGlobal#performCullingUpdates needs to know the chunk update deadline and the partial tick time
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void getRendererUpdateDeadline(float partialTickTime, long chunkUpdateDeadline, CallbackInfo ci) {
        OcclusionHelpers.chunkUpdateDeadline = chunkUpdateDeadline;
        OcclusionHelpers.partialTickTime = partialTickTime;
    }

}
