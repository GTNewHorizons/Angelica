package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.event.liteloader.LiteloaderEvent;
import com.gtnewhorizons.angelica.event.liteloader.LiteloaderEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft_IconifyGuard {

    @Redirect(
        method = "runGameLoop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(F)V")
    )
    private void angelica$skipRenderWhenIconified(EntityRenderer entityRenderer, float partialTicks) {
        LiteloaderEvent.post(LiteloaderEventType.ON_TICK);
        final Framebuffer fb = Minecraft.getMinecraft().getFramebuffer();
        if (fb == null || fb.framebufferWidth < 16 || fb.framebufferHeight < 16) {
            return;
        }
        entityRenderer.updateCameraAndRender(partialTicks);
    }
}
