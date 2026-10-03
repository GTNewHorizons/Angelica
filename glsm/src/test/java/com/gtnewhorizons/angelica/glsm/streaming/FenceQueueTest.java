package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenceQueueTest {

    @Test
    void fifoOrderAcrossWrapAndGrowth() {
        final FenceQueue q = new FenceQueue();
        for (int i = 0; i < 5; i++) q.enqueue(i, i * 10);
        for (int i = 0; i < 3; i++) {
            assertEquals(i, q.firstId());
            assertEquals(i * 10, q.firstBytes());
            q.dequeue();
        }
        for (int i = 5; i < 15; i++) q.enqueue(i, i * 10);

        for (int i = 3; i < 15; i++) {
            assertFalse(q.isEmpty());
            assertEquals(i, q.firstId());
            assertEquals(i * 10, q.firstBytes());
            q.dequeue();
        }
        assertTrue(q.isEmpty());
    }

    @Test
    void firstBytesTracksFirstId() {
        final FenceQueue q = new FenceQueue();
        q.enqueue(100L, 7);
        q.enqueue(200L, 9);
        assertEquals(100L, q.firstId());
        assertEquals(7, q.firstBytes());
        q.dequeue();
        assertEquals(200L, q.firstId());
        assertEquals(9, q.firstBytes());
    }

    @Test
    void drainAndRefillKeepsBackingArray() {
        final FenceQueue q = new FenceQueue();
        for (int i = 0; i < 20; i++) q.enqueue(i, i);
        while (!q.isEmpty()) q.dequeue();

        final long[] before = Reflect.get(q, "ids");
        for (int i = 0; i < 10; i++) q.enqueue(i, i);
        final long[] after = Reflect.get(q, "ids");
        assertSame(before, after);
        assertEquals(0L, q.firstId());
    }
}
