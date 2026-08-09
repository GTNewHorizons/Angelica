package net.coderbot.iris.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvBuiltinScanTest {

    @Test
    void detectsVariableUsage() {
        String frag = "#version 120\nvoid main() { vec4 p = gl_ModelViewMatrix * vec4(1.0); gl_FragColor = p; }";
        assertTrue(DeferredWorldRenderingPipeline.referencesMvBuiltins(frag));
    }

    @Test
    void detectsFtransformCall() {
        String vert = "#version 120\nvoid main() { gl_Position = ftransform(); }";
        assertTrue(DeferredWorldRenderingPipeline.referencesMvBuiltins(vert));
    }

    @Test
    void ignoresCommentsAndUnrelated() {
        String frag = "#version 120\n// gl_ModelViewMatrix in a comment only\nvoid main() { gl_FragColor = vec4(1.0); }";
        assertFalse(DeferredWorldRenderingPipeline.referencesMvBuiltins(frag));
    }

    @Test
    void nullSourceIsClean() {
        assertFalse(DeferredWorldRenderingPipeline.referencesMvBuiltins(null));
    }
}
