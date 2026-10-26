package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.EntityIdsOnlyHandler;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@GLCompatTest
class TessellatorEntityIdsGLTest {

    private static final int[] GREEN = { 0, 255, 0, 255 };
    private static final int[] BLUE = { 0, 0, 255, 255 };

    private ImmediateExtendedAttribHandler previousHandler;
    private int program;

    @BeforeEach
    void setUp() {
        previousHandler = EntityIdsOnlyHandler.install();
        program = GlTestSupport.program(TessellatorEntityIdsFixture.VERTEX, TessellatorEntityIdsFixture.FRAGMENT);
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glViewport(0, 0, 8, 8);
        GLSMPrograms.glUseProgram(program);
        GLSMBlend.glClearColor(0, 0, 0, 1);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    @AfterEach
    void tearDown() {
        GLSMPrograms.glUseProgram(0);
        GLSMPrograms.glDeleteProgram(program);
        GLSMAttribStack.glPopAttrib();
        GLSMHooks.immediateExtendedHandler = previousHandler;
    }

    @Test
    void idsChangedMidBatchApplyToTheVerticesAddedAfterTheChange() {
        TessellatorEntityIdsFixture.drawIdsChangedMidBatch();

        assertColor(1, GREEN, "left quad was added under item 7");
        assertColor(6, BLUE, "right quad was added under item 50020");
    }

    @Test
    void idsChangedAfterTheLastVertexLeaveTheBatchAlone() {
        TessellatorEntityIdsFixture.drawIdsChangedAfterTheLastVertex();

        assertColor(1, GREEN, "every vertex was added under item 7");
        assertColor(6, GREEN, "every vertex was added under item 7");
    }

    private static void assertColor(int x, int[] expected, String message) {
        final int[] pixel = FfpFixture.readPixel(x, 4);
        assertArrayEquals(expected, pixel, message + ", got " + Arrays.toString(pixel));
    }
}
