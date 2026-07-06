package com.gtnewhorizons.angelica.glsm.recording.support;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLSMExtension;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.CompiledDisplayList;
import com.gtnewhorizons.angelica.glsm.recording.commands.BatchedIndexedDrawCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(GLSMExtension.class)
public abstract class DisplayListTestFixture {

    public static final float[] DEFAULT_QUAD_POSITIONS = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
    public static final byte[][] DEFAULT_SOLID_COLORS = {
        {1, 1, 1, 1}, {2, 2, 2, 2}, {3, 3, 3, 3}, {4, 4, 4, 4}
    };

    public static final int POS_COLOR_STRIDE = 12;

    private final List<Integer> listsToCleanup = new ArrayList<>();
    private final List<Integer> buffersToCleanup = new ArrayList<>();
    private final List<Integer> vaosToCleanup = new ArrayList<>();

    @AfterEach
    void cleanupFixtureResources() {
        for (int list : listsToCleanup) GLStateManager.glDeleteLists(list, 1);
        for (int buf : buffersToCleanup) {
            if (GL15.glIsBuffer(buf)) GL15.glDeleteBuffers(buf);
        }
        for (int vao : vaosToCleanup) {
            if (GL30.glIsVertexArray(vao)) GLStateManager.glDeleteVertexArrays(vao);
        }
        listsToCleanup.clear();
        buffersToCleanup.clear();
        vaosToCleanup.clear();
    }

    protected final int newList() {
        final int id = GL11.glGenLists(1);
        listsToCleanup.add(id);
        return id;
    }

    protected final void untrackList(int list) {
        listsToCleanup.remove((Integer) list);
    }

    protected final int newBuffer() {
        final int id = GL15.glGenBuffers();
        buffersToCleanup.add(id);
        return id;
    }

    protected final int newVao() {
        final int id = GL30.glGenVertexArrays();
        vaosToCleanup.add(id);
        return id;
    }

    /** 2 floats pos + 4 bytes color, stride 12, one vertex per entry in {@code colors}. */
    protected final int uploadPosColorVbo(float[] positions, byte[][] colors) {
        if (positions.length / 2 != colors.length) {
            throw new IllegalArgumentException("positions/colors length mismatch");
        }
        final int n = colors.length;
        final int vbo = newBuffer();
        final ByteBuffer data = MemoryUtilities.memAlloc(n * POS_COLOR_STRIDE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < n; i++) {
            data.putFloat(positions[i * 2]);
            data.putFloat(positions[i * 2 + 1]);
            data.put(colors[i][0]);
            data.put(colors[i][1]);
            data.put(colors[i][2]);
            data.put(colors[i][3]);
        }
        data.flip();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        MemoryUtilities.memFree(data);
        return vbo;
    }

    /** Pos+color VBO populated with {@link #DEFAULT_QUAD_POSITIONS} / {@link #DEFAULT_SOLID_COLORS}. */
    protected final int defaultQuadVbo() {
        return uploadPosColorVbo(DEFAULT_QUAD_POSITIONS, DEFAULT_SOLID_COLORS);
    }

    protected final int uploadEbo(int indexType, int... values) {
        final int bytesPer = switch (indexType) {
            case GL11.GL_UNSIGNED_BYTE -> 1;
            case GL11.GL_UNSIGNED_SHORT -> 2;
            case GL11.GL_UNSIGNED_INT -> 4;
            default -> throw new IllegalArgumentException("unsupported indexType 0x" + Integer.toHexString(indexType));
        };
        final int ebo = newBuffer();
        final ByteBuffer data = MemoryUtilities.memAlloc(values.length * bytesPer).order(ByteOrder.nativeOrder());
        for (int v : values) {
            switch (indexType) {
                case GL11.GL_UNSIGNED_BYTE -> data.put((byte) v);
                case GL11.GL_UNSIGNED_SHORT -> data.putShort((short) v);
                case GL11.GL_UNSIGNED_INT -> data.putInt(v);
            }
        }
        data.flip();
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        MemoryUtilities.memFree(data);
        return ebo;
    }

    /** VAO with loc0 = 2*FLOAT pos, loc1 = 4*UNSIGNED_BYTE norm color, stride 12. */
    protected final int setupPosColorVao(int vbo) {
        final int vao = newVao();
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, POS_COLOR_STRIDE, 0);
        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, POS_COLOR_STRIDE, 8);
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.glBindVertexArray(0);
        return vao;
    }

    /**
     * Record one {@code glDrawElements} into a {@code GL_COMPILE} list with the given VAO and EBO
     * already-bound setup. Returns the list id (tracked for cleanup).
     */
    protected final int recordSingleIndexedDraw(int vao, int ebo, int drawMode, int indexCount, int indexType) {
        final int list = newList();
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLStateManager.glDrawElements(drawMode, indexCount, indexType, 0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glEndList();
        return list;
    }

    protected static CompiledDisplayList compiledOf(int listId) {
        final CompiledDisplayList compiled = DisplayListManager.getDisplayList(listId);
        assertNotNull(compiled, "compiled display list must exist for id " + listId);
        return compiled;
    }

    protected static List<BatchedIndexedDrawCmd> allBatchedCmds(int listId) {
        final List<BatchedIndexedDrawCmd> out = new ArrayList<>();
        final Object[] complex = compiledOf(listId).getComplexObjects();
        if (complex == null) return out;
        for (Object o : complex) {
            if (o instanceof BatchedIndexedDrawCmd b) out.add(b);
        }
        return out;
    }

    protected static BatchedIndexedDrawCmd firstBatchedCmd(int listId) {
        final List<BatchedIndexedDrawCmd> cmds = allBatchedCmds(listId);
        return cmds.isEmpty() ? null : cmds.getFirst();
    }

    protected static List<IndexedDrawBatch> batchesOf(int listId) {
        return compiledOf(listId).getIndexedBatches();
    }

    protected static IndexedDrawBatch firstBatch(int listId) {
        return batchesOf(listId).getFirst();
    }

    protected static ByteBuffer readBufferBytes(int target, int bufferId, int size) {
        final int prev;
        if (target == GL15.GL_ARRAY_BUFFER) prev = GLStateManager.getBoundVBO();
        else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) prev = GLStateManager.getBoundEBO();
        else prev = 0;

        GLStateManager.glBindBuffer(target, bufferId);
        final ByteBuffer buf = MemoryUtilities.memAlloc(size).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(target, 0, buf);
        GLStateManager.glBindBuffer(target, prev);
        return buf;
    }

    protected static void overwriteBufferWithGarbage(int target, int bufferId, int size) {
        final ByteBuffer garbage = MemoryUtilities.memAlloc(size);
        for (int i = 0; i < garbage.capacity(); i++) garbage.put(i, (byte) 0xAA);
        GLStateManager.glBindBuffer(target, bufferId);
        GL15.glBufferData(target, garbage, GL15.GL_STATIC_DRAW);
        GLStateManager.glBindBuffer(target, 0);
        MemoryUtilities.memFree(garbage);
    }
}
