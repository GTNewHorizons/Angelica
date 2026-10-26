package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.gtnewhorizons.angelica.glsm.GlTestSupport.floats;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyLightState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyMaterialState;
import static com.gtnewhorizons.angelica.util.GLSMUtil.verifyState;

@GLCompatTest
public class GLSM_Lighting_UnitTest {

    @Test
    void testLightEnablement() {
        verifyState(GL11.GL_LIGHT0, false, "GL_LIGHT0 Initial State");
        GLSMEnable.glEnable(GL11.GL_LIGHT0);
        verifyState(GL11.GL_LIGHT0, true, "GL_LIGHT0 Enabled");

        verifyState(GL11.GL_LIGHT1, false, "GL_LIGHT1 Initial State");
        GLSMEnable.glEnable(GL11.GL_LIGHT1);
        verifyState(GL11.GL_LIGHT1, true, "GL_LIGHT1 Enabled State");

        GLSMEnable.glDisable(GL11.GL_LIGHT0);
        GLSMEnable.glDisable(GL11.GL_LIGHT1);
    }

    @Test
    void testLightValues() {
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);

        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT0 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Specular");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT0 Initial Position");

        verifyLightState(GL11.GL_LIGHT1, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT1 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_SPECULAR, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Specular");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT1 Initial Position");

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, floats(0.25F, 0.5F, 0.25F, 0.5F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Ambient Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, floats(0.3F, 0.4F, 0.5F, 0.6F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        // The position is stored in eye coordinates, so we're moving around the modelview matrix before changing them
        // That way we can test that the client side transformation of them via GLSM is working and matches the OpenGL
        // transformation. This doesn't seem like float precision should break this test, but it may be possible for it
        // to need tweaked a bit more.
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslatef(25.0F, 25.0F, 25.0F);
        GLSMMatrices.glRotatef(35.0F, 10.0F, 15.0F, 20.0F);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.25F, 0.5F, 0.25F, 0.0F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.1090F, 0.5189F, 0.3062F, 0.0F}, "GL_LIGHT0 Position Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, floats(0.3F, 0.4F, 0.5F, 0.0F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.2824F, 0.4200F, 0.4937F, 0.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0.1090F, 0.5189F, 0.3062F, 0.0F}, "GL_LIGHT0 attrib pop state check");
        GLSMMatrices.glPopMatrix();

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.25F, 0.5F, 0.25F, 0.5F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Diffuse Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, floats(0.3F, 0.4F, 0.5F, 0.6F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, floats(0.25F, 0.5F, 0.25F, 0.5F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 Specular Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, floats(0.3F, 0.4F, 0.5F, 0.6F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.3F, 0.4F, 0.5F, 0.6F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{0.25F, 0.5F, 0.25F, 0.5F}, "GL_LIGHT0 attrib pop state check");

        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glTranslatef(25.0F, 25.0F, 25.0F);
        GLSMMatrices.glRotatef(35.0F, 10.0F, 15.0F, 20.0F);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.25F, 0.5F, 0.25F, 0.0F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.1090F, 0.5189F, 0.3062F}, "GL_LIGHT0 Spot Direction Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, floats(0.3F, 0.4F, 0.5F, 0.0F));
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.2824F, 0.4200F, 0.4937F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_DIRECTION, new float[]{0.1090F, 0.5189F, 0.3062F}, "GL_LIGHT0 attrib pop state check");
        GLSMMatrices.glPopMatrix();

        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 1.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{1.0F}, "GL_LIGHT0 Spot Exponent Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, 2.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{2.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_EXPONENT, new float[]{1.0F}, "GL_LIGHT0 attrib pop state check");

        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{20.0F}, "GL_LIGHT0 Spot Cutoff Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, 40.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{40.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPOT_CUTOFF, new float[]{20.0F}, "GL_LIGHT0 attrib pop state check");

        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Constant Attenuation Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_CONSTANT_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");

        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Linear Attenuation Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_LINEAR_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");

        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, 10.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 Quadratic Attenuation Changed State");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightf(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, 20.0F);
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{20.0F}, "GL_LIGHT0 attrib push state changed check");
        GLSMAttribStack.glPopAttrib();
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_QUADRATIC_ATTENUATION, new float[]{10.0F}, "GL_LIGHT0 attrib pop state check");

        GLSMAttribStack.glPopAttrib();
    }

    @Test
    void testLightModel() {
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "GL_LIGHT_MODEL_AMBIENT initial state");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT,  new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "GL_LIGHT_MODEL_AMBIENT changed state");
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F});

        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR);

        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModelf(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 0.8F);
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, true);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);

        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModeli(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, 1);
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, true);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER, false);

        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModelf(GL11.GL_LIGHT_MODEL_TWO_SIDE, 0.8F);
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, true);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);

        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, 1);
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, true);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_LIGHT_MODEL_TWO_SIDE, false);
    }

    @Test
    void testColorMaterial() {
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GLSMCurrentAttribs.glColor4f(0.8F, 0.6F, 0.4F, 0.2F);
        GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT);
        GLSMEnable.glEnable(GL11.GL_COLOR_MATERIAL);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMEnable.glDisable(GL11.GL_COLOR_MATERIAL);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_COLOR_MATERIAL, false);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT_AND_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT_AND_DIFFUSE);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLSMLighting.glColorMaterial(GL11.GL_FRONT, GL11.GL_SPECULAR);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_SPECULAR);
        GLSMLighting.glColorMaterial(GL11.GL_BACK, GL11.GL_DIFFUSE);
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_DIFFUSE);
        GLSMAttribStack.glPopAttrib();
        verifyState(GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_FRONT_AND_BACK);
        verifyState(GL11.GL_COLOR_MATERIAL_PARAMETER, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    @Test
    void testMaterial() {
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_AMBIENT, new float[]{0.2F, 0.2F, 0.2F, 1.0F}, "");
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");
        verifyMaterialState(GL11.GL_BACK, GL11.GL_DIFFUSE, new float[]{0.8F, 0.8F, 0.8F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_BACK, GL11.GL_SPECULAR, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_BACK, GL11.GL_SPECULAR, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");

        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_BACK, GL11.GL_EMISSION, floats(0.8F, 0.6F, 0.4F, 0.2F));
        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.8F, 0.6F, 0.4F, 0.2F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_BACK, GL11.GL_EMISSION, new float[]{0.0F, 0.0F, 0.0F, 1.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{0.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterialf(GL11.GL_FRONT, GL11.GL_SHININESS, 5.5F);
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{5.5F}, "");
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_SHININESS, new float[]{0.0F}, "");

        verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.0F, 1.0F, 1.0F}, "");
        GLSMAttribStack.glPushAttrib(GL11.GL_LIGHTING_BIT);
        GLSMLighting.glMaterial(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, floats(0.8F, 0.6F, 0.4F, 0.0F));
        if (GLStateManager.vendorIsNVIDIA()) {
            verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.8F, 0.4F, 0.6F}, "");
        } else {
            verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.8F, 0.6F, 0.4F}, "");
        }
        GLSMAttribStack.glPopAttrib();
        verifyMaterialState(GL11.GL_FRONT, GL11.GL_COLOR_INDEXES, new float[]{0.0F, 1.0F, 1.0F}, "");

    }
}
