package me.jellysquid.mods.sodium.client.gl.tessellation;

import lombok.Getter;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;

public class TessellationBinding {
    @Getter
    private final GlBuffer buffer;
    private final GlVertexAttributeBinding[] bindings;
    @Getter
    private final boolean instanced;

    public TessellationBinding(GlBuffer buffer, GlVertexAttributeBinding[] bindings, boolean instanced) {
        this.buffer = buffer;
        this.bindings = bindings;
        this.instanced = instanced;
    }

    public GlVertexAttributeBinding[] getAttributeBindings() {
        return this.bindings;
    }

}
