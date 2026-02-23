package com.gtnewhorizons.angelica.mixins.early.angelica;

import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.imageio.ImageIO;

/**
 * Requests a GL 3.3 core profile context
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient_CoreProfile {

    @Shadow static int stencilBits;

    /**
     * @author Angelica
     * @reason Request GL 3.3 core profile context
     */
    @Overwrite
    public static void createDisplay() throws LWJGLException {
        ImageIO.setUseCache(false);
        final PixelFormat format = new PixelFormat().withDepthBits(24);
        final ContextAttribs attribs = new ContextAttribs(3, 3)
            .withProfileCore(true)
            .withForwardCompatible(true);

        // Preserve vanilla Forge stencil support -- enabled via -Dforge.forceDisplayStencil=true by users/modpacks
        final boolean wantStencil = Boolean.parseBoolean(System.getProperty("forge.forceDisplayStencil", "false"));

        if (!wantStencil) {
            try {
                Display.create(format, attribs);
            } catch (LWJGLException e) {
                reportContextFailure(e);
                throw e;
            }
            stencilBits = 0;
            return;
        }

        try {
            Display.create(format.withStencilBits(8), attribs);
            stencilBits = 8;
        } catch (LWJGLException e) {
            try {
                Display.create(format, attribs);
                stencilBits = 0;
            } catch (LWJGLException e2) {
                reportContextFailure(e2);
                throw e2;
            }
        }
    }

    private static void reportContextFailure(LWJGLException e) {
        System.err.println("[Angelica] FATAL: Failed to create OpenGL 3.3 core profile context.");
        System.err.println("[Angelica] Error: " + e.getMessage());
        try {
            System.err.println("[Angelica] GPU: " + GL11.glGetString(GL11.GL_RENDERER) + ", Driver: " + GL11.glGetString(GL11.GL_VERSION));
        } catch (Exception ignored) {}
    }
}
