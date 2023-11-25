package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {

    @Shadow(remap = false)
    private FontRenderer fontrenderer;

    @Inject(
        method = "renderHUDText",
        at = @At(
            value = "INVOKE",
            target = "net/minecraftforge/client/event/RenderGameOverlayEvent$Text.<init>(Lnet/minecraftforge/client/event/RenderGameOverlayEvent;Ljava/util/ArrayList;Ljava/util/ArrayList;)V"),
        remap = false)
    private void angelica$startF3TextBatching(int width, int height, CallbackInfo ci) {
        FontRendererAccessor fra = (FontRendererAccessor) (Object) fontrenderer;
        fra.angelica$getBatcher().beginBatch();
    }

    @Inject(
        method = "renderHUDText",
        at = @At(value = "INVOKE", target = "net/minecraft/profiler/Profiler.endSection()V"))
    private void angelica$endF3TextBatching(int width, int height, CallbackInfo ci) {
        FontRendererAccessor fra = (FontRendererAccessor) (Object) fontrenderer;
        fra.angelica$getBatcher().endBatch();
    }
}
