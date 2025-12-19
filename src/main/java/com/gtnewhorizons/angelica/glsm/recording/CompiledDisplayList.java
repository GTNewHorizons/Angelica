package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.BigVBO;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
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
    private final ByteBuffer commandBuffer;     // Off-heap command storage, must be freed
    private final Object[] complexObjects;      // Complex commands (TexImage2D, etc.)
    private final BigVBO ownedVbos;     // GPU resources referenced by index

    public CompiledDisplayList(ByteBuffer commandBuffer, Object[] complexObjects, BigVBO ownedVbos) {
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

    // === Test inspection methods ===

    /**
     * Get counts of each command type in the buffer.
     * Used for testing to verify optimization results.
     */
    public Int2IntMap getCommandCounts() {
        Int2IntMap counts = new Int2IntOpenHashMap();
        if (commandBuffer == null || commandBuffer.limit() == 0) {
            return counts;
        }
        long ptr = memAddress(commandBuffer);
        long end = ptr + commandBuffer.limit();
        while (ptr < end) {
            int cmd = memGetInt(ptr);
            counts.mergeInt(cmd, 1, Integer::sum);
            ptr += getCommandSize(cmd, ptr);
        }
        return counts;
    }

    /**
     * Get the sequence of command opcodes in order.
     * Used for testing to verify command ordering.
     */
    public IntList getCommandOpcodes() {
        IntList opcodes = new IntArrayList();
        if (commandBuffer == null || commandBuffer.limit() == 0) {
            return opcodes;
        }
        long ptr = memAddress(commandBuffer);
        long end = ptr + commandBuffer.limit();
        while (ptr < end) {
            int cmd = memGetInt(ptr);
            opcodes.add(cmd);
            ptr += getCommandSize(cmd, ptr);
        }
        return opcodes;
    }

    /**
     * Get size of a command in bytes (including the opcode).
     */
    private static int getCommandSize(int cmd, long ptr) {
        return switch (cmd) {
            // Single int commands (8 bytes)
            case GLCommand.ENABLE, GLCommand.DISABLE, GLCommand.CLEAR, GLCommand.CLEAR_STENCIL,
                 GLCommand.CULL_FACE, GLCommand.DEPTH_FUNC, GLCommand.SHADE_MODEL, GLCommand.LOGIC_OP,
                 GLCommand.MATRIX_MODE, GLCommand.ACTIVE_TEXTURE, GLCommand.USE_PROGRAM,
                 GLCommand.PUSH_ATTRIB, GLCommand.POP_ATTRIB, GLCommand.LOAD_IDENTITY,
                 GLCommand.PUSH_MATRIX, GLCommand.POP_MATRIX, GLCommand.STENCIL_MASK,
                 GLCommand.DEPTH_MASK, GLCommand.FRONT_FACE, GLCommand.POINT_SIZE, GLCommand.LINE_WIDTH,
                 GLCommand.CALL_LIST, GLCommand.COMPLEX_REF, GLCommand.DRAW_RANGE, GLCommand.BIND_VBO,
                 GLCommand.BIND_VAO -> 8;

            // Two int commands (12 bytes)
            case GLCommand.BIND_TEXTURE, GLCommand.POLYGON_MODE, GLCommand.COLOR_MATERIAL,
                 GLCommand.LINE_STIPPLE, GLCommand.STENCIL_MASK_SEPARATE, GLCommand.FOGI,
                 GLCommand.HINT, GLCommand.POLYGON_OFFSET, GLCommand.ALPHA_FUNC, GLCommand.FOGF,
                 GLCommand.LIGHT_MODELF, GLCommand.LIGHT_MODELI -> 12;

            // Three int commands (16 bytes)
            case GLCommand.STENCIL_FUNC, GLCommand.STENCIL_OP, GLCommand.TEX_PARAMETERI,
                 GLCommand.LIGHTF, GLCommand.LIGHTI,
                 GLCommand.MATERIALF, GLCommand.TEX_PARAMETERF, GLCommand.DRAW_ARRAYS -> 16;

            // Four int commands (20 bytes)
            case GLCommand.VIEWPORT, GLCommand.BLEND_FUNC, GLCommand.COLOR_MASK,
                 GLCommand.STENCIL_FUNC_SEPARATE, GLCommand.STENCIL_OP_SEPARATE,
                 GLCommand.COLOR, GLCommand.CLEAR_COLOR, GLCommand.BLEND_COLOR -> 20;

            // Double commands
            case GLCommand.TRANSLATE, GLCommand.SCALE -> 32;  // cmd + mode + 3 doubles
            case GLCommand.CLEAR_DEPTH -> 12;  // cmd + 1 double
            case GLCommand.ROTATE -> 40;  // cmd + mode + 4 doubles
            case GLCommand.ORTHO, GLCommand.FRUSTUM -> 52;  // cmd + 6 doubles

            // Matrix commands (72 bytes)
            case GLCommand.MULT_MATRIX, GLCommand.LOAD_MATRIX -> 72;

            // Buffer commands (variable, read count)
            case GLCommand.FOG, GLCommand.LIGHT_MODEL -> {
                int count = memGetInt(ptr + 8);  // pname at +4, count at +8
                yield 12 + count * 4;
            }
            case GLCommand.LIGHT, GLCommand.MATERIAL -> {
                int count = memGetInt(ptr + 12);  // light/face at +4, pname at +8, count at +12
                yield 16 + count * 4;
            }

            default -> throw new IllegalStateException("Unknown command: " + cmd);
        };
    }
}
