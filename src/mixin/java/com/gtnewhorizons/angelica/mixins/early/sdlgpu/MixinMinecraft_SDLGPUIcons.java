package com.gtnewhorizons.angelica.mixins.early.sdlgpu;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.ByteBuffer;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_SDLGPUIcons {

    @ModifyArg(method = "startGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setIcon([Ljava/nio/ByteBuffer;)I", remap = false), index = 0)
    private ByteBuffer[] angelicaSdl$rememberIcons(ByteBuffer[] icons) {
        SDLGPUGate.rememberIcons(icons);
        return icons;
    }
}
