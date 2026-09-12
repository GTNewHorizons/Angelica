package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class MixinDark1 {

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"))
    private void angelica$trackScreenDraw(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (BatchingFontRenderer.matchGuiScreen(screen)) {
            final boolean previous = BatchingFontRenderer.beginScreenDraw();
            try {
                screen.drawScreen(mouseX, mouseY, partialTicks);
            } finally {
                BatchingFontRenderer.endScreenDraw(previous);
            }
        } else {
            screen.drawScreen(mouseX, mouseY, partialTicks);
        }
    }
}
