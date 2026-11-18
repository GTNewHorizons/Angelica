package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
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
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        final boolean texGenS = GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_GEN_S);
        final boolean texGenT = GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_GEN_T);
        final boolean texGenR = GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_GEN_R);
        final boolean texGenQ = GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_GEN_Q);
        if (texGenS) GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_S);
        if (texGenT) GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_T);
        if (texGenR) GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_R);
        if (texGenQ) GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_Q);
        try {
            for (int unit = 0; unit < VertexKey.MAX_UNITS; unit++) {
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
                GLSMMatrices.glLoadIdentity();
                assertFalse(texMatEnabled(unit), "unit " + unit + " identity");

                GLSMMatrices.glTranslatef(1.0f, 2.0f, 3.0f);
                assertTrue(texMatEnabled(unit), "unit " + unit + " non-identity");

                GLSMMatrices.glLoadIdentity();
                assertFalse(texMatEnabled(unit), "unit " + unit + " back to identity");
            }
        } finally {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            if (texGenS) GLSMEnable.glEnable(GL11.GL_TEXTURE_GEN_S);
            if (texGenT) GLSMEnable.glEnable(GL11.GL_TEXTURE_GEN_T);
            if (texGenR) GLSMEnable.glEnable(GL11.GL_TEXTURE_GEN_R);
            if (texGenQ) GLSMEnable.glEnable(GL11.GL_TEXTURE_GEN_Q);
        }
    }

    @Test
    void pushPopMatrixRestoresIdentity() {
        final int unit = 1;
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslatef(4.0f, 5.0f, 6.0f);
        assertTrue(texMatEnabled(unit));

        GLSMMatrices.glPopMatrix();
        assertFalse(texMatEnabled(unit), "pop must restore identity and invalidate the cached mask");
    }

    @Test
    void transformBitPopBumpsGeneration() {
        final int unit = 2;
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        GLSMAttribStack.glPushAttrib(GL11.GL_TRANSFORM_BIT);
        try {
            GLSMMatrices.glTranslatef(7.0f, 8.0f, 9.0f);
            assertTrue(texMatEnabled(unit));
        } finally {
            GLSMAttribStack.glPopAttrib();
        }
        assertTrue(texMatEnabled(unit), "GL_TRANSFORM_BIT pop does not restore matrix content; the pop bump must still force a correct recompute");

        GLSMMatrices.glLoadIdentity();
        assertFalse(texMatEnabled(unit));
    }

    @Test
    void generationUnchangedSkipsRecompute() {
        final int unit = 3;
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        assertFalse(texMatEnabled(unit));

        final int genBefore = GLSMMatrices.getTexMatrixGeneration();
        GLStateManager.ctx().textures.getTextureUnitMatrix(unit).translate(10.0f, 11.0f, 12.0f);
        assertEquals(genBefore, GLSMMatrices.getTexMatrixGeneration(), "direct mutation of the matrix object must not bump the generation");
        assertFalse(texMatEnabled(unit), "cached mask must stay stale (identity) while the generation is unchanged");

        GLSMMatrices.glLoadIdentity();
        assertFalse(texMatEnabled(unit));
    }
}
