package org.embeddedt.embeddium.impl.gl.tessellation;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeBinding;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

public abstract class GlAbstractTessellation implements GlTessellation {
    protected final TessellationBinding[] bindings;

    protected GlAbstractTessellation(TessellationBinding[] bindings) {
        this.bindings = bindings;
    }

    protected void bindAttributes(CommandList commandList) {
        for (TessellationBinding binding : this.bindings) {
            commandList.bindBuffer(binding.target(), binding.buffer());

            for (GlVertexAttributeBinding attrib : binding.attributeBindings()) {
                if (attrib.isIntType()) {
                    GL30C.glVertexAttribIPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat().typeId(),
                            attrib.getStride(), attrib.getPointer());
                } else {
                    GL20C.glVertexAttribPointer(attrib.getIndex(), attrib.getCount(), attrib.getFormat().typeId(), attrib.isNormalized(),
                            attrib.getStride(), attrib.getPointer());
                }
                GL20C.glEnableVertexAttribArray(attrib.getIndex());
            }
        }
    }
}
