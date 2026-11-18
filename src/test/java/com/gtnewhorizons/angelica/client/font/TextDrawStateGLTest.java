package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class TextDrawStateGLTest {

    @AfterEach
    void resetState() {
        setDefaults();
    }

    private static void setDefaults() {
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMRasterizer.glFrontFace(GL11.GL_CCW);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(0.0f, 0.0f);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
    }

    private static void setNonDefaults() {
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_GEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMBlend.glColorMask(false, false, false, false);
        GLSMRasterizer.enableCull();
        GLSMRasterizer.glCullFace(GL11.GL_FRONT);
        GLSMRasterizer.glFrontFace(GL11.GL_CW);
        GLSMEnable.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMRasterizer.glPolygonOffset(-10.0f, -10.0f);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.5f);
    }

    private static TextDrawState capture() {
        final TextDrawState state = new TextDrawState();
        state.captureLive(GLStateManager.ctx());
        return state;
    }

    private static List<Field> stateFields() {
        final List<Field> fields = new ArrayList<>();
        for (Field field : TextDrawState.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    private static boolean isGlState(Field field) {
        return !field.getName().equals("alphaRef");
    }

    @Test
    void everyFieldIsMovedByTheTestState() throws IllegalAccessException {
        setDefaults();
        final TextDrawState defaults = capture();
        setNonDefaults();
        final TextDrawState moved = capture();
        for (Field field : stateFields()) {
            assertNotEquals(field.get(defaults), field.get(moved), field.getName() + " - add it to setNonDefaults()");
        }
    }

    @Test
    void sameAsAndSetCoverEveryField() throws IllegalAccessException {
        setNonDefaults();
        final TextDrawState moved = capture();
        setDefaults();
        final TextDrawState defaults = capture();
        for (Field field : stateFields()) {
            final TextDrawState copy = new TextDrawState();
            copy.set(moved);
            assertEquals(field.get(moved), field.get(copy), field.getName() + " - missing from set()");
            assertTrue(copy.sameAs(moved));
            field.set(copy, field.get(defaults));
            assertFalse(copy.sameAs(moved), field.getName() + " - missing from sameAs()");
        }
    }

    @Test
    void applyReplaysCapturedState() throws IllegalAccessException {
        setNonDefaults();
        final TextDrawState captured = capture();
        setDefaults();
        captured.apply();
        final TextDrawState replayed = capture();
        for (Field field : stateFields()) {
            if (!isGlState(field)) continue;
            assertEquals(field.get(captured), field.get(replayed), field.getName());
        }
        assertEquals(GL11.GL_GEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");
        assertEquals(GL11.GL_FRONT, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face");
        assertEquals(GL11.GL_CW, GL11.glGetInteger(GL11.GL_FRONT_FACE), "driver front face");
        assertEquals(-10.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), "driver polygon offset factor");
        assertTrue(GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver polygon offset fill");
    }

    static Stream<Arguments> fontStateSets() {
        return Stream.of(Arguments.of("FONT", StateSet.FONT), Arguments.of("FONT_PIPELINE", StateSet.FONT_PIPELINE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fontStateSets")
    void fontStateSetRestoresEverythingApplyTouches(String name, StateSet set) throws IllegalAccessException {
        setNonDefaults();
        final TextDrawState moved = capture();
        setDefaults();
        final TextDrawState before = capture();

        final int depth = GLSMAttribStack.pushState(set);
        moved.apply();
        GLSMAttribStack.popStateTo(depth);

        final ByteBuffer driverColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, driverColorMask);
        assertFalse(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "driver depth test");
        assertEquals(GL11.GL_LESS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "driver depth func");
        assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "driver depth mask");
        for (int i = 0; i < 4; i++) assertNotEquals(0, driverColorMask.get(i), "driver color mask channel " + i);
        assertFalse(GL11.glIsEnabled(GL11.GL_CULL_FACE), "driver cull face enable");
        assertEquals(GL11.GL_BACK, GL11.glGetInteger(GL11.GL_CULL_FACE_MODE), "driver cull face mode");
        assertEquals(GL11.GL_CCW, GL11.glGetInteger(GL11.GL_FRONT_FACE), "driver front face");
        assertFalse(GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), "driver polygon offset fill");
        assertEquals(0.0f, GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR), "driver polygon offset factor");

        final TextDrawState after = capture();
        for (Field field : stateFields()) {
            if (!isGlState(field)) continue;
            assertEquals(field.get(before), field.get(after), field.getName() + " - not restored by the font state set");
        }
    }
}
