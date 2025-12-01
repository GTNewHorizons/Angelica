package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.MultMatrixCmd;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Helper class for tracking and collapsing MODELVIEW transforms during optimization.
 * Implements the transform accumulation and emission strategy:
 * <ul>
 *   <li>accumulated: total relative transform (what GL matrix should be)</li>
 *   <li>lastEmitted: what we've emitted (what GL matrix is)</li>
 *   <li>stack: saved accumulated values at Push points</li>
 * </ul>
 */
class TransformOptimizer {
    private static final Logger LOGGER = LogManager.getLogger("TransformOptimizer");

    private final Matrix4f accumulated = new Matrix4f();  // Current accumulated transform
    private final Matrix4f lastEmitted = new Matrix4f();  // Last emitted transform
    private final Deque<Matrix4f> stack = new ArrayDeque<>();  // For Push/Pop
    private final int listId;  // For logging

    TransformOptimizer(int listId) {
        this.listId = listId;
        accumulated.identity();
        lastEmitted.identity();
    }

    Matrix4f getAccumulated() {
        return accumulated;
    }

    void loadIdentity() {
        accumulated.identity();
    }

    void pushTransform() {
        stack.push(new Matrix4f(accumulated));
    }

    void popTransform() {
        if (!stack.isEmpty()) {
            final Matrix4f popped = stack.pop();
            accumulated.set(popped);
            // After Pop, GL state is restored to the pushed value so lastEmitted should also reflect this
            lastEmitted.set(accumulated);
        } else {
            LOGGER.warn("[TransformOptimizer] list={} Pop with empty stack - resetting to identity", listId);
            accumulated.identity();
            lastEmitted.identity();
        }
    }

    boolean isIdentity() {
        return DisplayListManager.isIdentity(accumulated);
    }

    /**
     * Check if there's a pending transform that hasn't been emitted yet.
     */
    boolean hasPendingTransform() {
        return !accumulated.equals(lastEmitted);
    }

    /**
     * Emit a MultMatrix command if accumulated differs from lastEmitted.
     * Updates lastEmitted to match accumulated after emission.
     */
    void emitPendingTransform(List<DisplayListCommand> output) {
        if (!accumulated.equals(lastEmitted)) {
            emitTransformTo(output, accumulated);
        }
    }

    /**
     * Emit a MultMatrix command to make GL state match a target transform.
     * Used when a draw expects a specific transform that may differ from accumulated.
     * Updates lastEmitted to match target after emission.
     *
     * @param output The command list to emit to
     * @param target The target transform to reach
     */
    void emitTransformTo(List<DisplayListCommand> output, Matrix4f target) {
        if (target.equals(lastEmitted)) {
            // Already at target - nothing to emit
            return;
        }

        // Calculate delta: what we need to multiply to go from lastEmitted to target
        // delta = inverse(lastEmitted) * target
        // But for simplicity, if lastEmitted is identity, just emit target
        if (DisplayListManager.isIdentity(lastEmitted)) {
            // Simple case: just emit target
            output.add(MultMatrixCmd.create(target, GL11.GL_MODELVIEW));
        } else {
            // Need to emit delta: inv(lastEmitted) * target
            final Matrix4f delta = new Matrix4f(lastEmitted).invert().mul(target);
            output.add(MultMatrixCmd.create(delta, GL11.GL_MODELVIEW));
        }
        lastEmitted.set(target);
    }

    /**
     * Check if a draw's expected transform differs from what we've emitted.
     */
    boolean needsTransformForDraw(Matrix4f drawTransform) {
        return !drawTransform.equals(lastEmitted);
    }
}
