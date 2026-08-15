package net.coderbot.iris.gbuffer_overrides.matching;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import org.lwjgl.opengl.GL11;

/**
 * Decides whether a draw is blending.
 */
public final class TranslucentBlendMatcher {

    /** Render thread only. */
    private static final BlendState scratch = new BlendState();

    private TranslucentBlendMatcher() {}

    public static boolean matchesCurrentState() {
        if (!GLStateManager.isEffectiveBlendEnabled()) {
            return false;
        }
        GLStateManager.getEffectiveBlendState(scratch);
        return scratch.getSrcRgb() == GL11.GL_SRC_ALPHA && scratch.getDstRgb() == GL11.GL_ONE_MINUS_SRC_ALPHA;
    }
}
