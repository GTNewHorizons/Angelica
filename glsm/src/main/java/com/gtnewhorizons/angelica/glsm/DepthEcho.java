package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/// A draw between [#beginTracking] and [#endTracking] is left to the caller's replay instead of repeated. A replay
/// ignores the depth mask, which a previous renderer may leak differently into the pass and the replay, and repeats
/// set GL state through the backend, never the GLSM cache.
public final class DepthEcho {

    private static int framebuffer;
    private static int source;
    private static boolean echoed;
    private static boolean tracking;
    private static boolean hit;
    private static int immediate;
    private static boolean replaying;
    private static boolean replayDepthWrite;

    private DepthEcho() {}

    public static void arm(int framebuffer, int source) {
        DepthEcho.framebuffer = framebuffer;
        DepthEcho.source = source;
    }

    public static void disarm() {
        framebuffer = 0;
        tracking = false;
        hit = false;
        endReplay();
    }

    public static boolean armed() {
        return framebuffer != 0;
    }

    public static void beginTracking() {
        tracking = framebuffer != 0;
        hit = false;
    }

    public static boolean endTracking() {
        tracking = false;
        final boolean was = hit;
        hit = false;
        return was;
    }

    public static void beginImmediate() {
        immediate++;
    }

    public static void endImmediate() {
        immediate--;
    }

    public static void beginReplay() {
        replaying = true;
    }

    public static void endReplay() {
        replaying = false;
        restoreReplayDepthMask();
    }

    public static boolean replaying() {
        return replaying;
    }

    public static boolean takeEchoed() {
        final boolean was = echoed;
        echoed = false;
        return was;
    }

    public static boolean skipsCall() {
        return replaySkips() && !DisplayListManager.isRecording();
    }

    public static boolean skipsDraw() {
        if (!replaying) return false;
        if (replaySkips()) return true;
        if (!GLSMDepthStencilAlpha.getDepthState().isEnabled()) {
            RENDER_BACKEND.depthMask(true);
            replayDepthWrite = true;
        }
        return false;
    }

    private static boolean replaySkips() {
        return replaying && !(blendsVisibly() && GLSMFramebuffer.getDrawFramebuffer() == framebuffer);
    }

    public static boolean begin() {
        if (replayDepthWrite) {
            restoreReplayDepthMask();
            return false;
        }
        if (framebuffer == 0 || GLSMDepthStencilAlpha.getDepthState().isEnabled() || !blendsVisibly() || GLSMFramebuffer.getDrawFramebuffer() != source) return false;
        echoed = true;
        if (tracking && immediate == 0 && !BatchStateGuard.isSuspended()) {
            hit = true;
            return false;
        }
        RENDER_BACKEND.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        RENDER_BACKEND.depthMask(true);
        return true;
    }

    public static void end() {
        RENDER_BACKEND.depthMask(false);
        RENDER_BACKEND.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
    }

    private static void restoreReplayDepthMask() {
        if (!replayDepthWrite) return;
        replayDepthWrite = false;
        RENDER_BACKEND.depthMask(GLSMDepthStencilAlpha.getDepthState().isEnabled());
    }

    private static boolean blendsVisibly() {
        if (!GLSMDepthStencilAlpha.getDepthTest().isEnabled() || !GLSMBlend.getBlendMode().isEnabled()) return false;
        if (GLSMDepthStencilAlpha.getDepthState().getFunc() == GL11.GL_EQUAL) return false;
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        return mask.red || mask.green || mask.blue || mask.alpha;
    }
}
