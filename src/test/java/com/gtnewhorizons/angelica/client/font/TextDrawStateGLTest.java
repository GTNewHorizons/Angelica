package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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
        GLStateManager.disableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LESS);
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.disableCull();
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glFrontFace(GL11.GL_CCW);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(0.0f, 0.0f);
        GLStateManager.glAlphaFunc(GL11.GL_ALWAYS, 0.0f);
    }

    private static void setNonDefaults() {
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_GEQUAL);
        GLStateManager.glDepthMask(false);
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.enableCull();
        GLStateManager.glCullFace(GL11.GL_FRONT);
        GLStateManager.glFrontFace(GL11.GL_CW);
        GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glPolygonOffset(-10.0f, -10.0f);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.5f);
    }

    private static TextDrawState capture() {
        final TextDrawState state = new TextDrawState();
        state.captureLive();
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

    @Test
    void fontStateSetRestoresEverythingApplyTouches() throws IllegalAccessException {
        assertApplyDoesNotLeak(StateSet.FONT);
    }

    @Test
    void fontPipelineStateSetRestoresEverythingApplyTouches() throws IllegalAccessException {
        assertApplyDoesNotLeak(StateSet.FONT_PIPELINE);
    }

    private static void assertApplyDoesNotLeak(StateSet set) throws IllegalAccessException {
        setNonDefaults();
        final TextDrawState moved = capture();
        setDefaults();
        final TextDrawState before = capture();

        final int depth = GLStateManager.pushState(set);
        moved.apply();
        GLStateManager.popStateTo(depth);

        final TextDrawState after = capture();
        for (Field field : stateFields()) {
            if (!isGlState(field)) continue;
            assertEquals(field.get(before), field.get(after), field.getName() + " - not restored by the font state set");
        }
    }
}
