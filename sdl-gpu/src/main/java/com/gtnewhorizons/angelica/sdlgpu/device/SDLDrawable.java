package com.gtnewhorizons.angelica.sdlgpu.device;

import org.lwjglx.opengl.Display;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import org.lwjglx.opengl.ContextGL;
import org.lwjglx.opengl.DrawableGL;

/**
 * Drawable for the SDL GPU backend.
 */
public final class SDLDrawable extends DrawableGL {

    public SDLDrawable() {
        super((ContextGL) null);
    }

    @Override
    public void makeCurrent() {
    }

    @Override
    public void releaseContext() {
        BackendManager.RENDER_BACKEND.onRenderThreadReleased(Thread.currentThread());
    }

    @Override
    public boolean isCurrent() {
        return SDLGPUGate.isActive();
    }

    @Override
    public ContextGL createSharedContext() {
        return null;
    }

    @Override
    public void destroy() {
        BackendManager.RENDER_BACKEND.onRenderThreadReleased(Thread.currentThread());
    }

    @Override
    public void checkGLError() {
    }

    @Override
    public void setSwapInterval(int swap_interval) {
    }

    @Override
    public void swapBuffers() {
        BackendManager.RENDER_BACKEND.handleSwapBuffers();
    }

    @Override
    public void initContext(float r, float g, float b) {
    }

    @Override
    public long getSdlWindowId() {
        return Display.getWindow();
    }
}
