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
                GLSMDraws.glNewList(list, compileMode);
                GLSMMatrices.glMatrixMode(target);
                GLSMMatrices.glScalef(-2, 3, -4);
                GLSMDraws.glEndList();
                resetMatrices();
                GLSMDraws.glCallList(list);
                FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
                GLSMQueries.glGetFloat(switch (target) {
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
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMMatrices.glLoadIdentity();
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
    }

    private static float translation(int target) {
        return GlTestSupport.matrix(target).get(12);
    }

    @Test
    void transformAttributesSaveAndRestoreLazyMode() {
        for (int mask : new int[] { GL11.GL_TRANSFORM_BIT, GL11.GL_ALL_ATTRIB_BITS }) {
            for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
                int list = newList();
                GLSMDraws.glNewList(list, compileMode);
                GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
                GLSMAttribStack.glPushAttrib(mask);
                GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
                GLSMMatrices.glLoadIdentity();
                GLSMMatrices.glTranslatef(3, 0, 0);
                GLSMAttribStack.glPopAttrib();
                GLSMMatrices.glLoadIdentity();
                GLSMDraws.glEndList();

                GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
                GLSMMatrices.glLoadIdentity();
                GLSMMatrices.glTranslatef(9, 0, 0);
                GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
                GLSMMatrices.glLoadIdentity();
                GLSMMatrices.glTranslatef(5, 0, 0);
                GLSMDraws.glCallList(list);
                assertEquals(3f, translation(GL11.GL_MODELVIEW_MATRIX));
                assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
            }
        }
    }

    @Test
    void nestedAttributesRestoreInheritedCallerMode() {
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMAttribStack.glPushAttrib(GL11.GL_TRANSFORM_BIT);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMAttribStack.glPopAttrib();
        GLSMMatrices.glLoadIdentity();
        GLSMAttribStack.glPopAttrib();
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glEndList();

        GLSMMatrices.glTranslatef(5, 0, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glTranslatef(7, 0, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(9, 0, 0);
        GLSMDraws.glCallList(list);
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
        assertEquals(0f, translation(GL11.GL_PROJECTION_MATRIX));
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
        assertEquals(GL11.GL_TEXTURE, GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE));
    }

    @Test
    void implicitMatrixCommandsAndEmptyListsInheritCallerTarget() {
        int empty = newList();
        GLSMDraws.glNewList(empty, GL11.GL_COMPILE);
        GLSMDraws.glEndList();
        int load = newList();
        GLSMDraws.glNewList(load, GL11.GL_COMPILE);
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glEndList();
        GLSMMatrices.glTranslatef(5, 0, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(9, 0, 0);
        GLSMDraws.glCallList(empty);
        assertEquals(GL11.GL_TEXTURE, GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE));
        assertEquals(9f, translation(GL11.GL_TEXTURE_MATRIX));
        GLSMDraws.glCallList(load);
        assertEquals(GL11.GL_TEXTURE, GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE));
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
    }

    @Test
    void explicitInitialModelviewSurvivesReplayFromTexture() {
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glEndList();
        GLSMMatrices.glTranslatef(5, 0, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(9, 0, 0);
        GLSMDraws.glCallList(list);
        assertEquals(0f, translation(GL11.GL_MODELVIEW_MATRIX));
        assertEquals(9f, translation(GL11.GL_TEXTURE_MATRIX));
    }

    @ParameterizedTest
    @ValueSource(ints = { GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE })
    void transformsBeforeExplicitModeInheritCallerTarget(int explicitTarget) {
        for (int compileMode : new int[] { GL11.GL_COMPILE, GL11.GL_COMPILE_AND_EXECUTE }) {
            resetMatrices();
            int list = newList();
            GLSMDraws.glNewList(list, compileMode);
            GLSMMatrices.glTranslatef(5, 0, 0);
            GLSMMatrices.glMatrixMode(explicitTarget);
            GLSMMatrices.glTranslatef(7, 0, 0);
            GLSMDraws.glEndList();

            for (int callerTarget : new int[] { GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE }) {
                resetMatrices();
                GLSMMatrices.glMatrixMode(callerTarget);
                GLSMDraws.glCallList(list);
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
            GLSMDraws.glNewList(list, compileMode);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMAttribStack.glPushAttrib(mask);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMMatrices.glTranslatef(7, 0, 0);
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            GLSMMatrices.glTranslatef(3, 0, 0);
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMAttribStack.glPopAttrib();
            GLSMAttribStack.glPopAttrib();
            GLSMDraws.glEndList();

            resetMatrices();
            GLSMDraws.glCallList(list);
            String context = "compileMode=" + compileMode;
            assertEquals(GL13.GL_TEXTURE0, GLSMQueries.glGetInteger(GL13.GL_ACTIVE_TEXTURE), context);
            assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), context);
            assertEquals(3f, translation(GL11.GL_MODELVIEW_MATRIX), context);
            assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX), context);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(7f, translation(GL11.GL_TEXTURE_MATRIX), context);
        }
    }

    @Test
    void pendingTextureTransformStaysOnOriginalUnit() {
        int list = newList();
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glTranslatef(7, 0, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMDraws.glEndList();
        GLSMDraws.glCallList(list);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        assertEquals(7f, translation(GL11.GL_TEXTURE_MATRIX));
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        assertEquals(0f, translation(GL11.GL_TEXTURE_MATRIX));
    }

    @Test
    void explicitModeAfterNestedListIsNotDeduplicated() {
        int child = newList();
        GLSMDraws.glNewList(child, GL11.GL_COMPILE);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glTranslatef(5, 0, 0);
        GLSMDraws.glEndList();
        int parent = newList();
        GLSMDraws.glNewList(parent, GL11.GL_COMPILE);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glCallList(child);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMDraws.glEndList();
        GLSMDraws.glCallList(parent);
        assertEquals(5f, translation(GL11.GL_MODELVIEW_MATRIX));
    }
}
