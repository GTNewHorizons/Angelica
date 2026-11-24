package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import org.lwjgl.opengl.GL11;

/// Discards fragments that would not change the target under the draw's blend, so the depth [DepthEcho] keeps for a
/// glow covers only what the glow shows and transparent texels do not take a [DepthPeel] peel. A fragment it drops
/// changes the draw's own color by less than one 8-bit step.
public final class CoverageDiscard {

    private static final int OFF = 0;
    private static final int ALPHA = 1;
    private static final int RGB = 2;
    private static final int RGB_ALPHA = 3;

    public static final String GLSL = "#define COVER_OFF " + OFF + "\n"
        + "#define COVER_RGB " + RGB + "\n"
        + "#define COVER_RGB_ALPHA " + RGB_ALPHA + "\n"
        + "#define COVER_MIN 0.004\n"
        + """
        uniform int u_CoverMode;
        void coverageTest(vec4 c) {
            if (u_CoverMode == COVER_OFF) return;
            float rgb = max(c.r, max(c.g, c.b));
            float coverage = u_CoverMode == COVER_RGB ? rgb : u_CoverMode == COVER_RGB_ALPHA ? rgb * c.a : c.a;
            if (coverage < COVER_MIN) discard;
        }
        """;

    private CoverageDiscard() {}

    public static boolean active() {
        return GLSMConfig.depthPeel || DepthEcho.discardsCoverage();
    }

    public static int mode() {
        if (!GLSMBlend.getBlendMode().isEnabled()) return OFF;
        final BlendStateStack blend = GLStateManager.ctx().blendState;
        if (blend.getDstRgb() != GL11.GL_ONE) return ALPHA;
        return blend.getSrcRgb() == GL11.GL_SRC_ALPHA ? RGB_ALPHA : RGB;
    }
}
