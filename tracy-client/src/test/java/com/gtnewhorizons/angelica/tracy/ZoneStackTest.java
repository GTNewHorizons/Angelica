package com.gtnewhorizons.angelica.tracy;

import com.gtnewhorizons.angelica.glsm.profiling.ZoneStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneStackTest {

    @Test
    void lifoBalance() {
        final ZoneStack stack = new ZoneStack();
        assertTrue(stack.push(1));
        assertTrue(stack.push(2));
        assertEquals(2, stack.peek());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.pop());
        assertEquals(0, stack.depth());
    }

    @Test
    void popOnEmptyIsSilentNoOp() {
        final ZoneStack stack = new ZoneStack();
        assertEquals(ZoneStack.EMPTY, stack.pop());
        assertEquals(ZoneStack.EMPTY, stack.peek());
        assertTrue(stack.push(7));
        assertEquals(7, stack.pop());
        assertEquals(ZoneStack.EMPTY, stack.pop());
    }

    @Test
    void growsPastInitialCapacity() {
        final ZoneStack stack = new ZoneStack();
        for (long i = 0; i < 256; i++) assertTrue(stack.push(i));
        for (long i = 255; i >= 0; i--) assertEquals(i, stack.pop());
    }

    @Test
    void gpuFlagFollowsPop() {
        final ZoneStack stack = new ZoneStack();
        assertTrue(stack.push(1, true));
        assertTrue(stack.push(2, false));
        assertTrue(stack.push(3, true));
        assertEquals(3, stack.pop());
        assertTrue(stack.poppedGpu());
        assertEquals(2, stack.pop());
        assertFalse(stack.poppedGpu());
        assertEquals(1, stack.pop());
        assertTrue(stack.poppedGpu());
        assertEquals(ZoneStack.EMPTY, stack.pop());
        assertFalse(stack.poppedGpu());
    }

    @Test
    void atCapReflectsDepth() {
        final ZoneStack stack = new ZoneStack();
        assertFalse(stack.atCap());
        for (long i = 0; i < 512; i++) stack.push(i);
        assertTrue(stack.atCap());
        stack.pop();
        assertFalse(stack.atCap());
    }

    @Test
    void overflowSkipsAndPreservesPairing() {
        final ZoneStack stack = new ZoneStack();
        for (long i = 0; i < 512; i++) assertTrue(stack.push(i));
        assertFalse(stack.push(9001));
        assertFalse(stack.push(9002));
        assertEquals(ZoneStack.EMPTY, stack.peek());
        assertEquals(ZoneStack.EMPTY, stack.pop());
        assertEquals(ZoneStack.EMPTY, stack.pop());
        for (long i = 511; i >= 0; i--) assertEquals(i, stack.pop());
        assertEquals(ZoneStack.EMPTY, stack.pop());
    }

    @Test
    void nameFollowsPop() {
        final ZoneStack stack = new ZoneStack();
        assertTrue(stack.push(1, false, "tick"));
        assertTrue(stack.push(2, true, "jobs"));
        assertTrue(stack.push(3, false, null));
        assertEquals(3, stack.pop());
        assertEquals(null, stack.poppedName());
        assertEquals(2, stack.pop());
        assertEquals("jobs", stack.poppedName());
        assertTrue(stack.poppedGpu());
        assertEquals(1, stack.pop());
        assertEquals("tick", stack.poppedName());
        assertEquals(ZoneStack.EMPTY, stack.pop());
        assertEquals(null, stack.poppedName());
    }

    @Test
    void namesSurviveGrowth() {
        final ZoneStack stack = new ZoneStack();
        for (int i = 0; i < 256; i++) assertTrue(stack.push(i, false, "z" + i));
        for (int i = 255; i >= 0; i--) {
            assertEquals(i, stack.pop());
            assertEquals("z" + i, stack.poppedName());
        }
    }

    @Test
    void drainToEmptyClearsSkippedAndDepth() {
        final ZoneStack stack = new ZoneStack();
        for (long i = 0; i < 512; i++) assertTrue(stack.push(i, false, "d" + i));
        assertFalse(stack.push(9001, false, "over"));
        assertTrue(stack.hasSkipped());
        int drained = 0;
        while (stack.depth() > 0 || stack.hasSkipped()) {
            if (stack.pop() != ZoneStack.EMPTY) drained++;
        }
        assertEquals(512, drained);
        assertFalse(stack.hasSkipped());
        assertEquals(0, stack.depth());
        assertEquals(ZoneStack.EMPTY, stack.pop());
    }

    @Test
    void skippedPopReportsNoName() {
        final ZoneStack stack = new ZoneStack();
        for (long i = 0; i < 512; i++) stack.push(i, false, "x");
        stack.push(9001, false, "capped");
        assertEquals(ZoneStack.EMPTY, stack.pop());
        assertEquals(null, stack.poppedName());
        assertFalse(stack.poppedGpu());
    }
}
