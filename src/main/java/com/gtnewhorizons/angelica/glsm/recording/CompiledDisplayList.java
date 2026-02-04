package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;

/**
 * Represents a compiled display list.
 *
 * <p>Contains commands serialized to a direct ByteBuffer for off-heap storage,
 * with complex objects (TexImage2DCmd, TexSubImage2DCmd) stored separately.
 *
 * <p>The ownedVbos array contains VBOs that are referenced by DrawRange commands.
 */
public final class CompiledDisplayList {
    /**
     * Empty display list singleton. Per OpenGL spec, an empty list is still valid.
     * This instance does nothing when rendered and is never deleted (shared singleton).
     */
    public static final CompiledDisplayList EMPTY = new CompiledDisplayList(null, null, null);

    private final ByteBuffer commandBuffer;     // Off-heap command storage, must be freed
    private final Object[] complexObjects;      // Complex commands (TexImage2D, etc.)
    private final DisplayListVBO ownedVbos;     // GPU resources referenced by index

    public CompiledDisplayList(ByteBuffer commandBuffer, Object[] complexObjects, DisplayListVBO ownedVbos) {
        this.commandBuffer = commandBuffer;
        this.complexObjects = complexObjects;
        this.ownedVbos = ownedVbos;
    }

    /**
     * Render this display list by executing all commands.
     */
    public void render() {
        if (commandBuffer != null && commandBuffer.limit() > 0) {
            CommandBufferExecutor.execute(commandBuffer, complexObjects, ownedVbos);
        }
    }

    /**
     * Delete all resources held by this display list.
     */
    public void delete() {
        // Delete complex objects that hold resources
        if (complexObjects != null) {
            for (Object obj : complexObjects) {
                if (obj instanceof DisplayListCommand cmd) {
                    cmd.delete();
                }
            }
        }

        // Close VBOs
        if (ownedVbos != null) {
            ownedVbos.delete();
        }

        // Free the command buffer (off-heap memory)
        if (commandBuffer != null) {
            memFree(commandBuffer);
        }
    }

    /**
     * Get the command buffer for debugging/inspection.
     */
    public ByteBuffer getCommandBuffer() {
        return commandBuffer;
    }

    /**
     * Get complex objects for inlining into another display list (nested lists).
     */
    public Object[] getComplexObjects() {
        return complexObjects;
    }

    public DisplayListVBO getOwnedVbos() {
        return ownedVbos;
    }

    // === Test inspection methods ===

    /**
     * Get counts of each command type in the buffer.
     * Used for testing to verify optimization results.
     */
    public Int2IntMap getCommandCounts() {
        final Int2IntMap counts = new Int2IntOpenHashMap();
        if (commandBuffer == null || commandBuffer.limit() == 0) {
            return counts;
        }
        long ptr = memAddress(commandBuffer);
        final long end = ptr + commandBuffer.limit();
        while (ptr < end) {
            final int cmd = memGetInt(ptr);
            counts.mergeInt(cmd, 1, Integer::sum);
            ptr += GLCommand.getCommandSize(cmd, ptr);
        }
        return counts;
    }

    /**
     * Get the sequence of command opcodes in order.
     * Used for testing to verify command ordering.
     */
    public IntList getCommandOpcodes() {
        final IntList opcodes = new IntArrayList();
        if (commandBuffer == null || commandBuffer.limit() == 0) {
            return opcodes;
        }
        long ptr = memAddress(commandBuffer);
        final long end = ptr + commandBuffer.limit();
        while (ptr < end) {
            final int cmd = memGetInt(ptr);
            opcodes.add(cmd);
            ptr += GLCommand.getCommandSize(cmd, ptr);
        }
        return opcodes;
    }

    @Override
    public String toString() {
        return DisplayListManager.getCompiledDisplayListString(0, this, null);
    }
}
