package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.util.GLSMUtil.*;

@ExtendWith(AngelicaExtension.class)
public class GLSM_Lighting_UnitTest {

    @Test
    void testLightZeroAndOne() {
        verifyState(GL11.GL_LIGHT0, false, "GL_LIGHT0 Initial State");
        verifyState(GL11.GL_LIGHT1, false, "GL_LIGHT1 Initial State");

        verifyLightState(GL11.GL_LIGHT0, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT0 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_SPECULAR, new float[]{1F, 1F, 1F, 1F}, "GL_LIGHT0 Initial Specular");
        verifyLightState(GL11.GL_LIGHT0, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT0 Initial Position");

        verifyLightState(GL11.GL_LIGHT1, GL11.GL_AMBIENT, new float[]{0F, 0F, 0F, 1F},  "GL_LIGHT1 Initial Ambient");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Diffuse");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_SPECULAR, new float[]{0F, 0F, 0F, 1F}, "GL_LIGHT1 Initial Specular");
        verifyLightState(GL11.GL_LIGHT1, GL11.GL_POSITION, new float[]{0F, 0F, 1F, 0F}, "GL_LIGHT1 Initial Position");

    }
}
