package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.minecraftforge.client.GuiIngameForge;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngameForge.class, remap = false)
public class MixinGuiIngameForge {
    @Inject(method="renderCrosshairs", at= @At(value = "INVOKE", target = "Lnet/minecraftforge/client/GuiIngameForge;bind(Lnet/minecraft/util/ResourceLocation;)V", shift = At.Shift.AFTER))
    private void renderCrosshairs(CallbackInfo ci) {
        // Set the same state as vanilla so we don't break with textures that set a color value on transparent pixels for the crosshairs
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }
}
