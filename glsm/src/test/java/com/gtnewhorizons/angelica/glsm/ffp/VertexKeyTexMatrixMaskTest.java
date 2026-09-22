package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class VertexKeyTexMatrixMaskTest {

    private static boolean texMatEnabled(int unit) {
        return VertexKey.fromState(false, false, false, false, 0).unitTexMatEnabled(unit);
    }

    @Test
    void identityNonIdentityTransitionsPerUnit() {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean texGenS = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_S);
        final boolean texGenT = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_T);
        final boolean texGenR = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_R);
        final boolean texGenQ = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_GEN_Q);
        if (texGenS) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_S);
        if (texGenT) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_T);
        if (texGenR) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_R);
        if (texGenQ) GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_Q);
        try {
            for (int unit = 0; unit < VertexKey.MAX_UNITS; unit++) {
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                GLStateManager.glLoadIdentity();
                assertFalse(texMatEnabled(unit), "unit " + unit + " identity");

                GLStateManager.glTranslatef(1.0f, 2.0f, 3.0f);
                assertTrue(texMatEnabled(unit), "unit " + unit + " non-identity");

                GLStateManager.glLoadIdentity();
                assertFalse(texMatEnabled(unit), "unit " + unit + " back to identity");
            }
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            if (texGenS) GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_S);
            if (texGenT) GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_T);
            if (texGenR) GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_R);
            if (texGenQ) GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_Q);
        }
    }

    @Test
    void pushPopMatrixRestoresIdentity() {
        final int unit = 1;
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(4.0f, 5.0f, 6.0f);
        assertTrue(texMatEnabled(unit));

        GLStateManager.glPopMatrix();
        assertFalse(texMatEnabled(unit), "pop must restore identity and invalidate the cached mask");
    }

    @Test
    void transformBitPopBumpsGeneration() {
        final int unit = 2;
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        GLStateManager.glPushAttrib(GL11.GL_TRANSFORM_BIT);
        try {
            GLStateManager.glTranslatef(7.0f, 8.0f, 9.0f);
            assertTrue(texMatEnabled(unit));
        } finally {
            GLStateManager.glPopAttrib();
        }
        assertTrue(texMatEnabled(unit), "GL_TRANSFORM_BIT pop does not restore matrix content; the pop bump must still force a correct recompute");

        GLStateManager.glLoadIdentity();
        assertFalse(texMatEnabled(unit));
    }

    @Test
    void generationUnchangedSkipsRecompute() {
        final int unit = 3;
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        final int genBefore = GLStateManager.getTexMatrixGeneration();
        GLStateManager.ctx().textures.getTextureUnitMatrix(unit).translate(10.0f, 11.0f, 12.0f);
        assertEquals(genBefore, GLStateManager.getTexMatrixGeneration(), "direct mutation of the matrix object must not bump the generation");
        assertFalse(texMatEnabled(unit), "cached mask must stay stale (identity) while the generation is unchanged");

        GLStateManager.glLoadIdentity();
        assertFalse(texMatEnabled(unit));
    }
}
