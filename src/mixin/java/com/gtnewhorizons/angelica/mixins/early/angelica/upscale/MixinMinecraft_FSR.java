package com.gtnewhorizons.angelica.mixins.early.angelica.upscale;

import com.gtnewhorizons.angelica.mixins.interfaces.IMinecraftMainFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Minecraft.class)
public class MixinMinecraft_FSR implements IMinecraftMainFramebuffer {

    @Shadow
    private Framebuffer framebufferMc;

    @Override
    public Framebuffer angelica$getMainFramebuffer() {
        return this.framebufferMc;
    }

    @Override
    public void angelica$setMainFramebuffer(Framebuffer framebuffer) {
        this.framebufferMc = framebuffer;
    }
}
