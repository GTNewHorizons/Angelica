package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.renderer.tileentity;

import net.minecraft.client.renderer.tileentity.TileEntitySignRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.prupe.mcpatcher.cc.ColorizeWorld;

@Mixin(TileEntitySignRenderer.class)
public abstract class MixinTileEntitySignRenderer {

    @ModifyArg(
        method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntitySign;DDDF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I"),
        index = 3)
    private int modifyRenderTileEntityAt(int color) {
        return ColorizeWorld.colorizeSignText();
    }
}
