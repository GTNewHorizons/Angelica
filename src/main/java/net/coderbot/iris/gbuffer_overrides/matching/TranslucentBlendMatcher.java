package net.coderbot.iris.gbuffer_overrides.matching;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import org.lwjgl.opengl.GL11;

/**
 * Decides whether a draw is blending.
 */
public final class TranslucentBlendMatcher {

    private static final BlendState scratch = new BlendState();

    private TranslucentBlendMatcher() {}

    public static boolean matches(boolean blendEnabled, int srcRgb, int dstRgb) {
        return blendEnabled && srcRgb == GL11.GL_SRC_ALPHA && dstRgb == GL11.GL_ONE_MINUS_SRC_ALPHA;
    }

    public static boolean matchesCurrentState() {
        GLStateManager.getEffectiveBlendState(scratch);
        return matches(GLStateManager.isEffectiveBlendEnabled(), scratch.getSrcRgb(), scratch.getDstRgb());
    }
}
