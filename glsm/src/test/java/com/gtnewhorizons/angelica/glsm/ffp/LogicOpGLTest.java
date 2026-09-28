package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.glLogicOp(GL11.GL_COPY);
        GLStateManager.glDisable(GL11.GL_BLEND);
        LogicOpFixture.deleteResources();
        FfpFixture.resetFfpState();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
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
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void blendingIsIgnoredWhileLogicOpIsEnabled() {
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
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
