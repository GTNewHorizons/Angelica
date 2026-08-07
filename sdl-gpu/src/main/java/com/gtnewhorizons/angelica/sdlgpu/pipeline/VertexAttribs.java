package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VertexAttribs {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private final Set<Long> warnedDivisorKeys = ConcurrentHashMap.newKeySet();

    public VertexAttribs() {
    }

    public void applyVertexAttribPointer(ContextState st, int index, int size, int type, boolean normalized, boolean isInteger, int stride, long pointer) {
        if (index >= ContextState.MAX_VERTEX_ATTRIBS) return;
        final ContextState.VAOState vao = st.currentVao;
        final int vbo = st.boundArrayBuffer;
        if (vao.attribSize[index] == size && vao.attribType[index] == type && vao.attribNormalized[index] == normalized
                && vao.attribIsInteger[index] == isInteger && vao.attribStride[index] == stride && vao.attribOffset[index] == pointer
                && vao.attribVBO[index] == vbo && vao.attribBinding[index] == index && vao.attribRelativeOffset[index] == 0
                && vao.bindingBuffer[index] == vbo && vao.bindingOffset[index] == pointer && vao.bindingStride[index] == stride)
            return;
        vao.attribSize[index] = size;
        vao.attribType[index] = type;
        vao.attribNormalized[index] = normalized;
        vao.attribIsInteger[index] = isInteger;
        vao.attribStride[index] = stride;
        vao.attribOffset[index] = pointer;
        vao.attribVBO[index] = vbo;
        vao.attribBinding[index] = index;
        vao.attribRelativeOffset[index] = 0;
        vao.bindingBuffer[index] = vbo;
        vao.bindingOffset[index] = pointer;
        vao.bindingStride[index] = stride;
        st.bumpAttribStateGen();
        st.pipeline.markInputDirty();
    }

    public void warnDivisorClamp(int program, int attribIndex, int divisor) {
        final long key = Hashing.packHiLo(program, attribIndex);
        if (warnedDivisorKeys.add(key)) {
            LOG.warn("vertexAttribDivisor(prog={}, index={}, divisor={}): clamped to 1; SDL has no step>1.", program, attribIndex, divisor);
        }
    }
}
