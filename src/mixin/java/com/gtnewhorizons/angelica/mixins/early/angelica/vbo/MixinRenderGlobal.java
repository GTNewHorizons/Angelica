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
        final IRenderHandler renderer;
        if ((renderer = theWorld.provider.getCloudRenderer()) != null) {
            renderer.render(partialTicks, theWorld, mc);
            ci.cancel();
            return;
        }
        // When a shader pack is active, fall through to the Tessellator-based cloud path so
        // the pack's gbuffers_clouds program (and clouds= directive) can apply.
        if (IrisApi.getInstance().isShaderPackInUse()) {
            return;
        }
        if (mc.theWorld.provider.isSurfaceWorld()) {
            CloudRenderer.getCloudRenderer().render(cloudTickCounter, partialTicks);
            ci.cancel();
        }
    }
}
