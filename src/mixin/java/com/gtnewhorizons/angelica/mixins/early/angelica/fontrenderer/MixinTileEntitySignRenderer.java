package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.client.font.ColorCodeUtils;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TileEntitySignRenderer.class)
public class MixinTileEntitySignRenderer {

    @Redirect(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;getStringWidth(Ljava/lang/String;)I"))
    private int angelica$preprocessSignWidth(FontRenderer fr, String text) {
        if (AngelicaConfig.enableAmpersandConversion) {
            text = ColorCodeUtils.convertAmpersandToSectionX(text);
        }
        return fr.getStringWidth(text);
    }
}
