package me.jellysquid.mods.sodium.client.gl.state;

import me.jellysquid.mods.sodium.client.gl.array.GlVertexArray;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBufferTarget;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

public class GlStateTracker {
    private static final int UNASSIGNED_HANDLE = -1;

    private final int[] bufferState = new int[GlBufferTarget.COUNT];
    //private final int[] bufferRestoreState = new int[GlBufferTarget.COUNT];

    private int vertexArrayState;
    //private int vertexArrayRestoreState;

    public GlStateTracker() {
        this.clearRestoreState();
    }

    public boolean makeBufferActive(GlBufferTarget target, GlBuffer buffer) {
        return this.makeBufferActive(target, buffer == null ? GlBuffer.NULL_BUFFER_ID : buffer.handle());
    }

    private boolean makeBufferActive(GlBufferTarget target, int buffer) {
        int prevBuffer = this.bufferState[target.ordinal()];

        this.bufferState[target.ordinal()] = buffer;

        return prevBuffer != buffer;
    }

    public boolean makeVertexArrayActive(GlVertexArray array) {
        return this.makeVertexArrayActive(array == null ? GlVertexArray.NULL_ARRAY_ID : array.handle());
    }

    private boolean makeVertexArrayActive(int array) {
        int prevArray = this.vertexArrayState;

        this.vertexArrayState = array;

        return prevArray != array;
    }

    public void applyRestoreState() {
        for (int i = 0; i < GlBufferTarget.COUNT; i++) {
            GL15.glBindBuffer(GlBufferTarget.VALUES[i].getTargetParameter(), 0);
        }

        GL30.glBindVertexArray(0);
    }

    public void clearRestoreState() {
        Arrays.fill(this.bufferState, UNASSIGNED_HANDLE);
        //Arrays.fill(this.bufferRestoreState, UNASSIGNED_HANDLE);

        this.vertexArrayState = UNASSIGNED_HANDLE;
        //this.vertexArrayRestoreState = UNASSIGNED_HANDLE;
    }
}
