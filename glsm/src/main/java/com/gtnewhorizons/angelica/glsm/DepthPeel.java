package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/// Per-draw state for depth peeling. Color passes skip the depth test but keep the draw's depth writes, so the target
/// ends with the nearest peeled depth and the rest pass's own test keeps only what is in front of it. Depth and rest
/// passes discard fragments that would not change the target under the draw's blend, so transparent texels do not take
/// a peel. Draws with an application program cannot run [#GLSL], so they skip the peels and draw unsorted afterwards.
public final class DepthPeel {

    public static final int TEXTURE_UNIT = 15;
    public static final int SCENE_TEXTURE_UNIT = 14;

    private static final int PASS_DEPTH = 0;
    private static final int PASS_COLOR = 1;
    private static final int PASS_REST = 2;
    private static final int UNBOUNDED = 4;
    private static final int COVER_ALPHA = 0;
    private static final int COVER_RGB = 8;
    private static final int COVER_RGB_ALPHA = 16;
    private static final int COVER_ALL = 24;

    public static final String GLSL = "#define PEEL_PASS_COLOR " + PASS_COLOR + "\n"
        + "#define PEEL_UNBOUNDED " + UNBOUNDED + "\n"
        + "#define PEEL_COVER_MASK " + COVER_ALL + "\n"
        + "#define PEEL_COVER_RGB " + COVER_RGB + "\n"
        + "#define PEEL_COVER_RGB_ALPHA " + COVER_RGB_ALPHA + "\n"
        + "#define PEEL_COVER_ALL " + COVER_ALL + "\n"
        + "#define PEEL_SCENE_TOLERANCE 6.0e-8\n"
        + "#define PEEL_WINDOW 2.4e-7\n"
        + "#define PEEL_MIN_COVERAGE 0.004\n"
        + """
        uniform sampler2D u_PeelDepth;
        uniform sampler2D u_PeelScene;
        uniform int u_PeelMode;
        void peelTest(vec4 c) {
            ivec2 texel = ivec2(gl_FragCoord.xy);
            float z = gl_FragCoord.z;
            if (z > texelFetch(u_PeelScene, texel, 0).r + PEEL_SCENE_TOLERANCE) discard;
            float bound = texelFetch(u_PeelDepth, texel, 0).r;
            if ((u_PeelMode & 3) == PEEL_PASS_COLOR) {
                if (z > bound || z < bound - PEEL_WINDOW) discard;
                return;
            }
            if ((u_PeelMode & PEEL_UNBOUNDED) == 0 && z >= bound - PEEL_WINDOW) discard;
            int cover = u_PeelMode & PEEL_COVER_MASK;
            float rgb = max(c.r, max(c.g, c.b));
            float coverage = cover == PEEL_COVER_ALL ? 1.0 : cover == PEEL_COVER_RGB ? rgb : cover == PEEL_COVER_RGB_ALPHA ? rgb * c.a : c.a;
            if (coverage < PEEL_MIN_COVERAGE) discard;
        }
        """;

    public enum Pass { OFF, DEPTH, COLOR, REST, APPLICATION }

    private static Pass pass = Pass.OFF;
    private static boolean unbounded;
    private static boolean overridden;
    private static boolean skippedApplicationDraws;

    private DepthPeel() {}

    public static void setPass(Pass next, boolean unbounded) {
        if (pass == Pass.OFF) skippedApplicationDraws = false;
        pass = next;
        DepthPeel.unbounded = unbounded;
        GLSMConfig.depthPeel = next == Pass.DEPTH || next == Pass.COLOR || next == Pass.REST;
    }

    public static boolean active() {
        return pass != Pass.OFF;
    }

    public static boolean skippedApplicationDraws() {
        return skippedApplicationDraws;
    }

    public static int shaderMode() {
        return switch (pass) {
            case DEPTH -> PASS_DEPTH | coverage() | (unbounded ? UNBOUNDED : 0);
            case COLOR -> PASS_COLOR;
            default -> PASS_REST | coverage();
        };
    }

    private static int coverage() {
        if (!GLSMBlend.getBlendMode().isEnabled()) return COVER_ALL;
        final BlendStateStack blend = GLStateManager.ctx().blendState;
        if (blend.getDstRgb() != GL11.GL_ONE) return COVER_ALPHA;
        return blend.getSrcRgb() == GL11.GL_SRC_ALPHA ? COVER_RGB_ALPHA : COVER_RGB;
    }

    public static boolean begin() {
        if (pass == Pass.OFF) return true;
        final boolean fixedFunction = GLSMPrograms.getActiveProgram() == 0;
        if (pass == Pass.APPLICATION) return !fixedFunction;
        if (!fixedFunction) {
            skippedApplicationDraws = true;
            return false;
        }
        if (pass == Pass.DEPTH) {
            RENDER_BACKEND.colorMask(false, false, false, false);
            RENDER_BACKEND.depthMask(true);
            RENDER_BACKEND.depthFunc(GL11.GL_GREATER);
            overridden = true;
        } else if (pass == Pass.COLOR) {
            RENDER_BACKEND.depthFunc(GL11.GL_ALWAYS);
            overridden = true;
        }
        return true;
    }

    public static void end() {
        if (!overridden) return;
        overridden = false;
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        final DepthStateStack depth = GLSMDepthStencilAlpha.getDepthState();
        RENDER_BACKEND.colorMask(mask.red, mask.green, mask.blue, mask.alpha);
        RENDER_BACKEND.depthMask(depth.isEnabled());
        RENDER_BACKEND.depthFunc(depth.getFunc());
    }
}
