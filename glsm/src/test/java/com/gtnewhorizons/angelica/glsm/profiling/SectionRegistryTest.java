package com.gtnewhorizons.angelica.glsm.profiling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionRegistryTest {

    private static final class RecordingEmitter implements SectionRegistry.Emitter {
        final List<String> setups = new ArrayList<>();
        final List<String> enters = new ArrayList<>();
        final List<Long> leaves = new ArrayList<>();
        long nextId;
        boolean connected;

        @Override
        public long enter(int category, String text) {
            enters.add(category + ":" + text);
            return connected ? ++nextId : 0L;
        }

        @Override
        public void leave(long nativeId) {
            leaves.add(nativeId);
        }

        @Override
        public void setup(int category, String name) {
            setups.add(category + ":" + name);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }
    }

    private final RecordingEmitter emitter = new RecordingEmitter();
    private final SectionRegistry registry = new SectionRegistry(emitter);

    private void reconnect() {
        emitter.connected = false;
        registry.poll();
        emitter.connected = true;
        registry.poll();
    }

    @Test
    void tokensOutliveEveryReplayAndLeaveUsesTheNewestId() {
        final long openedWhileDisconnected = registry.enter(1, "world");
        assertNotEquals(0L, openedWhileDisconnected, "token must be valid while disconnected");
        assertEquals(0L, emitter.nextId, "no native id while disconnected");

        emitter.connected = true;
        registry.poll();
        final long openedWhileConnected = registry.enter(2, "shaders");
        assertNotEquals(openedWhileDisconnected, openedWhileConnected);

        reconnect();
        emitter.leaves.clear();
        registry.leave(openedWhileDisconnected);
        registry.leave(openedWhileConnected);

        assertEquals(List.of(3L, 4L), emitter.leaves, "leave must use the post-reconnect ids");
    }

    @Test
    void aSectionEnteredOnTheConnectingFrameIsNotEnteredTwice() {
        emitter.connected = true;
        registry.enter(2, "shaders pack / Overworld");
        registry.poll();

        assertEquals(List.of("2:shaders pack / Overworld"), emitter.enters, "must not re-enter a section already live on this connection");
    }

    @Test
    void reconnectReplaysEveryCategoryAndEveryOpenSection() {
        registry.registerCategory(1, "World");
        registry.registerCategory(4, "UI");
        emitter.connected = true;
        registry.poll();
        registry.enter(1, "world");
        registry.enter(4, "pause");
        emitter.setups.clear();
        emitter.enters.clear();

        reconnect();

        assertEquals(List.of("1:World", "4:UI"), emitter.setups, "all categories re-registered");
        assertEquals(List.of("1:world", "4:pause"), emitter.enters, "all open sections re-entered");
    }

    @Test
    void leftSectionsAreNotResurrectedByAReconnect() {
        emitter.connected = true;
        registry.poll();
        final long token = registry.enter(1, "transient");
        registry.leave(token);
        emitter.enters.clear();
        emitter.leaves.clear();

        reconnect();
        registry.leave(token);

        assertTrue(emitter.enters.isEmpty(), "closed sections must not be re-entered");
        assertTrue(emitter.leaves.isEmpty(), "closed tokens must not be left twice");
    }
}
