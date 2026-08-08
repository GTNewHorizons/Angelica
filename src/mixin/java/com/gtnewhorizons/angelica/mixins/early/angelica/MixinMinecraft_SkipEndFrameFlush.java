package com.gtnewhorizons.angelica.mixins.early.angelica;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft_SkipEndFrameFlush {

    @Redirect(method = "runGameLoop", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFlush()V", remap = false))
    private void angelica$skipEndOfFrameFlush() {
    }
}
