package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizons.angelica.render.CloudRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class)
public class MixinRenderGlobal {
    @Shadow public WorldClient theWorld;
    @Shadow public Minecraft mc;
    @Shadow private int cloudTickCounter;

    @Inject(method = "renderClouds(F)V", at = @At("HEAD"), cancellable = true)
    private void angelica$vboClouds(float partialTicks, CallbackInfo ci) {
        final IRenderHandler renderer = theWorld.provider.getCloudRenderer();
        if (renderer != null) {
            renderer.render(partialTicks, theWorld, mc);
            ci.cancel();
            return;
        }
        // Without shaders, use CloudRenderer for the faster path.
        // With shaders, fall through to the notfine @Overwrite which goes
        // through gbuffers_clouds so shader packs can customize rendering.
        if (!IrisApi.getInstance().isShaderPackInUse() && theWorld.provider.isSurfaceWorld()) {
            if (CloudRenderer.getCloudRenderer().render(cloudTickCounter, partialTicks)) {
                ci.cancel();
            }
        }
    }
}
