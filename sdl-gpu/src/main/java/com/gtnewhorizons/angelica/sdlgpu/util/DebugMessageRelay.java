package com.gtnewhorizons.angelica.sdlgpu.util;

import java.nio.charset.StandardCharsets;
import com.gtnewhorizons.angelica.glsm.backend.GLDebugMessageListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.sdl.SDLLog;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Presents SDL's validation output as a GL debug message stream.  */
public final class DebugMessageRelay {

    private static final Logger LOG = LogManager.getLogger("SDLGPU");
    private static final int RING_CAPACITY = 64;

    private static volatile GLDebugMessageListener listener;
    private static volatile long listenerUserParam;
    private static boolean warnedNoValidationLayer;

    private static final String[] ringMessages = new String[RING_CAPACITY];
    private static final int[] ringSeverities = new int[RING_CAPACITY];
    private static int ringHead;
    private static int ringCount;

    private DebugMessageRelay() {}

    public static void setListener(GLDebugMessageListener newListener, long userParam, boolean validationLayerActive) {
        listener = newListener;
        listenerUserParam = userParam;
        if (newListener != null && !validationLayerActive && !warnedNoValidationLayer) {
            warnedNoValidationLayer = true;
            LOG.warn("A GL debug message callback was registered, but the SDL validation layer is off so no driver messages exist to deliver. Run with -Dorg.lwjgl.util.Debug=true (or -Dangelica.sdlgpu.debug=true) to enable it. Labels and debug groups are unaffected.");
        }
    }

    public static void onSdlMessage(int priority, long message) {
        final GLDebugMessageListener target = listener;
        final int severity = mapSeverity(priority);
        final int type = severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH ? KHRDebug.GL_DEBUG_TYPE_ERROR : KHRDebug.GL_DEBUG_TYPE_OTHER;

        record(severity, message);

        if (target == null) return;
        final int length = MemoryUtil.memByteBufferNT1(message).remaining();
        try {
            target.onMessage(KHRDebug.GL_DEBUG_SOURCE_API, type, 0, severity, length, message, listenerUserParam);
        } catch (Throwable t) {
            LOG.warn("GL debug message callback threw", t);
        }
    }

    private static int mapSeverity(int priority) {
        return switch (priority) {
            case SDLLog.SDL_LOG_PRIORITY_ERROR, SDLLog.SDL_LOG_PRIORITY_CRITICAL -> KHRDebug.GL_DEBUG_SEVERITY_HIGH;
            case SDLLog.SDL_LOG_PRIORITY_WARN -> KHRDebug.GL_DEBUG_SEVERITY_MEDIUM;
            case SDLLog.SDL_LOG_PRIORITY_INFO -> KHRDebug.GL_DEBUG_SEVERITY_LOW;
            default -> KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION;
        };
    }

    private static synchronized void record(int severity, long message) {
        ringMessages[ringHead] = MemoryUtil.memUTF8(message);
        ringSeverities[ringHead] = severity;
        ringHead = (ringHead + 1) % RING_CAPACITY;
        if (ringCount < RING_CAPACITY) ringCount++;
    }

    public static synchronized int drain(int count, IntBuffer sources, IntBuffer types, IntBuffer ids,
        IntBuffer severities, IntBuffer lengths, ByteBuffer messageLog) {
        int written = 0;
        while (written < count && ringCount > 0) {
            final int tail = (ringHead - ringCount + RING_CAPACITY) % RING_CAPACITY;
            final byte[] utf8 = ringMessages[tail].getBytes(StandardCharsets.UTF_8);
            final int needed = utf8.length + 1; // GL counts the NUL terminator in `lengths`
            if (messageLog != null && messageLog.remaining() < needed) break;

            if (messageLog != null) {
                messageLog.put(utf8).put((byte) 0);
            }
            final int severity = ringSeverities[tail];
            if (sources != null) sources.put(KHRDebug.GL_DEBUG_SOURCE_API);
            if (types != null) types.put(severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH ? KHRDebug.GL_DEBUG_TYPE_ERROR : KHRDebug.GL_DEBUG_TYPE_OTHER);
            if (ids != null) ids.put(0);
            if (severities != null) severities.put(severity);
            if (lengths != null) lengths.put(needed);

            ringMessages[tail] = null;
            ringCount--;
            written++;
        }
        return written;
    }
}
