package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_PolygonOffsetClamp_UnitTest {

    @AfterEach
    void reset() {
        GLStateManager.glPolygonOffset(0.0f, 0.0f);
    }

    private static float cachedClamp() {
        return GLStateManager.ctx().polygonState.getOffsetClamp();
    }

    @Test
    void zeroClampTakesThePlainPath() {
        GLStateManager.glPolygonOffsetClamp(1.5f, 2.5f, 0.0f);
        assertEquals(1.5f, GLStateManager.ctx().polygonState.getOffsetFactor());
        assertEquals(2.5f, GLStateManager.ctx().polygonState.getOffsetUnits());
        assertEquals(0.0f, cachedClamp());
    }

    @Test
    void plainOffsetClearsTheCachedClamp() {
        GLStateManager.ctx().polygonState.setOffsetClamp(0.25f);
        GLStateManager.glPolygonOffset(1.0f, 1.0f);
        assertEquals(0.0f, cachedClamp());
    }

    @Test
    void nonZeroClampFailsByNameOnTheGLBackend() {
        final UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> GLStateManager.glPolygonOffsetClamp(1.0f, 1.0f, 0.5f));
        assertTrue(e.getMessage().contains("polygon offset clamp"), e::getMessage);
        assertTrue(e.getMessage().indexOf(':') > 0, () -> "message must name the backend: " + e.getMessage());
    }
}
