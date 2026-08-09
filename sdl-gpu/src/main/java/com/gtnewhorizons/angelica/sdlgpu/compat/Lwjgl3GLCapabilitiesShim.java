package com.gtnewhorizons.angelica.sdlgpu.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.FunctionProvider;

import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.function.IntFunction;

/** {@link GLCapabilities} shim for Lwjgl3ify-Aware mods */
public final class Lwjgl3GLCapabilitiesShim {

    private static final Logger LOG = LogManager.getLogger("SDLGPU");

    private static final long SENTINEL_ADDRESS = 0xDEAD_0000_0000_0001L;

    private Lwjgl3GLCapabilitiesShim() {}

    public static void installOnCurrentThread(Set<String> advertised) {
        GL.setCapabilities(create(advertised));
        LOG.info("Installed fabricated LWJGL3 GLCapabilities on {} ({} capability names advertised)", Thread.currentThread().getName(), advertised.size());
    }

    public static void clearOnCurrentThread() {
        GL.setCapabilities(null);
        LOG.info("Cleared fabricated LWJGL3 GLCapabilities on {}", Thread.currentThread().getName());
    }

    public static GLCapabilities create(Set<String> advertised) {
        final FunctionProvider provider = name -> SENTINEL_ADDRESS;
        final IntFunction<PointerBuffer> bufferFactory = BufferUtils::createPointerBuffer;
        try {
            final Constructor<GLCapabilities> ctor = GLCapabilities.class.getDeclaredConstructor(FunctionProvider.class, Set.class, boolean.class, IntFunction.class);
            ctor.setAccessible(true);
            return ctor.newInstance(provider, advertised, true, bufferFactory);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GLCapabilities(FunctionProvider, Set, boolean, IntFunction) is gone", e);
        }
    }
}
