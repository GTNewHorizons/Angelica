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
    public static void buildFromRawBuffer(
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

        while (rangeIndex < accumulatedDraws.size()) {
            emitDrawRangeToBuffer(accumulatedDraws.get(rangeIndex), finalBuffer, rangeIndex);
            rangeIndex++;
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
