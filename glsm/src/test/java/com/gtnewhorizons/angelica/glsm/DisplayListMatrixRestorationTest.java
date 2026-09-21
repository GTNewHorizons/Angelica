package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.support.DisplayListTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import java.nio.FloatBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayListMatrixRestorationTest extends DisplayListTestFixture {
    @Test
    void flushedScalesPreserveNegativeComponentsInEveryMatrixTarget() {
        for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            for (int target : new int[] { GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE }) {
                resetMatrices();
                int list = newList();
                GLStateManager.glNewList(list, compileMode);
                GLStateManager.glMatrixMode(target);
                GLStateManager.glScalef(-2, 3, -4);
                GLStateManager.glEndList();
                resetMatrices();
                GLStateManager.glCallList(list);
                FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
                GLStateManager.glGetFloat(switch (target) {
                    case GL11.GL_PROJECTION -> GL11.GL_PROJECTION_MATRIX;
                    case GL11.GL_TEXTURE -> GL11.GL_TEXTURE_MATRIX;
                    default -> GL11.GL_MODELVIEW_MATRIX;
                }, matrix);
                assertEquals(-2f, matrix.get(0));
                assertEquals(3f, matrix.get(5));
                assertEquals(-4f, matrix.get(10));
            }
        }
    }

    @BeforeEach
    @AfterEach
    void resetMatrices() {
        for (int unit = 0; unit < 2; unit++) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glLoadIdentity();
        }
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
    }

    private static float translation(int target) {
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        GLStateManager.glGetFloat(target, b);
        return b.get(12);
    }

    @Test
    void transformAttributesSaveAndRestoreLazyMode() {
        for (int mask : new int[] { GL11.GL_TRANSFORM_BIT, GL11.GL_ALL_ATTRIB_BITS }) {
            for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                int list = newList();
                GLStateManager.glNewList(list, compileMode);
                GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                GLStateManager.glPushAttrib(mask);
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glLoadIdentity();
                GLStateManager.glTranslatef(3, 0, 0);
                GLStateManager.glPopAttrib();
                GLStateManager.glLoadIdentity();
                GLStateManager.glEndList();

                GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                GLStateManager.glLoadIdentity();
                GLStateManager.glTranslatef(9, 0, 0);
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glLoadIdentity();
                GLStateManager.glTranslatef(5, 0, 0);
                GLStateManager.glCallList(list);
                assertEquals(3f, translation(GL11.GL_MODELVIEW_MATRIX));
                assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
            }
        }
    }

    @Test
    void nestedAttributesRestoreInheritedCallerMode() {
        int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPopAttrib();
        GLStateManager.glLoadIdentity();
        GLStateManager.glPopAttrib();
        GLStateManager.glLoadIdentity();
        GLStateManager.glEndList();

        GLStateManager.glTranslatef(5, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glTranslatef(7, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(9, 0, 0);
        GLStateManager.glCallList(list);
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
        assertEquals(0f, translation(GL11.GL_PROJECTION_MATRIX));
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
        assertEquals(GL11.GL_TEXTURE, GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE));
    }

    @Test
    void implicitMatrixCommandsAndEmptyListsInheritCallerTarget() {
        int empty = newList();
        GLStateManager.glNewList(empty, GL11.GL_COMPILE);
        GLStateManager.glEndList();
        int load = newList();
        GLStateManager.glNewList(load, GL11.GL_COMPILE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glEndList();
        GLStateManager.glTranslatef(5, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(9, 0, 0);
        GLStateManager.glCallList(empty);
        assertEquals(GL11.GL_TEXTURE, GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE));
        assertEquals(9f, translation(GL11.GL_TEXTURE_MATRIX));
        GLStateManager.glCallList(load);
        assertEquals(GL11.GL_TEXTURE, GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE));
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
    }

    @Test
    void explicitInitialModelviewSurvivesReplayFromTexture() {
        int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glEndList();
        GLStateManager.glTranslatef(5, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(9, 0, 0);
        GLStateManager.glCallList(list);
        assertEquals(0f, translation(GL11.GL_MODELVIEW_MATRIX));
        assertEquals(9f, translation(GL11.GL_TEXTURE_MATRIX));
    }

    @ParameterizedTest
    @ValueSource(ints = { GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE })
    void transformsBeforeExplicitModeInheritCallerTarget(int explicitTarget) {
        for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            resetMatrices();
            int list = newList();
            GLStateManager.glNewList(list, compileMode);
            GLStateManager.glTranslatef(5, 0, 0);
            GLStateManager.glMatrixMode(explicitTarget);
            GLStateManager.glTranslatef(7, 0, 0);
            GLStateManager.glEndList();

            for (int callerTarget : new int[] { GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE }) {
                resetMatrices();
                GLStateManager.glMatrixMode(callerTarget);
                GLStateManager.glCallList(list);
                String context = "compileMode=" + compileMode + ", callerTarget=" + callerTarget;
                assertEquals((callerTarget == GL11.GL_MODELVIEW ? 5f : 0f) + (explicitTarget == GL11.GL_MODELVIEW ? 7f : 0f),
                    translation(GL11.GL_MODELVIEW_MATRIX), context);
                assertEquals((callerTarget == GL11.GL_PROJECTION ? 5f : 0f) + (explicitTarget == GL11.GL_PROJECTION ? 7f : 0f),
                    translation(GL11.GL_PROJECTION_MATRIX), context);
                assertEquals((callerTarget == GL11.GL_TEXTURE ? 5f : 0f) + (explicitTarget == GL11.GL_TEXTURE ? 7f : 0f),
                    translation(GL11.GL_TEXTURE_MATRIX), context);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { GL11.GL_TEXTURE_BIT, GL11.GL_TEXTURE_BIT | GL11.GL_TRANSFORM_BIT, GL11.GL_ALL_ATTRIB_BITS })
    void textureAttributesFlushTransformsBeforeRestoringUnit(int mask) {
        for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            resetMatrices();
            int list = newList();
            GLStateManager.glNewList(list, compileMode);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glPushAttrib(mask);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glTranslatef(7, 0, 0);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glTranslatef(3, 0, 0);
            GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLStateManager.glPopAttrib();
            GLStateManager.glPopAttrib();
            GLStateManager.glEndList();

            resetMatrices();
            GLStateManager.glCallList(list);
            String context = "compileMode=" + compileMode;
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE), context);
            assertEquals(3f, translation(GL11.GL_MODELVIEW_MATRIX), context);
            assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX), context);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(7f, translation(GL11.GL_TEXTURE_MATRIX), context);
        }
    }

    @Test
    void pendingTextureTransformStaysOnOriginalUnit() {
        int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(7, 0, 0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glEndList();
        GLStateManager.glCallList(list);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        assertEquals(7f, translation(GL11.GL_TEXTURE_MATRIX));
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
    }

    @Test
    void explicitModeAfterNestedListIsNotDeduplicated() {
        int child = newList();
        GLStateManager.glNewList(child, GL11.GL_COMPILE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glTranslatef(5, 0, 0);
        GLStateManager.glEndList();
        int parent = newList();
        GLStateManager.glNewList(parent, GL11.GL_COMPILE);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glCallList(child);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glEndList();
        GLStateManager.glCallList(parent);
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
    }
}
