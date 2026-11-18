package com.gtnewhorizons.angelica.glsm;

import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.floats;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_DirtyTracking_UnitTest {

    @BeforeEach
    void pushTrackedState() {
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT | GL11.GL_FOG_BIT | GL11.GL_CURRENT_BIT);
    }

    @AfterEach
    void popTrackedState() {
        GLSMAttribStack.glPopAttrib();
    }

    @Test
    void materialBumpsOnlyOnChange() {
        GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.1F, 0.2F, 0.3F, 0.4F));
        final int gen = GLSMLighting.getLightingGeneration();

        GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.1F, 0.2F, 0.3F, 0.4F));
        assertEquals(gen, GLSMLighting.getLightingGeneration(), "identical material must not bump lightingGeneration");

        GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.9F, 0.2F, 0.3F, 0.4F));
        assertEquals(gen + 1, GLSMLighting.getLightingGeneration(), "changed material must bump lightingGeneration once");
    }

    @Test
    void lightModelBumpsOnlyOnChange() {
        GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.5F, 0.5F, 0.5F, 1.0F));
        final int gen = GLSMLighting.getLightingGeneration();

        GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.5F, 0.5F, 0.5F, 1.0F));
        assertEquals(gen, GLSMLighting.getLightingGeneration(), "identical light model ambient must not bump");
    }

    @Test
    void lightColorBumpsOnlyOnChange() {
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.25F, 0.5F, 0.75F, 1.0F));
        final int gen = GLSMLighting.getLightingGeneration();

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.25F, 0.5F, 0.75F, 1.0F));
        assertEquals(gen, GLSMLighting.getLightingGeneration(), "identical light diffuse must not bump");
    }

    @Test
    void lightPositionDirtySkipsUnderSameModelview() {
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glTranslatef(1.0F, 2.0F, 3.0F);

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        final int gen = GLSMLighting.getLightingGeneration();

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        assertEquals(gen, GLSMLighting.getLightingGeneration(), "same position under same modelview must not bump");

        GLSMMatrices.glTranslatef(5.0F, 0.0F, 0.0F);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        assertEquals(gen + 1, GLSMLighting.getLightingGeneration(), "same raw position under changed modelview must bump");

        GLSMMatrices.glPopMatrix();
    }

    @Test
    void translationDoesNotBumpLinearGeneration() {
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();

        final int mv = GLSMMatrices.getMvGeneration();
        final int lin = GLSMMatrices.getMvLinearGeneration();

        GLSMMatrices.glTranslatef(1.0F, 2.0F, 3.0F);
        assertEquals(mv + 1, GLSMMatrices.getMvGeneration(), "translate must bump mvGeneration");
        assertEquals(lin, GLSMMatrices.getMvLinearGeneration(), "translate must NOT bump mvLinearGeneration");

        GLSMMatrices.glRotatef(30.0F, 0.0F, 1.0F, 0.0F);
        assertEquals(mv + 2, GLSMMatrices.getMvGeneration(), "rotate must bump mvGeneration");
        assertEquals(lin + 1, GLSMMatrices.getMvLinearGeneration(), "rotate must bump mvLinearGeneration");

        GLSMMatrices.glScalef(2.0F, 2.0F, 2.0F);
        assertEquals(lin + 2, GLSMMatrices.getMvLinearGeneration(), "scale must bump mvLinearGeneration");

        GLSMMatrices.glPopMatrix();
    }

    @Test
    void fogBumpsOnlyOnChange() {
        GLSMFog.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        final int gen = GLStateManager.getFragmentGeneration();

        GLSMFog.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        assertEquals(gen, GLStateManager.getFragmentGeneration(), "identical fog density must not bump fragmentGeneration");

        GLSMFog.glFogf(GL11.GL_FOG_DENSITY, 0.7F);
        assertEquals(gen + 1, GLStateManager.getFragmentGeneration(), "changed fog density must bump fragmentGeneration");
    }

    @Test
    void spotDirectionDirtySkipsUnderTranslationOnly() {
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        final int gen = GLSMLighting.getLightingGeneration();

        GLSMMatrices.glTranslatef(5.0F, 0.0F, 0.0F);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        assertEquals(gen, GLSMLighting.getLightingGeneration(), "spot direction under translation-only must not bump");

        GLSMMatrices.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        assertEquals(gen + 1, GLSMLighting.getLightingGeneration(), "same raw spot direction under changed 3x3 must bump");

        GLSMMatrices.glPopMatrix();
    }

    @Test
    void colorMaterialFreezesLastColorOnDisable() {
        GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLSMCurrentAttribs.glColor4f(1.0F, 0.0F, 0.0F, 1.0F); // enable-time color
        GLSMLighting.enableColorMaterial();
        GLSMCurrentAttribs.glColor4f(0.0F, 0.0F, 1.0F, 1.0F); // last color while enabled
        GLSMLighting.disableColorMaterial();

        final Vector4f diffuse = GLSMLighting.getFrontMaterial().diffuse;
        assertEquals(0.0F, diffuse.x, "diffuse must freeze at last color (blue), not enable-time red");
        assertEquals(0.0F, diffuse.y, "green channel must be 0");
        assertEquals(1.0F, diffuse.z, "blue channel must be 1");
    }
}
