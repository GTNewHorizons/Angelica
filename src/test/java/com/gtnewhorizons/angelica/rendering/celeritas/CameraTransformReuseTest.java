package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CameraTransformReuseTest {

    private static CameraTransform lookup(CeleritasWorldRenderer renderer, double x, double y, double z) {
        return Reflect.invoke(renderer, "cameraTransform", new Class<?>[] { double.class, double.class, double.class }, x, y, z);
    }

    @Test
    void identicalCoordinatesReuseImmutableUpstreamTransform() {
        final CeleritasWorldRenderer renderer = Reflect.allocate(CeleritasWorldRenderer.class);
        final CameraTransform original = lookup(renderer, 1475.7192986248613, 65.625, -935.8161276616005);
        assertSame(original, lookup(renderer, original.x, original.y, original.z));
        final CameraTransform movedX = lookup(renderer, original.x + 0.125, original.y, original.z);
        final CameraTransform movedY = lookup(renderer, movedX.x, movedX.y + 0.125, movedX.z);
        final CameraTransform movedZ = lookup(renderer, movedY.x, movedY.y, movedY.z + 0.125);
        assertNotSame(original, movedX);
        assertNotSame(movedX, movedY);
        assertNotSame(movedY, movedZ);
        assertEquals(1475.7192986248613, original.x);
        assertEquals(65.625, original.y);
        assertEquals(-935.8161276616005, original.z);
        assertSame(movedZ, lookup(renderer, movedZ.x, movedZ.y, movedZ.z));
        final CameraTransform expected = new CameraTransform(movedZ.x, movedZ.y, movedZ.z);
        assertEquals(expected.intX, movedZ.intX);
        assertEquals(expected.intY, movedZ.intY);
        assertEquals(expected.intZ, movedZ.intZ);
        assertEquals(expected.fracX, movedZ.fracX);
        assertEquals(expected.fracY, movedZ.fracY);
        assertEquals(expected.fracZ, movedZ.fracZ);
    }
}
