package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.StencilStateStack;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;

@SuppressWarnings("unused") // Called via ASM
public class GLStencilManager {

    @Getter
    protected static final StencilStateStack stencilState = new StencilStateStack();
    @Getter protected static final BooleanStateStack stencilTest = new BooleanStateStack(GL11.GL_STENCIL_TEST);

    public static void enableStencilTest() {
        stencilTest.enable();
    }

    public static void disableStencilTest() {
        stencilTest.disable();
    }

    public static void glStencilFunc(int func, int ref, int mask) {
        stencilState.setFunc(func, ref, mask);
    }

    public static void glStencilOp(int sfail, int dpfail, int dppass) {
        stencilState.setOp(sfail, dpfail, dppass);
    }

    public static void glStencilMask(int mask) {
        stencilState.setMask(mask);
    }

    public static void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        GL33C.glStencilFuncSeparate(face, func, ref, mask);
    }

    public static void glStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        GL33C.glStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    public static void glStencilMaskSeparate(int face, int mask) {
        GL33C.glStencilMaskSeparate(face, mask);
    }
}
