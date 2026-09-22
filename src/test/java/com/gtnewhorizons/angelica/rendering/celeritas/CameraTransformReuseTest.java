package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CameraTransformReuseTest {
    @Test
    void identicalCoordinatesReuseImmutableUpstreamTransform() throws ReflectiveOperationException {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        final Object unsafe = singleton.get(null);
        final CeleritasWorldRenderer renderer = (CeleritasWorldRenderer) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafe, CeleritasWorldRenderer.class);
        final Method lookup = CeleritasWorldRenderer.class.getDeclaredMethod("cameraTransform", double.class, double.class, double.class);
        lookup.setAccessible(true);
        final CameraTransform original = (CameraTransform) lookup.invoke(renderer, 1475.7192986248613, 65.625, -935.8161276616005);
        assertSame(original, lookup.invoke(renderer, original.x, original.y, original.z));
        final CameraTransform movedX = (CameraTransform) lookup.invoke(renderer, original.x + 0.125, original.y, original.z);
        final CameraTransform movedY = (CameraTransform) lookup.invoke(renderer, movedX.x, movedX.y + 0.125, movedX.z);
        final CameraTransform movedZ = (CameraTransform) lookup.invoke(renderer, movedY.x, movedY.y, movedY.z + 0.125);
        assertNotSame(original, movedX);
        assertNotSame(movedX, movedY);
        assertNotSame(movedY, movedZ);
        assertEquals(1475.7192986248613, original.x);
        assertEquals(65.625, original.y);
        assertEquals(-935.8161276616005, original.z);
        assertSame(movedZ, lookup.invoke(renderer, movedZ.x, movedZ.y, movedZ.z));
        final CameraTransform expected = new CameraTransform(movedZ.x, movedZ.y, movedZ.z);
        assertEquals(expected.intX, movedZ.intX);
        assertEquals(expected.intY, movedZ.intY);
        assertEquals(expected.intZ, movedZ.intZ);
        assertEquals(expected.fracX, movedZ.fracX);
        assertEquals(expected.fracY, movedZ.fracY);
        assertEquals(expected.fracZ, movedZ.fracZ);
    }
}
