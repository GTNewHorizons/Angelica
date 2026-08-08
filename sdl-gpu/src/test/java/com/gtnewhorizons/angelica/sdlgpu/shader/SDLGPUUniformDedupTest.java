package com.gtnewhorizons.angelica.sdlgpu.shader;


import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDLGPUUniformDedupTest {

    private PipelineApplier applier;
    private ContextState st;
    private ShaderManager.ProgramObject prog;
    private UniformStaging us;

    @BeforeEach
    void setUp() {
        applier = new PipelineApplier(null, null, null, null, null, null, null, null, null);
        st = new ContextState();
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.vertexUboSize = 64;
        prog.fragmentUboSize = 16;
        prog.nextUniformLocation = 3;
        for (int i = 0; i < 3; i++) {
            prog.vertexMemberInfo.put(i, new ShaderManager.UniformMemberInfo(
                i * 16, 16, 0, true, 4, 1, 0, 1));
        }
        prog.fragmentMemberInfo.put(2, new ShaderManager.UniformMemberInfo(0, 16, 0, false, 4, 1, 0, 1));
        prog.buildUniformSlotArrays();
        st.boundProgramObj = prog;
        st.boundProgram = 1;
        us = st.uniformStaging(prog);
    }

    @AfterEach
    void tearDown() {
        st.releaseUniformStaging(prog);
    }

    @Test
    void firstSet_dirtiesAndWritesUbo() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        assertTrue(us.vsUniformDirty);
        assertEquals(1f, us.vsUniformFb.get(0));
        assertEquals(4f, us.vsUniformFb.get(3));
    }

    @Test
    void setSameValueTwice_secondCallDoesNotDirty() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;

        // Caller reuses the same backing array via reuseOrAlloc; writes the same values back.
        final float[] b = applier.reuseOrAlloc(st, 0, 4);
        assertSame(a, b, "reuseOrAlloc should return the cached array");
        b[0] = 1f; b[1] = 2f; b[2] = 3f; b[3] = 4f;
        applier.putUniform(st, 0, b);
        assertFalse(us.vsUniformDirty, "same-value reset must not re-dirty");
    }

    @Test
    void setDifferentValue_dirties() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;

        final float[] b = applier.reuseOrAlloc(st, 0, 4);
        b[0] = 1f; b[1] = 2f; b[2] = 3f; b[3] = 99f;
        applier.putUniform(st, 0, b);
        assertTrue(us.vsUniformDirty);
        assertEquals(99f, us.vsUniformFb.get(3));
    }

    @Test
    void differentSlots_independent() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        final float[] b = applier.reuseOrAlloc(st, 1, 4);
        b[0] = 5f; b[1] = 6f; b[2] = 7f; b[3] = 8f;
        applier.putUniform(st, 1, b);
        // slot 1 lives at byte offset 16 = float index 4
        assertEquals(1f, us.vsUniformFb.get(0));
        assertEquals(5f, us.vsUniformFb.get(4));
        assertEquals(8f, us.vsUniformFb.get(7));
    }

    @Test
    void fragmentSlot_independentDirtyFlag() {
        final float[] a = applier.reuseOrAlloc(st, 2, 4);
        a[0] = 9f; a[1] = 10f; a[2] = 11f; a[3] = 12f;
        applier.putUniform(st, 2, a);
        assertTrue(us.vsUniformDirty, "slot 2 has VS member at offset 32");
        assertTrue(us.fsUniformDirty, "slot 2 also has FS member at offset 0");
        assertEquals(9f, us.vsUniformFb.get(8));
        assertEquals(9f, us.fsUniformFb.get(0));
    }

    @Test
    void outOfRangeLocation_noCrash_noDirty() {
        applier.putUniform(st, 999, new float[]{1f, 2f, 3f, 4f});
        applier.putUniform(st, -1, new float[]{1f, 2f, 3f, 4f});
        assertFalse(us.vsUniformDirty);
        assertFalse(us.fsUniformDirty);
    }

    @Test
    void noProgramBound_reuseOrAllocReturnsFresh_putIsNoOp() {
        st.boundProgramObj = null;
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        assertEquals(4, a.length);
        final float[] b = applier.reuseOrAlloc(st, 0, 4);
        assertNotSame(a, b, "no caching without bound program");
        applier.putUniform(st, 0, a);
    }

    @Test
    void lengthChange_writesThrough() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;
        final float[] c = applier.reuseOrAlloc(st, 0, 2);
        assertNotSame(a, c);
        c[0] = 1f; c[1] = 2f;
        applier.putUniform(st, 0, c);
        assertTrue(us.vsUniformDirty);
    }
}
