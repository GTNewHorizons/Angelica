package com.gtnewhorizons.angelica.sdlgpu.compat;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.GLDebugMessageCallbackI;
import org.lwjgl.system.Callback;

import java.io.PrintStream;

/** Redirect targets for Lwjgl3ify-Aware callers - redirect to GLStateManager */
public final class AwareGLDebugShim {

    private static final Logger LOG = LogManager.getLogger("SDLGPU");

    private AwareGLDebugShim() {}

    public static void glDebugMessageCallback(GLDebugMessageCallbackI callback, long userParam) {
        GLStateManager.registerDebugMessageListener(callback == null ? null : callback::invoke, userParam);
    }

    public static Callback setupDebugMessageCallback() {
        return setupDebugMessageCallback(System.err);
    }

    public static Callback setupDebugMessageCallback(PrintStream stream) {
        if (!BackendManager.RENDER_BACKEND.supportsDebugOutput()) {
            LOG.info("GLUtil.setupDebugMessageCallback ignored: {} has no driver message stream. Run with -Dorg.lwjgl.util.Debug=true to enable it.", BackendManager.RENDER_BACKEND.getName());
            return null;
        }
        final GLDebugMessageCallback proc = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> stream.println("[Angelica] GL debug message: " + GLDebugMessageCallback.getMessage(length, message)));
        GLStateManager.registerDebugMessageListener(proc::invoke, 0L);
        return proc;
    }
}
