package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.CommandBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandBufferProcessor;

import java.util.List;

/**
 * Builds final command buffers from raw command buffers and compiled VBO data.
 * Bridges the gap between VBO compilation (glsm types) and buffer processing (recording types).
 */
public final class CommandBufferBuilder {

    private CommandBufferBuilder() {}

    /**
     * Build commands with transform collapsing optimization.
     * Accumulates MODELVIEW transforms and emits MultMatrix at barriers (draws, CallList).
     * Uses mergedRanges where consecutive same-transform draws are combined.
     */
    public static void buildOptimizedFromRawBuffer(
            CommandBuffer rawBuffer,
            List<AccumulatedDraw> accumulatedDraws,
            CommandBuffer finalBuffer) {

        rawBuffer.resetRead();
        int cmdIndex = 0;
        int rangeIndex = 0;

        AccumulatedDraw draw;
        while (rawBuffer.hasRemaining()) {
            // Read and process the command
            CommandBufferProcessor.copyCommand(rawBuffer, finalBuffer);

            // Emit draw ranges at this command position
            while (rangeIndex < accumulatedDraws.size() && (draw = accumulatedDraws.get(rangeIndex)).commandIndex == cmdIndex) {
                emitDrawRangeToBuffer(draw, finalBuffer, rangeIndex);
                rangeIndex++;
            }

            cmdIndex++;
        }
    }

    /**
     * Build commands without transform collapsing.
     * Copies state commands directly (no transform optimization), but still uses
     * merged draw ranges (draws already collapsed by format + transform in VBO compilation).
     */
    public static void buildUnoptimizedFromRawBuffer(
            CommandBuffer rawBuffer,
            List<AccumulatedDraw> accumulatedDraws,
            CommandBuffer finalBuffer) {

        rawBuffer.resetRead();
        int cmdIndex = 0;
        int rangeIndex = 0;

        while (rawBuffer.hasRemaining()) {
            // Copy the command directly
            CommandBufferProcessor.copyCommand(rawBuffer, finalBuffer);

            // Emit draw ranges at this command position
            while (rangeIndex < accumulatedDraws.size() && accumulatedDraws.get(rangeIndex).commandIndex == cmdIndex) {
                finalBuffer.writeDrawRange(rangeIndex);
                rangeIndex++;
            }

            cmdIndex++;
        }
    }

    /**
     * Emit a draw range to the output buffer, with transform if needed.
     */
    private static void emitDrawRangeToBuffer(
            AccumulatedDraw draw,
            CommandBuffer out,
            int vboIdx) {

        if (draw.restoreData != null) {
            out.writeDrawRangeRestore(vboIdx, draw.restoreData);
            return;
        }

        // Write the draw range command
        out.writeDrawRange(vboIdx);
    }
}
