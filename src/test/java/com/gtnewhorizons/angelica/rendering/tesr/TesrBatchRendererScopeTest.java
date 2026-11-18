package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TesrBatchRendererScopeTest {

    private final TesrBatchRenderer renderer = TesrBatchRenderer.INSTANCE;

    private long current() {
        return Reflect.get(renderer, "currentScope");
    }

    @Test
    void nestedBeginKeepsOuterIdOutermostEndReturnsToZeroAndIdsAreUnique() {
        renderer.beginScope();
        final long outer = current();
        assertNotEquals(0L, outer);
        renderer.beginScope();
        assertEquals(outer, current());
        renderer.endScope();
        assertEquals(outer, current());
        renderer.endScope();
        assertEquals(0L, current());
        renderer.beginScope();
        final long second = current();
        renderer.endScope();
        assertNotEquals(outer, second);
    }
}
