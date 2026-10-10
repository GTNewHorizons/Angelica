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
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT | GL11.GL_FOG_BIT | GL11.GL_CURRENT_BIT);
    }

    @AfterEach
    void popTrackedState() {
        GLStateManager.glPopAttrib();
    }

    @Test
    void materialBumpsOnlyOnChange() {
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.1F, 0.2F, 0.3F, 0.4F));
        final int gen = GLStateManager.getLightingGeneration();

        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.1F, 0.2F, 0.3F, 0.4F));
        assertEquals(gen, GLStateManager.getLightingGeneration(), "identical material must not bump lightingGeneration");

        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.9F, 0.2F, 0.3F, 0.4F));
        assertEquals(gen + 1, GLStateManager.getLightingGeneration(), "changed material must bump lightingGeneration once");
    }

    @Test
    void lightModelBumpsOnlyOnChange() {
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.5F, 0.5F, 0.5F, 1.0F));
        final int gen = GLStateManager.getLightingGeneration();

        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.5F, 0.5F, 0.5F, 1.0F));
        assertEquals(gen, GLStateManager.getLightingGeneration(), "identical light model ambient must not bump");
    }

    @Test
    void lightColorBumpsOnlyOnChange() {
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.25F, 0.5F, 0.75F, 1.0F));
        final int gen = GLStateManager.getLightingGeneration();

        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.25F, 0.5F, 0.75F, 1.0F));
        assertEquals(gen, GLStateManager.getLightingGeneration(), "identical light diffuse must not bump");
    }

    @Test
    void lightPositionDirtySkipsUnderSameModelview() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);

        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        final int gen = GLStateManager.getLightingGeneration();

        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        assertEquals(gen, GLStateManager.getLightingGeneration(), "same position under same modelview must not bump");

        GLStateManager.glTranslatef(5.0F, 0.0F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.1F, 0.2F, 0.3F, 1.0F));
        assertEquals(gen + 1, GLStateManager.getLightingGeneration(), "same raw position under changed modelview must bump");

        GLStateManager.glPopMatrix();
    }

    @Test
    void translationDoesNotBumpLinearGeneration() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();

        final int mv = GLStateManager.getMvGeneration();
        final int lin = GLStateManager.getMvLinearGeneration();

        GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);
        assertEquals(mv + 1, GLStateManager.getMvGeneration(), "translate must bump mvGeneration");
        assertEquals(lin, GLStateManager.getMvLinearGeneration(), "translate must NOT bump mvLinearGeneration");

        GLStateManager.glRotatef(30.0F, 0.0F, 1.0F, 0.0F);
        assertEquals(mv + 2, GLStateManager.getMvGeneration(), "rotate must bump mvGeneration");
        assertEquals(lin + 1, GLStateManager.getMvLinearGeneration(), "rotate must bump mvLinearGeneration");

        GLStateManager.glScalef(2.0F, 2.0F, 2.0F);
        assertEquals(lin + 2, GLStateManager.getMvLinearGeneration(), "scale must bump mvLinearGeneration");

        GLStateManager.glPopMatrix();
    }

    @Test
    void fogBumpsOnlyOnChange() {
        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        final int gen = GLStateManager.getFragmentGeneration();

        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        assertEquals(gen, GLStateManager.getFragmentGeneration(), "identical fog density must not bump fragmentGeneration");

        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.7F);
        assertEquals(gen + 1, GLStateManager.getFragmentGeneration(), "changed fog density must bump fragmentGeneration");
    }

    @Test
    void spotDirectionDirtySkipsUnderTranslationOnly() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();

        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        final int gen = GLStateManager.getLightingGeneration();

        GLStateManager.glTranslatef(5.0F, 0.0F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        assertEquals(gen, GLStateManager.getLightingGeneration(), "spot direction under translation-only must not bump");

        GLStateManager.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.0F, 0.0F, -1.0F));
        assertEquals(gen + 1, GLStateManager.getLightingGeneration(), "same raw spot direction under changed 3x3 must bump");

        GLStateManager.glPopMatrix();
    }

    @Test
    void colorMaterialFreezesLastColorOnDisable() {
        GLStateManager.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLStateManager.glColor4f(1.0F, 0.0F, 0.0F, 1.0F); // enable-time color
        GLStateManager.enableColorMaterial();
        GLStateManager.glColor4f(0.0F, 0.0F, 1.0F, 1.0F); // last color while enabled
        GLStateManager.disableColorMaterial();

        final Vector4f diffuse = GLStateManager.getFrontMaterial().diffuse;
        assertEquals(0.0F, diffuse.x, "diffuse must freeze at last color (blue), not enable-time red");
        assertEquals(0.0F, diffuse.y, "green channel must be 0");
        assertEquals(1.0F, diffuse.z, "blue channel must be 1");
    }
}
