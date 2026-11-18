package net.coderbot.iris.gl.state;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiListenerNotifierTest {

    @Test
    void dedupesAndRunsInRegistrationOrder() {
        final List<String> recorded = new ArrayList<>();
        final Runnable a = () -> recorded.add("a");
        final Runnable b = () -> recorded.add("b");
        final MultiListenerNotifier n = new MultiListenerNotifier();
        n.setListener(a);
        n.setListener(b);
        n.setListener(a);
        n.run();
        assertEquals(List.of("a", "b"), recorded);
    }

    @Test
    void clearDropsAllListeners() {
        final List<String> recorded = new ArrayList<>();
        final Runnable r1 = () -> recorded.add("1");
        final Runnable r2 = () -> recorded.add("2");
        final Runnable r3 = () -> recorded.add("3");
        final MultiListenerNotifier n = new MultiListenerNotifier();
        n.setListener(r1);
        n.setListener(r2);
        n.setListener(r3);
        n.setListener(null);
        n.run();
        assertTrue(recorded.isEmpty());
        n.setListener(r1);
        n.setListener(r2);
        n.setListener(r3);
        n.run();
        assertEquals(List.of("1", "2", "3"), recorded);
    }
}
