package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyLightState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

@ExtendWith(AngelicaExtension.class)
public class GLSM_Lighting_UnitTest {

    static final FloatBuffer f4b = ByteBuffer.allocateDirect(4 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
    static final FloatBuffer f3b = ByteBuffer.allocateDirect(3 << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();

    @Test
    void testLightEnablement() {
        verifyState(GL11.GL_LIGHT0, false, "GL_LIGHT0 Initial State");
        GLStateManager.glEnable(GL11.GL_LIGHT0);
        verifyState(GL11.GL_LIGHT0, true, "GL_LIGHT0 Enabled");

        verifyState(GL11.GL_LIGHT1, false, "GL_LIGHT1 Initial State");
        GLStateManager.glEnable(GL11.GL_LIGHT1);
        verifyState(GL11.GL_LIGHT1, true, "GL_LIGHT1 Enabled State");

        GLStateManager.glDisable(GL11.GL_LIGHT0);
        GLStateManager.glDisable(GL11.GL_LIGHT1);
    }

    @Test
    void testLightValues() {
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT0 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Specular");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT0 Initial Position");

        verifyLightState(GL11.GL_LIGHT1, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT1 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_SPECULAR, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Specular");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT1 Initial Position");

        newf4b(0.25F, 0.5F, 0.25F, 0.5F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Ambient Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.6F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        // TODO: Need to test position with a non identity modelview matrix because it's stored in eye coordinates
        newf4b(0.25F, 0.5F, 0.25F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.25F, 0.5F, 0.25F, 0.0F}, "GL_LIGHT0 Position Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.3F, 0.4F, 0.5F, 0.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.25F, 0.5F, 0.25F, 0.0F}, "GL_LIGHT0 attrib pop state check");


        newf4b(0.25F, 0.5F, 0.25F, 0.5F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Diffuse Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.6F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        newf4b(0.25F, 0.5F, 0.25F, 0.5F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Specular Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.6F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        newf4b(0.25F, 0.5F, 0.25F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.25F, 0.5F, 0.25F}, "GL_LIGHT0 Spot Direction Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.3F, 0.4F, 0.5F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.25F, 0.5F, 0.25F}, "GL_LIGHT0 attrib pop state check");

        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 1.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{1.0F}, "GL_LIGHT0 Spot Exponent Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 2.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{2.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{1.0F}, "GL_LIGHT0 attrib pop state check");

        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{20.0F}, "GL_LIGHT0 Spot Cutoff Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 40.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{40.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{20.0F}, "GL_LIGHT0 attrib pop state check");

        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Constant Attenuation Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");

        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Linear Attenuation Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");

        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Quadratic Attenuation Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightf(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");
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
