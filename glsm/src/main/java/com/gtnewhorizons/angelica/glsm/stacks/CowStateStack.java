package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;

/**
 *            __n__n__
 *     .------`-\00/-'
 *    /  ##  ## (oo)
 *   / \## __   ./
 *      |//YY \|/
 *      |||   |||
 *    Copy On Write State Stack
 */
public interface CowStateStack<T> extends IStateStack<T> {
    CowDepths cowDepths();

    void captureSlot(int slot);

    void restoreSlot(int slot);

    default int stackId() {
        return cowDepths().id;
    }

    default void setStackId(int id) {
        cowDepths().id = id;
    }

    default int restoreBit() {
        return cowDepths().restoreBit;
    }

    default int restoreUnit() {
        return cowDepths().restoreUnit;
    }

    default boolean topSlotChanged() {
        return false;
    }

    @Override
    default int pushDepth() {
        return GLStateManager.getAttribDepth();
    }

    @Override
    default void beforeModify() {
        final int s = cowDepths().claim(GLStateManager.getAttribDepth());
        if (s >= 0) {
            captureSlot(s);
            GLStateManager.registerModifiedState(stackId());
        }
    }

    @Override
    default boolean isEmpty() {
        return cowDepths().isEmpty();
    }

    default void drain() {
        int s;
        while ((s = cowDepths().pop()) >= 0) restoreSlot(s);
    }

    @SuppressWarnings("unchecked")
    @Override
    default T push() {
        captureSlot(cowDepths().claimUnconditional());
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    default T pop() {
        final int s = cowDepths().pop();
        if (s < 0) {
            throw new IllegalStateException("Stack underflow");
        }
        restoreSlot(s);
        return (T) this;
    }
}
