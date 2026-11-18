package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class LogicOpGLTest {

    private static final int SIZE = 8;

    @BeforeEach
    void setUp() {
        FfpFixture.resetFfpState();
        FfpFixture.activateFfp();
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
    }

    @AfterEach
    void tearDown() {
        GLSMEnable.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLSMBlend.glLogicOp(GL11.GL_COPY);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        LogicOpFixture.deleteResources();
        FfpFixture.resetFfpState();
    }

    @Test
    void everyOpMatchesTheBitwiseReference() {
        for (int op : LogicOpFixture.OPS) {
            for (int[] src : LogicOpFixture.SOURCES) {
                LogicOpFixture.clearToDst();
                LogicOpFixture.drawQuad(op, src);
                assertRgb(op, src);
            }
        }
    }

    @Test
    void blendingIsIgnoredWhileLogicOpIsEnabled() {
        GLSMEnable.glEnable(GL11.GL_BLEND);
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        final int[] src = LogicOpFixture.SOURCES[1];
        LogicOpFixture.clearToDst();
        LogicOpFixture.drawQuad(GL11.GL_XOR, src);
        assertRgb(GL11.GL_XOR, src);
    }

    private static void assertRgb(int op, int[] src) {
        final int[] px = FfpFixture.readPixel(SIZE / 2, SIZE / 2);
        final String label = LogicOpFixture.label(op, src);
        assertEquals(LogicOpFixture.expected(op, src[0], LogicOpFixture.DST_R), px[0], () -> label + " red");
        assertEquals(LogicOpFixture.expected(op, src[1], LogicOpFixture.DST_G), px[1], () -> label + " green");
        assertEquals(LogicOpFixture.expected(op, src[2], LogicOpFixture.DST_B), px[2], () -> label + " blue");
    }
}
