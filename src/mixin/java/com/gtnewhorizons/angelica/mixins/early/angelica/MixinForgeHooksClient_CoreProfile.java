package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraftforge.client.ForgeHooksClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.LWJGLUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.imageio.ImageIO;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Requests highest GL version with core profile context, with optional version pinning.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClient_CoreProfile {

    @Unique private static final Logger LOGGER = LogManager.getLogger("Angelica");

    @Shadow static int stencilBits;

    /**
     * @author Angelica
     * @reason Request highest GL version with core profile context, or GL ES 3.2 when requested
     */
    @Overwrite
    public static void createDisplay() throws LWJGLException {
        ImageIO.setUseCache(false);
        // Always enable 8-bit stencil, was an option before "-Dforge.forceDisplayStencil=true"
        stencilBits = 8;

        final PixelFormat format = new PixelFormat().withDepthBits(24).withStencilBits(stencilBits);

        final AngelicaConfig.GLProfile profile = AngelicaConfig.getEffectiveGlProfile();

        if (profile == AngelicaConfig.GLProfile.ES) {
            final Exception e = angelica$tryCreateES();
            if (e == null) {
                LOGGER.info("Created GL ES 3.2 context");
                return;
            }
            angelica$reportContextFailure(e);
            if (e instanceof LWJGLException lwjgl) throw lwjgl;
            throw new LWJGLException("Failed to create OpenGL ES 3.2 context", e);
        }

        final ContextAttribs attribs = new ContextAttribs(3, 3).withProfileCore(true).withForwardCompatible(true).withDebug(AngelicaMod.lwjglDebug);
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
        final int platformMax = platformMaxMajor * 10 + platformMaxMinor;

        // Try pinned version first if configured
        final int pinned = angelica$clampPinned(AngelicaConfig.pinnedGLVersion, platformMax);
        if (pinned >= 33) {
            final Exception e = angelica$tryCreate(attribs, format, setMajor, setMinor, pinned / 10, pinned % 10);
            if (e == null) {
                LOGGER.info("Created GL {}.{} core profile context (pinned)", pinned / 10, pinned % 10);
                return;
            }
            LOGGER.warn("Pinned GL version {}.{} failed, probing from highest", pinned / 10, pinned % 10);
        }

        // Probe from highest to lowest
        Exception lastException = null;
        for (int major = platformMaxMajor; major >= 3; --major) {
            final int maxMinor = (major == 4) ? platformMaxMinor : 3;
            final int minMinor = (major == 3) ? 3 : 0;
            for (int minor = maxMinor; minor >= minMinor; --minor) {
                final Exception e = angelica$tryCreate(attribs, format, setMajor, setMinor, major, minor);
                if (e == null) {
                    final int version = major * 10 + minor;
                    LOGGER.info("Created GL {}.{} core profile context", major, minor);
                    // Auto-pin if below platform max, or update a stale pin
                    if (version < platformMax && AngelicaConfig.pinnedGLVersion != version && (AngelicaConfig.pinnedGLVersion != 0 || !AngelicaConfig.disableGLVersionPinning)) {
                        angelica$savePin(version);
                    }
                    return;
                }
                lastException = e;
            }
        }

        angelica$reportContextFailure(lastException);
        if (lastException instanceof LWJGLException lwjgl) {
            throw lwjgl;
        }
        throw new LWJGLException("Failed to create OpenGL core profile context", lastException);
    }

    @Unique
    private static int angelica$clampPinned(int pinned, int platformMax) {
        if (pinned == 0) return 0;
        if (pinned >= 33 && pinned <= platformMax) return pinned;
        final int clamped = Math.max(33, Math.min(pinned, platformMax));
        LOGGER.warn("pinnedGLVersion={} is out of range (33-{}), clamping to {}", pinned, platformMax, clamped);
        return clamped;
    }

    @Unique
    private static Exception angelica$tryCreateES() {
        final PixelFormat esFormat = new PixelFormat().withDepthBits(24).withStencilBits(stencilBits);
        final ContextAttribs esAttribs = new ContextAttribs(3, 2).withProfileES(true).withDebug(AngelicaMod.lwjglDebug);
        try {
            Display.create(esFormat, esAttribs);
            return null;
        } catch (LWJGLException | RuntimeException e) {
            try { Display.destroy(); } catch (Exception ignored) {}
            return e;
        }
    }

    @Unique
    private static Exception angelica$tryCreate(ContextAttribs attribs, PixelFormat format, MethodHandle setMajor, MethodHandle setMinor, int major, int minor) throws RuntimeException {
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

    @Unique
    private static void angelica$savePin(int version) {
        AngelicaConfig.pinnedGLVersion = version;
        try {
            ConfigurationManager.save(AngelicaConfig.class);
            LOGGER.info("Pinned OpenGL version to {}.{} - set pinnedGLVersion=0 to re-probe, or disableGLVersionPinning=true to never pin", version / 10, version % 10);
        } catch (Exception e) {
            LOGGER.warn("Failed to save pinned GL version: {}", e.getMessage());
        }
    }

    @Unique
    private static void angelica$reportContextFailure(Exception e) {
        LOGGER.error("FATAL: Failed to create OpenGL core profile context.");
        LOGGER.error("Error: {}", e != null ? e.getMessage() : "unknown");
        if (Display.isCreated()) {
            try {
                LOGGER.error("GPU: {}, Driver: {}", GLStateManager.glGetString(GL11.GL_RENDERER), GLStateManager.glGetString(GL11.GL_VERSION));
            } catch (Exception ignored) {}
        } else {
            LOGGER.error("GPU info: not available (no GL context)");
        }
    }
}
