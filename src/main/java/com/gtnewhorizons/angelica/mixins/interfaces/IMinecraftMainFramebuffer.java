package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.shader.Framebuffer;

/** Implemented on Minecraft by MixinMinecraft_FSR; lets FSR1 swap the main framebuffer for the world pass. */
public interface IMinecraftMainFramebuffer {

    Framebuffer angelica$getMainFramebuffer();

    void angelica$setMainFramebuffer(Framebuffer framebuffer);
}
