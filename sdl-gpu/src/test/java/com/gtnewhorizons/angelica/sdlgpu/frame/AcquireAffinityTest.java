package com.gtnewhorizons.angelica.sdlgpu.frame;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcquireAffinityTest {

    private static final Thread WINDOW = new Thread("window");
    private static final Thread OTHER = new Thread("other");

    /** A null window is never a violation: the check runs before the window is claimed. */
    static Stream<Arguments> acquires() {
        return Stream.of(
            Arguments.of("on the window thread", WINDOW, WINDOW, false),
            Arguments.of("on any other thread", OTHER, WINDOW, true),
            Arguments.of("before the window thread is known", OTHER, null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acquires")
    void acquireAffinityViolated(String name, Thread self, Thread window, boolean violated) {
        assertEquals(violated, FrameManager.acquireAffinityViolated(self, window));
    }
}
