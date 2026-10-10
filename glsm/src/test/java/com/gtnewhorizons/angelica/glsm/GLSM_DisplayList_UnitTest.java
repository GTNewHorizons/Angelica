package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCompatTest
class GLSM_DisplayList_UnitTest {

    /** One recordable state command: {@code apply} moves the driver value read by {@code driver} to {@code applied}. */
    private static Arguments state(String name, Runnable apply, Runnable reset, DoubleSupplier driver, double applied) {
        return Arguments.of(name, apply, reset, driver, applied);
    }

    private static double lighting() {
        final boolean enabled = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
        verifyState(GL11.GL_LIGHTING, enabled, "driver must agree with the GLSM cache");
        return enabled ? 1.0 : 0.0;
    }

    private static double blendColorRed() {
        final FloatBuffer color = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL14.GL_BLEND_COLOR, color);
        return color.get(0);
    }

    static Stream<Arguments> states() {
        return Stream.of(
            state("glEnable(GL_LIGHTING)", () -> GLStateManager.glEnable(GL11.GL_LIGHTING), () -> GLStateManager.glDisable(GL11.GL_LIGHTING),
                GLSM_DisplayList_UnitTest::lighting, 1.0),
            state("glCullFace", () -> GLStateManager.glCullFace(GL11.GL_FRONT), () -> GLStateManager.glCullFace(GL11.GL_BACK),
                () -> GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), GL11.GL_FRONT),
            state("glLogicOp", () -> GLStateManager.glLogicOp(GL11.GL_XOR), () -> GLStateManager.glLogicOp(GL11.GL_COPY),
                () -> GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), GL11.GL_XOR),
            state("glClearDepth", () -> GLStateManager.glClearDepth(0.5), () -> GLStateManager.glClearDepth(1.0),
                () -> GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE), 0.5),
            state("glBlendColor", () -> GLStateManager.glBlendColor(0.5f, 0.4f, 0.6f, 0.8f), () -> GLStateManager.glBlendColor(0f, 0f, 0f, 0f),
                GLSM_DisplayList_UnitTest::blendColorRed, 0.5),
            state("glFrontFace", () -> GLStateManager.glFrontFace(GL11.GL_CW), () -> GLStateManager.glFrontFace(GL11.GL_CCW),
                () -> GL11.glGetInteger(GL11.GL_FRONT_FACE), GL11.GL_CW),
            state("glHint", () -> GLStateManager.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST), () -> GLStateManager.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_DONT_CARE),
                () -> GL11.glGetInteger(GL11.GL_LINE_SMOOTH_HINT), GL11.GL_NICEST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void compiledStateChangeAppliesOnlyWhenTheListIsCalled(String name, Runnable apply, Runnable reset, DoubleSupplier driver, double applied) {
        final double initial = driver.getAsDouble();
        final int list = GL11.glGenLists(1);
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            apply.run();
            GLStateManager.glEndList();
            assertEquals(initial, driver.getAsDouble(), 0.0001, name + " must not execute under GL_COMPILE");

            GLStateManager.glCallList(list);
            assertEquals(applied, driver.getAsDouble(), 0.0001, name + " must apply when the list is called");
        } finally {
            reset.run();
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void compileAndExecuteAppliesStateChangeImmediately(String name, Runnable apply, Runnable reset, DoubleSupplier driver, double applied) {
        final int list = GL11.glGenLists(1);
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
            apply.run();
            GLStateManager.glEndList();
            assertEquals(applied, driver.getAsDouble(), 0.0001, name + " must apply under GL_COMPILE_AND_EXECUTE");
        } finally {
            reset.run();
            GLStateManager.glDeleteLists(list, 1);
        }
    }

    @Test
    void testMultipleStateChangesInDisplayList() {
        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.glLogicOp(GL11.GL_AND);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        GLStateManager.glCallList(list);

        assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "CullFace should be GL_FRONT");
        assertEquals(GL11.GL_AND, GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE), "LogicOp should be GL_AND");
        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING should be enabled");

        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glLogicOp(GL11.GL_COPY);
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glDeleteLists(list, 1);
    }
}
