package com.gtnewhorizons.angelica.sdlgpu.shader;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class UniformStaging {
    public ByteBuffer vsUniformBuf;
    public FloatBuffer vsUniformFb;
    public ByteBuffer fsUniformBuf;
    public FloatBuffer fsUniformFb;
    public boolean vsUniformDirty;
    public boolean fsUniformDirty;
    public float[][] uniformDataBySlot;
    public long[] uniformValueHashBySlot;

    public void allocate(ShaderManager.ProgramObject prog) {
        if (prog.vertexUboSize > 0) {
            vsUniformBuf = MemoryUtil.memCalloc(prog.vertexUboSize);
            vsUniformFb = vsUniformBuf.asFloatBuffer();
        }
        if (prog.fragmentUboSize > 0) {
            fsUniformBuf = MemoryUtil.memCalloc(prog.fragmentUboSize);
            fsUniformFb = fsUniformBuf.asFloatBuffer();
        }
        uniformDataBySlot = new float[prog.uniformSlotCount][];
        uniformValueHashBySlot = new long[prog.uniformSlotCount];
    }

    public void free() {
        if (vsUniformBuf != null) { MemoryUtil.memFree(vsUniformBuf); vsUniformBuf = null; vsUniformFb = null; }
        if (fsUniformBuf != null) { MemoryUtil.memFree(fsUniformBuf); fsUniformBuf = null; fsUniformFb = null; }
    }
}
