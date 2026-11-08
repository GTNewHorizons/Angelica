package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/// Per-draw state for depth peeling. Color passes skip the depth test but keep the draw's depth writes, so the target
/// ends with the nearest peeled depth and the rest pass's own test keeps only what is in front of it. Draws with an
/// application program cannot run [#GLSL], so they skip the peels and draw unsorted afterwards.
public final class DepthPeel {

    public static final int TEXTURE_UNIT = 15;
    public static final int SCENE_TEXTURE_UNIT = 14;

    private static final int PASS_DEPTH = 0;
    private static final int PASS_COLOR = 1;
    private static final int PASS_REST = 2;
    private static final int UNBOUNDED = 4;

    public static final String GLSL = "#define PEEL_PASS_COLOR " + PASS_COLOR + "\n"
        + "#define PEEL_UNBOUNDED " + UNBOUNDED + "\n"
        + "#define PEEL_SCENE_TOLERANCE 6.0e-8\n"
        + "#define PEEL_WINDOW 2.4e-7\n"
        + """
        uniform sampler2D u_PeelDepth;
        uniform sampler2D u_PeelScene;
        uniform int u_PeelMode;
        void peelTest() {
            ivec2 texel = ivec2(gl_FragCoord.xy);
            float z = gl_FragCoord.z;
            if (z > texelFetch(u_PeelScene, texel, 0).r + PEEL_SCENE_TOLERANCE) discard;
            float bound = texelFetch(u_PeelDepth, texel, 0).r;
            if ((u_PeelMode & 3) == PEEL_PASS_COLOR) {
                if (z > bound || z < bound - PEEL_WINDOW) discard;
            } else if ((u_PeelMode & PEEL_UNBOUNDED) == 0 && z >= bound - PEEL_WINDOW) {
                discard;
            }
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
            case DEPTH -> PASS_DEPTH | (unbounded ? UNBOUNDED : 0);
            case COLOR -> PASS_COLOR;
            default -> PASS_REST;
        };
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
