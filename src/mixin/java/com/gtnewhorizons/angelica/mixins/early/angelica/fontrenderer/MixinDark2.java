package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiContainer.class)
public abstract class MixinDark2 {

    @Shadow
    protected abstract void drawGuiContainerForegroundLayer(int mouseX, int mouseY);

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V"))
    private void angelica$trackScreenDraw(GuiContainer guiContainer, int mouseX, int mouseY) {
        final boolean previous = BatchingFontRenderer.beginScreenDraw();
        try {
            drawGuiContainerForegroundLayer(mouseX, mouseY);
        } finally {
            BatchingFontRenderer.endScreenDraw(previous);
        }
    }
}

