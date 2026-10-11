package com.gtnewhorizons.angelica.sdlgpu.compat;

import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lwjgl3GLCapabilitiesShimTest {

    private static boolean flag(GLCapabilities caps, String name) throws ReflectiveOperationException {
        final Field f = GLCapabilities.class.getField(name);
        return (boolean) f.get(caps);
    }

    @Test
    void constructsFromTheAdvertisedSet() {
        final GLCapabilities caps = Lwjgl3GLCapabilitiesShim.create(SDLGPURenderBackend.advertisedCapabilities());
        assertTrue(caps.forwardCompatible, "SDL reports Core Profile Equivalent, so forwardCompatible must be true");
    }

    @Test
    void advertises46() throws ReflectiveOperationException {
        final GLCapabilities caps = Lwjgl3GLCapabilitiesShim.create(SDLGPURenderBackend.advertisedCapabilities());
        assertTrue(flag(caps, "OpenGL46"), "getString(GL_VERSION) and GL_MAJOR/MINOR_VERSION already claim 4.6; the capability view must agree");
    }

    @Test
    void everyAdvertisedExtensionFlagIsTrue() throws ReflectiveOperationException {
        final Set<String> advertised = SDLGPURenderBackend.advertisedCapabilities();
        final GLCapabilities caps = Lwjgl3GLCapabilitiesShim.create(advertised);

        final Set<String> missing = new TreeSet<>();
        for (String name : advertised) {
            if (name.startsWith("GL_") && !flag(caps, name)) missing.add(name);
        }
        assertTrue(missing.isEmpty(), () -> "advertised but reported unsupported: " + missing);
    }

    @Test
    void unadvertisedExtensionIsFalse() throws ReflectiveOperationException {
        final Set<String> advertised = SDLGPURenderBackend.advertisedCapabilities();
        assertFalse(advertised.contains("GL_ARB_bindless_texture"), "test fixture assumption");
        final GLCapabilities caps = Lwjgl3GLCapabilitiesShim.create(advertised);
        assertFalse(flag(caps, "GL_ARB_bindless_texture"), "capabilities must not be fabricated wholesale");
    }

    @Test
    void advertisedSetIsTheOnlyVersionSource() throws ReflectiveOperationException {
        final Set<String> advertised = SDLGPURenderBackend.advertisedCapabilities();
        final GLCapabilities caps = Lwjgl3GLCapabilitiesShim.create(advertised);

        final Set<String> fromSet = new TreeSet<>();
        for (String name : advertised) if (name.startsWith("OpenGL")) fromSet.add(name);

        final Set<String> fromCaps = new TreeSet<>();
        for (Field f : GLCapabilities.class.getFields()) {
            if (f.getName().startsWith("OpenGL") && f.getType() == boolean.class && (boolean) f.get(caps)) {
                fromCaps.add(f.getName());
            }
        }
        assertEquals(fromSet, fromCaps);
    }

    @Test
    void installIsPerThreadAndClearable() throws Exception {
        Lwjgl3GLCapabilitiesShim.installOnCurrentThread(SDLGPURenderBackend.advertisedCapabilities());
        try {
            assertNotNull(GL.getCapabilities());

            TestThreads.run("no-context-thread", () -> assertThrows(IllegalStateException.class, GL::getCapabilities,
                "a thread that does not own the context must not look like the render thread"));
        } finally {
            Lwjgl3GLCapabilitiesShim.clearOnCurrentThread();
        }
        assertThrows(IllegalStateException.class, GL::getCapabilities, "clearing on release must stop the thread answering yes to runningOnRenderThread()");
    }

    @Test
    void advertisedSetHasNoDuplicatesOrBlanks() {
        final Set<String> advertised = SDLGPURenderBackend.advertisedCapabilities();
        assertEquals(new HashSet<>(advertised).size(), advertised.size());
        for (String name : advertised) {
            assertTrue(name.startsWith("OpenGL") || name.startsWith("GL_"), () -> "malformed capability name: " + name);
        }
    }
}
