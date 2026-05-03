package com.gtnewhorizons.umbra.mixins.early.glsm;

import net.minecraftforge.client.ForgeHooksClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
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
 * Requests highest GL version with core profile context.
 * Required for GLSM's FFP emulation to work correctly.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient_CoreProfile {

    @Unique private static final Logger LOGGER = LogManager.getLogger("Umbra");

    @Shadow static int stencilBits;

    /**
     * @author Umbra
     * @reason Request highest GL version with core profile context for GLSM
     */
    @Overwrite
    public static void createDisplay() throws LWJGLException {
        ImageIO.setUseCache(false);
        stencilBits = 8;

        final PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(stencilBits);

        final boolean lwjglDebug = Boolean.getBoolean("org.lwjgl.util.Debug");
        final ContextAttribs attribs = new ContextAttribs(3, 3)
            .withProfileCore(true).withForwardCompatible(true).withDebug(lwjglDebug);

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

        final int platformMaxMajor = 4;
        final int platformMaxMinor = (LWJGLUtil.getPlatform() == LWJGLUtil.PLATFORM_MACOSX) ? 1 : 6;

        // Probe from highest to lowest
        Exception lastException = null;
        for (int major = platformMaxMajor; major >= 3; --major) {
            final int maxMinor = (major == 4) ? platformMaxMinor : 3;
            final int minMinor = (major == 3) ? 3 : 0;
            for (int minor = maxMinor; minor >= minMinor; --minor) {
                final Exception e = umbra$tryCreate(attribs, format, setMajor, setMinor, major, minor);
                if (e == null) {
                    LOGGER.info("Created GL {}.{} core profile context", major, minor);
                    return;
                }
                lastException = e;
            }
        }

        LOGGER.error("FATAL: Failed to create OpenGL core profile context. Error: {}", lastException != null ? lastException.getMessage() : "unknown");
        if (lastException instanceof LWJGLException lwjgl) {
            throw lwjgl;
        }
        throw new LWJGLException("Failed to create OpenGL core profile context", lastException);
    }

    @Unique
    private static Exception umbra$tryCreate(ContextAttribs attribs, PixelFormat format, MethodHandle setMajor, MethodHandle setMinor, int major, int minor) {
        try {
            setMajor.invokeExact(attribs, major);
            setMinor.invokeExact(attribs, minor);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to set ContextAttribs version", t);
        }
        try {
            Display.create(format, attribs);
            return null;
        } catch (LWJGLException | RuntimeException e) {
            try { Display.destroy(); } catch (Exception ignored) {}
            return e;
        }
    }
}
