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
import org.spongepowered.asm.mixin.Unique;

import javax.imageio.ImageIO;

/**
 * Requests highest GL version with core profile context
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient_CoreProfile {

    @Shadow static int stencilBits;

    /**
     * @author Angelica
     * @reason Request highest GL version with core profile context
     */
    @Overwrite
    public static void createDisplay() throws LWJGLException {
        ImageIO.setUseCache(false);
        // Preserve vanilla Forge stencil support -- enabled via -Dforge.forceDisplayStencil=true by users/modpacks
        final boolean wantStencil = Boolean.parseBoolean(System.getProperty("forge.forceDisplayStencil", "false"));
        stencilBits = wantStencil ? 8 : 0;

        final PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(stencilBits);

        LWJGLException cur_exception = null;
        for(byte cur_major = 4; cur_major > 0; --cur_major) {
            for(byte cur_minor = 5; cur_minor >= 0; --cur_minor) {
                try {
                    Display.create(format, new ContextAttribs(cur_major, cur_minor).withProfileCore(true).withForwardCompatible(true));
                    return;
                }catch (LWJGLException e) {
                    cur_exception = e;
                }
            }
        }
        angelica$reportContextFailure(cur_exception);
        throw cur_exception;
    }

    @Unique
    private static void angelica$reportContextFailure(LWJGLException e) {
        System.err.println("[Angelica] FATAL: Failed to create OpenGL core profile context.");
        System.err.println("[Angelica] Error: " + e.getMessage());
        try {
            System.err.println("[Angelica] GPU: " + GL11.glGetString(GL11.GL_RENDERER) + ", Driver: " + GL11.glGetString(GL11.GL_VERSION));
        } catch (Exception ignored) {}
    }
}
