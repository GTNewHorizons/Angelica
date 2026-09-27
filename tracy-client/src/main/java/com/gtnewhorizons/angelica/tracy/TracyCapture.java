package com.gtnewhorizons.angelica.tracy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

final class TracyCapture {
    private static final Logger LOGGER = LogManager.getLogger("Tracy");
    private static final int MEM_PERCENT = 25;
    private static final String UNAVAILABLE = "capture library unavailable on this platform";

    private boolean loaded;
    private String loadError;
    private long capStart;
    private long capStop;
    private long capState;
    private long capElapsedMs;
    private long capError;

    synchronized String start(String path, int port, int seconds) {
        final String err = ensureLoaded();
        if (err != null) return err;
        final File file = new File(path);
        final File parent = file.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            return "cannot create directory " + parent;
        }
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer pathUtf8 = stack.UTF8(path, true);
            final int result = JNI.invokePI(MemoryUtil.memAddress(pathUtf8), port, seconds, MEM_PERCENT, capStart);
            if (result == 1) return "a capture is already running";
            if (result != 0) return "capture start failed (code " + result + ")";
            return null;
        }
    }

    synchronized void stop() {
        if (!loaded) return;
        JNI.invokeV(capStop);
    }

    synchronized int state() {
        return loaded ? JNI.invokeI(capState) : 0;
    }

    synchronized long elapsedMs() {
        return loaded ? JNI.invokeJ(capElapsedMs) : 0L;
    }

    synchronized String error() {
        if (!loaded) return "";
        final long ptr = JNI.invokeP(capError);
        return ptr == NULL ? "" : MemoryUtil.memUTF8(ptr);
    }

    private String ensureLoaded() {
        if (loaded) return null;
        if (loadError != null) return loadError;
        final SharedLibrary lib = TracyNativeLoader.load("AngelicaTracyCapture");
        if (lib == null) {
            loadError = UNAVAILABLE;
            return loadError;
        }
        try {
            capStart = req(lib, "ang_cap_start");
            capStop = req(lib, "ang_cap_stop");
            capState = req(lib, "ang_cap_state");
            capElapsedMs = req(lib, "ang_cap_elapsed_ms");
            capError = req(lib, "ang_cap_error");
        } catch (IllegalStateException e) {
            LOGGER.warn("Tracy: {}", e.getMessage());
            loadError = UNAVAILABLE;
            return loadError;
        }
        loaded = true;
        return null;
    }

    private static long req(SharedLibrary lib, String name) {
        final long address = lib.getFunctionAddress(name);
        if (address == NULL) throw new IllegalStateException("missing symbol " + name + " in " + lib.getPath());
        return address;
    }
}
