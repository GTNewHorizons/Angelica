package net.coderbot.iris.rendertarget;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParityFlipStateTest {

    @Test
    void paritySetExcludesClearedBuffers() {
        ParityFlipState state = new ParityFlipState(true);
        state.finalizeParitySet(ImmutableSet.of(1, 2, 7), IntArrayList.of(2));
        assertTrue(state.isEnabled());
        assertEquals(ImmutableSet.of(1, 7), state.parityBuffers());
    }

    @Test
    void emptyParitySetDisables() {
        ParityFlipState state = new ParityFlipState(true);
        state.finalizeParitySet(ImmutableSet.of(2), IntArrayList.of(2));
        assertFalse(state.isEnabled());
        state.onFrameStart();
        assertFalse(state.isOdd());
    }

    @Test
    void resolveXorsParityMembersOnOddFramesOnly() {
        ParityFlipState state = new ParityFlipState(true);
        state.finalizeParitySet(ImmutableSet.of(1, 7), IntArrayList.of());

        ImmutableSet<Integer> even = ImmutableSet.of(1, 3);
        assertEquals(even, state.resolve(even));

        state.onFrameStart();
        assertTrue(state.isOdd());
        assertEquals(ImmutableSet.of(3, 7), state.resolve(even));

        state.onFrameStart();
        assertFalse(state.isOdd());
        assertEquals(even, state.resolve(even));
    }

    @Test
    void disabledStateIsIdentity() {
        ParityFlipState state = new ParityFlipState(false);
        state.finalizeParitySet(ImmutableSet.of(1), IntArrayList.of());
        state.onFrameStart();
        assertFalse(state.isOdd());
        ImmutableSet<Integer> set = ImmutableSet.of(1, 2);
        assertEquals(set, state.resolve(set));
        assertFalse(state.affectsAny(new int[] { 1 }));
    }

    @Test
    void resetForcesEvenAfterFullClear() {
        ParityFlipState state = new ParityFlipState(true);
        state.finalizeParitySet(ImmutableSet.of(1), IntArrayList.of());
        state.onFrameStart();
        assertTrue(state.isOdd());
        state.reset();
        assertFalse(state.isOdd());
    }

    @Test
    void noToggleBeforeFinalize() {
        ParityFlipState state = new ParityFlipState(true);
        state.onFrameStart();
        assertFalse(state.isOdd());
    }

    @Test
    void affectsAnyChecksDrawBuffers() {
        ParityFlipState state = new ParityFlipState(true);
        state.finalizeParitySet(ImmutableSet.of(4, 5), IntArrayList.of());
        assertTrue(state.affectsAny(new int[] { 0, 5 }));
        assertFalse(state.affectsAny(new int[] { 0, 1, 2 }));
    }
}
