package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import static com.gtnewhorizons.angelica.util.GLSMUtil.*;

@ExtendWith(AngelicaExtension.class)
class GLSM_DisplayList_UnitTest {

    @Test
    void testDisplayListCompileCall() {
        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Initial State");

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Post Display List Compile");

        GLStateManager.glCallList(list);

        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING Post Display List Call");

        // Reset state that we changed during test
        GLStateManager.glDisable(GL11.GL_LIGHTING);
    }

    @Test
    void testDisplayListCompileExecute() {
        verifyState(GL11.GL_LIGHTING, false, "GL_LIGHTING Initial State");

        int list = GL11.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glEnable(GL11.GL_LIGHTING);
        GLStateManager.glEndList();

        verifyState(GL11.GL_LIGHTING, true, "GL_LIGHTING Post Display List Compile and Execute");

        // Reset state that we changed during test
        GLStateManager.glDisable(GL11.GL_LIGHTING);
    }
}
