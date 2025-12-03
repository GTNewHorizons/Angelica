package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.AccumulatedDraw;
import com.gtnewhorizons.angelica.glsm.recording.CommandBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandBufferProcessor;
import com.gtnewhorizons.angelica.glsm.recording.CommandBufferProcessor.BufferTransformOptimizer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            VertexBuffer[] ownedVbos,
            int glListId,
            CommandBuffer finalBuffer) {

        // Build VBO index map for efficient lookup (no boxing)
        final Object2IntMap<VertexBuffer> vboIndexMap = new Object2IntOpenHashMap<>();
        vboIndexMap.defaultReturnValue(-1);
        for (int i = 0; i < ownedVbos.length; i++) {
            vboIndexMap.put(ownedVbos[i], i);
        }

        // Collect all merged draw ranges sorted by command index
        final List<DrawRangeWithBuffer> allRanges = collectDrawRanges(compiledQuadBuffers, compiledLineBuffer);

        // Buffer optimizer state
        final BufferTransformOptimizer opt = new BufferTransformOptimizer();

        rawBuffer.resetRead();
        int cmdIndex = 0;
        int rangeIndex = 0;

        while (rawBuffer.hasRemaining()) {
            // Emit draw ranges at this command position
            while (rangeIndex < allRanges.size() && allRanges.get(rangeIndex).range().commandIndex() == cmdIndex) {
                emitDrawRangeToBuffer(allRanges.get(rangeIndex++), opt, finalBuffer, vboIndexMap);
            }

            // Read and process the command
            final int opcode = rawBuffer.readInt();
            CommandBufferProcessor.processCommandForOptimization(opcode, rawBuffer, opt, finalBuffer);
            cmdIndex++;
        }

        // Emit remaining draw ranges at end of command stream
        while (rangeIndex < allRanges.size()) {
            emitDrawRangeToBuffer(allRanges.get(rangeIndex++), opt, finalBuffer, vboIndexMap);
        }

        // Emit residual transform if not identity
        if (!opt.isIdentity()) {
            opt.emitPendingTransform(finalBuffer);
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
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            VertexBuffer[] ownedVbos,
            CommandBuffer finalBuffer) {

        // Build VBO index map for efficient lookup (no boxing)
        final Object2IntMap<VertexBuffer> vboIndexMap = new Object2IntOpenHashMap<>();
        vboIndexMap.defaultReturnValue(-1);
        for (int i = 0; i < ownedVbos.length; i++) {
            vboIndexMap.put(ownedVbos[i], i);
        }

        // Collect all merged draw ranges sorted by command index
        final List<DrawRangeWithBuffer> allRanges = collectDrawRanges(compiledQuadBuffers, compiledLineBuffer);

        rawBuffer.resetRead();
        int cmdIndex = 0;
        int rangeIndex = 0;

        while (rawBuffer.hasRemaining()) {
            // Emit draw ranges at this command position
            while (rangeIndex < allRanges.size() && allRanges.get(rangeIndex).range().commandIndex() == cmdIndex) {
                final DrawRangeWithBuffer drb = allRanges.get(rangeIndex++);
                final int vboIdx = vboIndexMap.getInt(drb.vbo());
                if (vboIdx >= 0) {
                    finalBuffer.writeDrawRange(vboIdx, drb.range().startVertex(), drb.range().vertexCount(), drb.hasBrightness());
                }
            }

            // Copy the command directly
            CommandBufferProcessor.copyCommand(rawBuffer, finalBuffer);
            cmdIndex++;
        }

        // Emit remaining draw ranges at end of command stream
        while (rangeIndex < allRanges.size()) {
            final DrawRangeWithBuffer drb = allRanges.get(rangeIndex++);
            final int vboIdx = vboIndexMap.getInt(drb.vbo());
            if (vboIdx >= 0) {
                finalBuffer.writeDrawRange(vboIdx, drb.range().startVertex(), drb.range().vertexCount(), drb.hasBrightness());
            }
        }
    }

    /**
     * Collect all draw ranges from compiled buffers, sorted by command index.
     */
    private static List<DrawRangeWithBuffer> collectDrawRanges(
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer) {

        final List<DrawRangeWithBuffer> allRanges = new ArrayList<>();

        for (CompiledFormatBuffer compiledBuffer : compiledQuadBuffers.values()) {
            for (DrawRange range : compiledBuffer.mergedRanges()) {
                allRanges.add(new DrawRangeWithBuffer(range, compiledBuffer.vbo(), compiledBuffer.flags().hasBrightness));
            }
        }

        if (compiledLineBuffer != null) {
            for (DrawRange range : compiledLineBuffer.mergedRanges()) {
                allRanges.add(new DrawRangeWithBuffer(range, compiledLineBuffer.vbo(), false));
            }
        }

        allRanges.sort(Comparator.comparingInt(r -> r.range().commandIndex()));
        return allRanges;
    }

    /**
     * Emit a draw range to the output buffer, with transform if needed.
     */
    private static void emitDrawRangeToBuffer(
            DrawRangeWithBuffer drb,
            BufferTransformOptimizer opt,
            CommandBuffer out,
            Object2IntMap<VertexBuffer> vboIndexMap) {
        // Emit transform to reach the draw's expected transform
        opt.emitTransformTo(out, drb.range().transform());

        // Write the draw range command
        final int vboIdx = vboIndexMap.getInt(drb.vbo());
        if (vboIdx >= 0) {
            out.writeDrawRange(vboIdx, drb.range().startVertex(), drb.range().vertexCount(), drb.hasBrightness());
        }
    }
}
