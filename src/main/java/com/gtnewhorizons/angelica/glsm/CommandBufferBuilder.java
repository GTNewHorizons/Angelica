package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandBuffer;
import com.gtnewhorizons.angelica.glsm.recording.CommandBufferProcessor;
import com.gtnewhorizons.angelica.glsm.recording.CommandBufferProcessor.BufferTransformOptimizer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds final command buffers from raw command buffers and compiled VBO data.
 * Transforms are already collapsed to MultMatrix during recording; the optimizer
 * tracks transform state to emit deltas when draws need a different transform.
 */
public final class CommandBufferBuilder {

    private static final Logger LOGGER = LogManager.getLogger("CommandBufferBuilder");

    private CommandBufferBuilder() {}

    /** Build with transform delta optimization and merged draw ranges. */
    public static void buildOptimizedFromRawBuffer(
            CommandBuffer rawBuffer,
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            final CompiledLineBuffer compiledLineBuffer,
            final Map<CapturingTessellator.Flags, CompiledPrimitiveBuffers> compiledPrimitiveBuffers,
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
        final List<DrawRangeWithBuffer> allRanges = collectDrawRanges(compiledQuadBuffers, compiledLineBuffer, compiledPrimitiveBuffers);

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

    /** Debug build: no optimizations, 1:1 draw commands. */
    public static void buildUnoptimizedFromRawBuffer(
            CommandBuffer rawBuffer,
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            Map<CapturingTessellator.Flags, CompiledPrimitiveBuffers> compiledPrimitiveBuffers,
            VertexBuffer[] ownedVbos,
            CommandBuffer finalBuffer) {

        // Build VBO index map for efficient lookup (no boxing)
        final Object2IntMap<VertexBuffer> vboIndexMap = new Object2IntOpenHashMap<>();
        vboIndexMap.defaultReturnValue(-1);
        for (int i = 0; i < ownedVbos.length; i++) {
            vboIndexMap.put(ownedVbos[i], i);
        }

        // Collect per-draw ranges (no merging) sorted by command index
        final List<DrawRangeWithBuffer> allRanges = collectDrawRangesUnoptimized(compiledQuadBuffers, compiledLineBuffer, compiledPrimitiveBuffers);

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
                    DisplayListManager.trackDrawRangeSource(drb.source().toString());
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
                DisplayListManager.trackDrawRangeSource(drb.source().toString());
            }
        }
    }

    /** Collect merged draw ranges, sorted by command index. */
    static List<DrawRangeWithBuffer> collectDrawRanges(
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            Map<CapturingTessellator.Flags, CompiledPrimitiveBuffers> compiledPrimitiveBuffers) {
        return collectDrawRangesInternal(compiledQuadBuffers, compiledLineBuffer, compiledPrimitiveBuffers, true);
    }

    /** Collect unmerged draw ranges for debug mode. */
    static List<DrawRangeWithBuffer> collectDrawRangesUnoptimized(
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            Map<CapturingTessellator.Flags, CompiledPrimitiveBuffers> compiledPrimitiveBuffers) {
        return collectDrawRangesInternal(compiledQuadBuffers, compiledLineBuffer, compiledPrimitiveBuffers, false);
    }

    private static List<DrawRangeWithBuffer> collectDrawRangesInternal(
            Map<CapturingTessellator.Flags, CompiledFormatBuffer> compiledQuadBuffers,
            CompiledLineBuffer compiledLineBuffer,
            Map<CapturingTessellator.Flags, CompiledPrimitiveBuffers> compiledPrimitiveBuffers,
            boolean useMerged) {

        final List<DrawRangeWithBuffer> allRanges = new ArrayList<>();

        // Add quad ranges
        final CompiledFormatBuffer[] quadBufferArray = compiledQuadBuffers.values().toArray(new CompiledFormatBuffer[0]);
        for (int b = 0; b < quadBufferArray.length; b++) {
            final CompiledFormatBuffer compiledBuffer = quadBufferArray[b];
            final DrawRange[] ranges = useMerged ? compiledBuffer.mergedRanges() : compiledBuffer.perDrawRanges();
            for (int i = 0; i < ranges.length; i++) {
                allRanges.add(new DrawRangeWithBuffer(ranges[i], compiledBuffer.vbo(), compiledBuffer.flags(),
                    DrawRangeWithBuffer.DrawSource.TESSELLATOR_QUADS));
            }
        }

        // Add immediate mode line ranges
        // Lines use a minimal flags (color only, no texture/brightness/normals)
        if (compiledLineBuffer != null) {
            final CapturingTessellator.Flags lineFlags = new CapturingTessellator.Flags(false, false, true, false);
            final DrawRange[] ranges = useMerged ? compiledLineBuffer.mergedRanges() : compiledLineBuffer.perDrawRanges();
            for (int i = 0; i < ranges.length; i++) {
                allRanges.add(new DrawRangeWithBuffer(ranges[i], compiledLineBuffer.vbo(), lineFlags,
                    DrawRangeWithBuffer.DrawSource.IMMEDIATE_MODE_LINES));
            }
        }

        // Add tessellator primitive ranges (lines and triangles) - grouped by format
        if (compiledPrimitiveBuffers != null && !compiledPrimitiveBuffers.isEmpty()) {
            final CompiledPrimitiveBuffers[] primBufferArray = compiledPrimitiveBuffers.values().toArray(new CompiledPrimitiveBuffers[0]);
            for (int p = 0; p < primBufferArray.length; p++) {
                final CompiledPrimitiveBuffers primBuffers = primBufferArray[p];
                final CapturingTessellator.Flags primFlags = primBuffers.flags();
                // Tessellator lines
                if (primBuffers.hasLines()) {
                    final DrawRange[] ranges = useMerged ? primBuffers.lineMergedRanges() : primBuffers.linePerDrawRanges();
                    final VertexBuffer vbo = primBuffers.lineVbo();
                    for (int i = 0; i < ranges.length; i++) {
                        allRanges.add(new DrawRangeWithBuffer(ranges[i], vbo, primFlags,
                            DrawRangeWithBuffer.DrawSource.TESSELLATOR_LINES));
                    }
                }
                // Tessellator triangles
                if (primBuffers.hasTriangles()) {
                    final DrawRange[] ranges = useMerged ? primBuffers.triangleMergedRanges() : primBuffers.trianglePerDrawRanges();
                    final VertexBuffer vbo = primBuffers.triangleVbo();
                    for (int i = 0; i < ranges.length; i++) {
                        allRanges.add(new DrawRangeWithBuffer(ranges[i], vbo, primFlags,
                            DrawRangeWithBuffer.DrawSource.TESSELLATOR_TRIANGLES));
                    }
                }
            }
        }

        allRanges.sort(Comparator.comparingInt(r -> r.range().commandIndex()));
        return allRanges;
    }

    /** Emit draw range with delta transform. Uses writeDrawRangeRestore for immediate mode. */
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
            final var restoreData = drb.range().restoreData();
            if (restoreData != null) {
                // Immediate mode draw with attribute restoration
                final var flags = drb.flags();
                out.writeDrawRangeRestore(
                    vboIdx, drb.range().startVertex(), drb.range().vertexCount(),
                    drb.hasBrightness(), flags.hasColor, flags.hasNormals, flags.hasTexture,
                    restoreData.lastColorR(), restoreData.lastColorG(),
                    restoreData.lastColorB(), restoreData.lastColorA(),
                    restoreData.lastNormalX(), restoreData.lastNormalY(), restoreData.lastNormalZ(),
                    restoreData.lastTexCoordS(), restoreData.lastTexCoordT()
                );
            } else {
                // Regular tessellator draw (no restoration needed)
                out.writeDrawRange(vboIdx, drb.range().startVertex(), drb.range().vertexCount(), drb.hasBrightness());
            }
            DisplayListManager.trackDrawRangeSource(drb.source().toString());
        }
    }
}
