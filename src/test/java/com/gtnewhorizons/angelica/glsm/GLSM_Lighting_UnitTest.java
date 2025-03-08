package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyLightState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyMaterialState;
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
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);

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

        // The position is stored in eye coordinates, so we're moving around the modelview matrix before changing them
        // That way we can test that the client side transformation of them via GLSM is working and matches the OpenGL
        // transformation. This doesn't seem like float precision should break this test, but it may be possible for it
        // to need tweaked a bit more.
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(25.0F, 25.0F, 25.0F);
        GLStateManager.glRotatef(35.0F, 10.0F, 15.0F, 20.0F);
        newf4b(0.25F, 0.5F, 0.25F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.1090F, 0.5189F, 0.3062F, 0.0F}, "GL_LIGHT0 Position Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.2824F, 0.4200F, 0.4937F, 0.0F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.1090F, 0.5189F, 0.3062F, 0.0F}, "GL_LIGHT0 attrib pop state check");
        GLStateManager.glPopMatrix();

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

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(25.0F, 25.0F, 25.0F);
        GLStateManager.glRotatef(35.0F, 10.0F, 15.0F, 20.0F);
        newf4b(0.25F, 0.5F, 0.25F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.1090F, 0.5189F, 0.3062F}, "GL_LIGHT0 Spot Direction Changed State");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.3F, 0.4F, 0.5F, 0.0F);
        GLStateManager.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, f4b);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.2824F, 0.4200F, 0.4937F}, "GL_LIGHT0 attrib push state changed check");
        GLStateManager.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.1090F, 0.5189F, 0.3062F}, "GL_LIGHT0 attrib pop state check");
        GLStateManager.glPopMatrix();

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

        GLStateManager.glPopAttrib();
    }

    @Test
    void testLightModel() {
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "GL_LIGHT_MODEL_AMBIENT initial state");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, f4b);
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT,  new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "GL_LIGHT_MODEL_AMBIENT changed state");
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F});

        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        GLStateManager.glPopAttrib();
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR);

        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightModelf(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 0.8F);
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, true);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);

        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightModeli(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1);
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, true);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);

        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightModelf(GL11.GL_LIGHT_MODEL_TWO_SIDE, 0.8F);
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, true);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);

        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, 1);
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, true);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
    }

    @Test
    void testColorMaterial() {
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GLStateManager.glColor4f(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT);
        GLStateManager.glEnable(GL11.GL_COLOR_MATERIAL);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glDisable(GL11.GL_COLOR_MATERIAL);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT_AND_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT_AND_DIFFUSE);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLStateManager.glColorMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_SPECULAR);
        GLStateManager.glColorMaterial(GL11.GL_BACK, GL11.GL_DIFFUSE);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_DIFFUSE);
        GLStateManager.glPopAttrib();
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT_AND_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    @Test
    void testMaterial() {
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, f4b);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT, f4b);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE, f4b);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glMaterial(GL11.GL_BACK, GL11.GL_SPECULAR, f4b);
        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.2F);
        GLStateManager.glMaterial(GL11.GL_BACK, GL11.GL_EMISSION, f4b);
        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{0.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLStateManager.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 5.5F);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{5.5F}, "");
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{0.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.0F, 1.0F, 1.0F}, "");
        GLStateManager.glPushAttrib(GL11.GL_LIGHTING_BIT);
        newf4b(0.8F, 0.6F, 0.4F, 0.0F);
        GLStateManager.glMaterial(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, f4b);
        if (GLStateManager.isNVIDIA()) {
            verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.8F, 0.4F, 0.6F}, "");
        } else {
            verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.8F, 0.6F, 0.4F}, "");
        }
        GLStateManager.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.0F, 1.0F, 1.0F}, "");

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
