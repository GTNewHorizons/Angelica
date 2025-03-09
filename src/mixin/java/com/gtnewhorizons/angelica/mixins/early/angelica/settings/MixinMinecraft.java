package com.gtnewhorizons.angelica.mixins.early.angelica.settings;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.lwjglx.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Redirect(method = "runGameLoop", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fancyGraphics:Z"))
    private boolean sodium$overrideFancyGrass(GameSettings gameSettings) {
        return SodiumClientMod.options().quality.grassQuality.isFancy();
    }

    @Redirect(method = "checkGLError", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetError()I", remap = false))
    private int sodium$checkGLError() {
        return SodiumClientMod.options().performance.useNoErrorGLContext ? 0 : GL11.glGetError();
    }
}
