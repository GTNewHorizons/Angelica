package com.gtnewhorizons.angelica.sdlgpu.util;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.sdl.SDLLog;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugMessageRelayTest {

    @AfterEach
    void reset() {
        DebugMessageRelay.setListener(null, 0L, true);
        drainAll();
    }

    private static void drainAll() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer log = stack.malloc(4096);
            while (DebugMessageRelay.drain(64, null, null, null, null, null, log) > 0) {
                log.clear();
            }
        }
    }

    private record Received(int source, int type, int severity, String message) {}

    private static List<Received> captureFrom(int priority, String message, boolean validationLayerActive) {
        final List<Received> got = new ArrayList<>();
        DebugMessageRelay.setListener((source, type, id, severity, length, msg, userParam) ->
            got.add(new Received(source, type, severity, MemoryUtil.memUTF8(MemoryUtil.memByteBuffer(msg, length)))),
            0L, validationLayerActive);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DebugMessageRelay.onSdlMessage(priority, MemoryUtil.memAddress(stack.UTF8(message)));
        }
        return got;
    }

    @Test
    void errorMessageReachesTheListenerAsAHighSeverityApiError() {
        final List<Received> got = captureFrom(SDLLog.SDL_LOG_PRIORITY_ERROR, "validation exploded", true);

        assertEquals(1, got.size());
        assertEquals(KHRDebug.GL_DEBUG_SOURCE_API, got.get(0).source());
        assertEquals(KHRDebug.GL_DEBUG_TYPE_ERROR, got.get(0).type());
        assertEquals(KHRDebug.GL_DEBUG_SEVERITY_HIGH, got.get(0).severity());
        assertEquals("validation exploded", got.get(0).message());
    }

    @Test
    void warningIsMediumAndNotAnError() {
        final List<Received> got = captureFrom(SDLLog.SDL_LOG_PRIORITY_WARN, "heads up", true);

        assertEquals(KHRDebug.GL_DEBUG_SEVERITY_MEDIUM, got.get(0).severity());
        assertNotEquals(KHRDebug.GL_DEBUG_TYPE_ERROR, got.get(0).type());
    }

    @Test
    void registrationIsAcceptedWithTheValidationLayerOff() {
        final List<Received> got = captureFrom(SDLLog.SDL_LOG_PRIORITY_ERROR, "still delivered if SDL logs", false);

        assertEquals(1, got.size(), "SDL only emits when the layer is on; if a message does arrive, deliver it");
    }

    @Test
    void getDebugMessageLogDrainsOldestFirstThenReportsEmpty() {
        DebugMessageRelay.setListener(null, 0L, true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DebugMessageRelay.onSdlMessage(SDLLog.SDL_LOG_PRIORITY_ERROR, MemoryUtil.memAddress(stack.UTF8("first")));
            DebugMessageRelay.onSdlMessage(SDLLog.SDL_LOG_PRIORITY_WARN, MemoryUtil.memAddress(stack.UTF8("second")));

            final IntBuffer severities = stack.mallocInt(4);
            final IntBuffer lengths = stack.mallocInt(4);
            final ByteBuffer log = stack.malloc(256);

            assertEquals(2, DebugMessageRelay.drain(4, null, null, null, severities, lengths, log));

            assertEquals(KHRDebug.GL_DEBUG_SEVERITY_HIGH, severities.get(0));
            assertEquals(KHRDebug.GL_DEBUG_SEVERITY_MEDIUM, severities.get(1));
            assertEquals("first".length() + 1, lengths.get(0), "GL counts the NUL terminator");

            log.flip();
            final byte[] bytes = new byte[log.remaining()];
            log.get(bytes);
            final String flat = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(flat.startsWith("first\0"), () -> "oldest first, NUL separated: " + flat);

            assertEquals(0, DebugMessageRelay.drain(4, null, null, null, null, null, stack.malloc(64)));
        }
    }

    @Test
    void nullListenerUnregisters() {
        captureFrom(SDLLog.SDL_LOG_PRIORITY_ERROR, "before", true);
        DebugMessageRelay.setListener(null, 0L, true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DebugMessageRelay.onSdlMessage(SDLLog.SDL_LOG_PRIORITY_ERROR, MemoryUtil.memAddress(stack.UTF8("after")));
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            assertTrue(DebugMessageRelay.drain(8, null, null, null, null, null, stack.malloc(512)) > 0);
        }
    }

    @Test
    void drainStopsWhenTheMessageLogIsTooSmall() {
        DebugMessageRelay.setListener(null, 0L, true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DebugMessageRelay.onSdlMessage(SDLLog.SDL_LOG_PRIORITY_ERROR, MemoryUtil.memAddress(stack.UTF8("a long message")));

            assertEquals(0, DebugMessageRelay.drain(4, null, null, null, null, null, stack.malloc(4)));
        }
    }
}
