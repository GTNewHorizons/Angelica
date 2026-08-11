package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
public class GLSM_SplashCachingCoverage_UnitTest {

    @BeforeEach
    void enterSplashWindow() throws IllegalAccessException {
        SplashWindow.enter();
        assertFalse(GLStateManager.isCachingEnabled(), "setup must reproduce the caching=false window");
    }

    @AfterEach
    void leaveSplashWindow() throws IllegalAccessException {
        SplashWindow.leave();
        GLStateManager.glDepthFunc(GL11.GL_LESS);
    }

    @Test
    void depthFuncSetDuringSplashLandsInCache() {
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "cache write dropped while caching=false");
    }

    @Test
    void popAttribDoesNotRevertDepthFuncSetDuringSplash() throws IllegalAccessException {
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);

        SplashWindow.setSplashComplete(true);
        GLStateManager.setDrawableGLHolder(Thread.currentThread());

        GLStateManager.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT);
        GLStateManager.glPopAttrib();

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "popAttrib reverted to the stale default");
    }

    @Test
    void everyBooleanStateIsCollectedForReplay() throws IllegalAccessException {
        final GLContextState ctx = GLStateManager.ctx();
        final Set<BooleanStateStack> tracked = Collections.newSetFromMap(new IdentityHashMap<>());
        tracked.addAll(ctx.allBooleanStates);
        assertEquals(ctx.allBooleanStates.size(), tracked.size(), "duplicate entries in allBooleanStates");

        int declared = 0;
        for (Field f : GLContextState.class.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getType() == BooleanStateStack.class) {
                final BooleanStateStack s = (BooleanStateStack) f.get(ctx);
                if (s == null) continue;
                declared++;
                assertTrue(tracked.contains(s), f.getName() + " skipped track() - missing from replay coverage");
            } else if (f.getType() == BooleanStateStack[].class) {
                for (BooleanStateStack s : (BooleanStateStack[]) f.get(ctx)) {
                    if (s == null) continue;
                    declared++;
                    assertTrue(tracked.contains(s), f.getName() + " element skipped track() - missing from replay coverage");
                }
            }
        }

        assertTrue(declared > 0, "no BooleanStateStack fields found - the check is broken");
        assertEquals(declared, ctx.allBooleanStates.size(), "allBooleanStates contains states not declared on GLContextState");
    }

    @Test
    void replayPushesCachedStateWithoutDisturbingIt() {
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glCullFace(GL11.GL_FRONT);

        GLStateManager.replayStateToBackend();

        assertEquals(GL11.GL_LEQUAL, GLStateManager.getDepthState().getFunc(), "replay must not mutate the cache");
        assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode(), "replay must not mutate the cache");
    }

    @Test
    void replayPushesDepthFuncToRealGL() {
        assertNotNull(BackendManager.RENDER_BACKEND, "no backend: replay would no-op and this test would pass vacuously");

        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LESS);
        assertEquals(GL11.GL_LESS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "setup failed to diverge real GL");

        GLStateManager.replayStateToBackend();

        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "cached depth func never reached real GL; GUI draws at equal depth would be rejected");
    }

    @Test
    void firstFrameAfterSplashSeedsRealGL() throws IllegalAccessException {
        assertNotNull(BackendManager.RENDER_BACKEND, "no backend: this test would pass vacuously");

        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LESS);

        SplashWindow.setSplashComplete(false);
        GLStateManager.markSplashComplete("test-seed");
        GLStateManager.setDrawableGLHolder(Thread.currentThread());

        BackendManager.RENDER_BACKEND.onFrameBegin();

        assertEquals(GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "first frame after splash did not seed cached state into real GL");

        GL11.glDepthFunc(GL11.GL_LESS);
        BackendManager.RENDER_BACKEND.onFrameBegin();
        assertEquals(GL11.GL_LESS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "seed re-ran on a later frame; it must fire exactly once");
    }

    @Test
    void splashCompleteRequestsSeedOnceAndIssuesNoGLItself() throws IllegalAccessException {
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LESS);

        SplashWindow.setSplashComplete(false);
        GLStateManager.markSplashComplete("test-seed");

        assertEquals(GL11.GL_LESS, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), "markSplashComplete must only request the seed: releaseContext already dropped the context on that thread");
        assertTrue(GLStateManager.takeStateSeedPending(), "end of splash must request a state seed");
        assertFalse(GLStateManager.takeStateSeedPending(), "seed must be consumed exactly once, not once per frame");
        assertFalse(GLStateManager.takeStateSeedPending(), "seed must stay consumed");
    }

    @Test
    void cullFaceSetDuringSplashLandsInCache() {
        GLStateManager.glCullFace(GL11.GL_FRONT);

        assertEquals(GL11.GL_FRONT, GLStateManager.getPolygonState().getCullFaceMode(), "cache write dropped while caching=false");
    }
}
