package com.gtnewhorizons.angelica.glsm;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(GLSMCoreExtension.class)
public class GLSM_DirtyTracking_UnitTest {

    static final FloatBuffer f4b = ByteBuffer.allocateDirect(4 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
    static final FloatBuffer f3b = ByteBuffer.allocateDirect(3 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

    @Test
    void materialBumpsOnlyOnChange() {
        newf4b(0.1F, 0.2F, 0.3F, 0.4F);
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, f4b);
        final int gen = GLStateManager.lightingGeneration;

        newf4b(0.1F, 0.2F, 0.3F, 0.4F);
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, f4b);
        assertEquals(gen, GLStateManager.lightingGeneration, "identical material must not bump lightingGeneration");

        newf4b(0.9F, 0.2F, 0.3F, 0.4F);
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, f4b);
        assertEquals(gen + 1, GLStateManager.lightingGeneration, "changed material must bump lightingGeneration once");
    }

    @Test
    void lightModelBumpsOnlyOnChange() {
        newf4b(0.5F, 0.5F, 0.5F, 1.0F);
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, f4b);
        final int gen = GLStateManager.lightingGeneration;

        newf4b(0.5F, 0.5F, 0.5F, 1.0F);
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, f4b);
        assertEquals(gen, GLStateManager.lightingGeneration, "identical light model ambient must not bump");
    }

    @Test
    void lightColorBumpsOnlyOnChange() {
        newf4b(0.25F, 0.5F, 0.75F, 1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, f4b);
        final int gen = GLStateManager.lightingGeneration;

        newf4b(0.25F, 0.5F, 0.75F, 1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, f4b);
        assertEquals(gen, GLStateManager.lightingGeneration, "identical light diffuse must not bump");
    }

    @Test
    void lightPositionDirtySkipsUnderSameModelview() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);

        newf4b(0.1F, 0.2F, 0.3F, 1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        final int gen = GLStateManager.lightingGeneration;

        newf4b(0.1F, 0.2F, 0.3F, 1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        assertEquals(gen, GLStateManager.lightingGeneration, "same position under same modelview must not bump");

        GLStateManager.glTranslatef(5.0F, 0.0F, 0.0F);
        newf4b(0.1F, 0.2F, 0.3F, 1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        assertEquals(gen + 1, GLStateManager.lightingGeneration, "same raw position under changed modelview must bump");

        GLStateManager.glPopMatrix();
    }

    @Test
    void translationDoesNotBumpLinearGeneration() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();

        final int mv = GLStateManager.mvGeneration;
        final int lin = GLStateManager.mvLinearGeneration;

        GLStateManager.glTranslatef(1.0F, 2.0F, 3.0F);
        assertEquals(mv + 1, GLStateManager.mvGeneration, "translate must bump mvGeneration");
        assertEquals(lin, GLStateManager.mvLinearGeneration, "translate must NOT bump mvLinearGeneration");

        GLStateManager.glRotatef(30.0F, 0.0F, 1.0F, 0.0F);
        assertEquals(mv + 2, GLStateManager.mvGeneration, "rotate must bump mvGeneration");
        assertEquals(lin + 1, GLStateManager.mvLinearGeneration, "rotate must bump mvLinearGeneration");

        GLStateManager.glScalef(2.0F, 2.0F, 2.0F);
        assertEquals(lin + 2, GLStateManager.mvLinearGeneration, "scale must bump mvLinearGeneration");

        GLStateManager.glPopMatrix();
    }

    @Test
    void fogBumpsOnlyOnChange() {
        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        final int gen = GLStateManager.fragmentGeneration;

        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.5F);
        assertEquals(gen, GLStateManager.fragmentGeneration, "identical fog density must not bump fragmentGeneration");

        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.7F);
        assertEquals(gen + 1, GLStateManager.fragmentGeneration, "changed fog density must bump fragmentGeneration");
    }

    @Test
    void spotDirectionDirtySkipsUnderTranslationOnly() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();

        newf3b(0.0F, 0.0F, -1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f3b);
        final int gen = GLStateManager.lightingGeneration;

        GLStateManager.glTranslatef(5.0F, 0.0F, 0.0F);
        newf3b(0.0F, 0.0F, -1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f3b);
        assertEquals(gen, GLStateManager.lightingGeneration, "spot direction under translation-only must not bump");

        GLStateManager.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
        newf3b(0.0F, 0.0F, -1.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f3b);
        assertEquals(gen + 1, GLStateManager.lightingGeneration, "same raw spot direction under changed 3x3 must bump");

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

    static void newf4b(float x, float y, float z, float w) {
        f4b.clear();
        f4b.put(x).put(y).put(z).put(w);
        f4b.flip();
    }

    static void newf3b(float x, float y, float z) {
        f3b.clear();
        f3b.put(x).put(y).put(z);
        f3b.flip();
    }
}
