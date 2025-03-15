package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntStack;
import lombok.Getter;
import org.lwjgl.opengl.GL11;


public class GLAttribManager {

    public static final int MAX_ATTRIB_STACK_DEPTH = GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH);
    public static final IntStack attribs = new IntArrayList(MAX_ATTRIB_STACK_DEPTH);
    @Getter protected static boolean poppingAttributes;

    public static void glPushAttrib(int mask) {
        GLStateManager.pushState(mask);
        GL11.glPushAttrib(mask);
    }

    public static void glPopAttrib() {
        poppingAttributes = true;
        GLStateManager.popState();
        GL11.glPopAttrib();
        poppingAttributes = false;
    }
}
