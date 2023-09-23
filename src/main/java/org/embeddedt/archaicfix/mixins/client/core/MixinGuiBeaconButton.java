package org.embeddedt.archaicfix.mixins.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = { "net/minecraft/client/gui/inventory/GuiBeacon$Button" })
public class MixinGuiBeaconButton {
    /**
     * Make transparent beacon buttons look right (e.g. with Modernity).
     */
    @Inject(method = "func_146112_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/inventory/GuiBeacon$Button;func_73729_b(IIIIII)V", ordinal = 1), require = 0)
    private void enableTransparency(Minecraft p_146112_1_, int p_146112_2_, int p_146112_3_, CallbackInfo ci) {
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
    }
}
