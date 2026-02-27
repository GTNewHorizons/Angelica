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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

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

        final ContextAttribs attribs = new ContextAttribs(4, 5).withProfileCore(true).withForwardCompatible(true);
        final MethodHandle setMajor, setMinor;
        try {
            final Field majorField = ContextAttribs.class.getDeclaredField("majorVersion");
            final Field minorField = ContextAttribs.class.getDeclaredField("minorVersion");
            majorField.setAccessible(true);
            minorField.setAccessible(true);
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            setMajor = lookup.unreflectSetter(majorField);
            setMinor = lookup.unreflectSetter(minorField);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to obtain ContextAttribs version setters", e);
        }

        Exception cur_exception = null;
        for (int major = 4; major >= 3; --major) {
            final int maxMinor = (major == 4) ? 6 : 3;
            final int minMinor = (major == 3) ? 3 : 0;
            for (int minor = maxMinor; minor >= minMinor; --minor) {
                try {
                    setMajor.invokeExact(attribs, major);
                    setMinor.invokeExact(attribs, minor);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to set ContextAttribs version", t);
                }
                try {
                    Display.create(format, attribs);
                    return;
                } catch (LWJGLException | RuntimeException e) {
                    cur_exception = e;
                }
            }
        }
        angelica$reportContextFailure(cur_exception);
        if (cur_exception instanceof LWJGLException lwjgl) {
            throw lwjgl;
        }
        throw new LWJGLException("Failed to create OpenGL core profile context", cur_exception);
    }

    @Unique
    private static void angelica$reportContextFailure(Exception e) {
        System.err.println("[Angelica] FATAL: Failed to create OpenGL core profile context.");
        System.err.println("[Angelica] Error: " + e.getMessage());
        try {
            System.err.println("[Angelica] GPU: " + GL11.glGetString(GL11.GL_RENDERER) + ", Driver: " + GL11.glGetString(GL11.GL_VERSION));
        } catch (Exception ignored) {}
    }
}
