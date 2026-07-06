package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge_ModernF3 {

    @Redirect(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 0,
            remap = true))
    private int renderBackgroundLeft(FontRenderer fontRenderer, String text, int x, int y, int color) {
        Gui.drawRect(x - 1, y - 1, x + fontRenderer.getStringWidth(text) + 1, y + fontRenderer.FONT_HEIGHT, 0x90505050);
        return fontRenderer.drawString(text, x, y, 0xe0e0e0);
    }

    @Redirect(
        method = "renderHUDText",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I",
            ordinal = 1,
            remap = true))
    private int renderBackgroundRight(FontRenderer fontRenderer, String text, int x, int y, int color) {
        x += 8;
        Gui.drawRect(x - 1, y - 1, x + fontRenderer.getStringWidth(text) + 1, y + fontRenderer.FONT_HEIGHT, 0x90505050);
        return fontRenderer.drawString(text, x, y, 0xe0e0e0);
    }
}
