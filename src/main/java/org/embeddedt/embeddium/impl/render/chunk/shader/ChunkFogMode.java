package org.embeddedt.embeddium.impl.render.chunk.shader;

import com.google.common.collect.ImmutableList;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.function.Function;

public enum ChunkFogMode {
    NONE(ChunkShaderFogComponent.None::new, ImmutableList.of()),
    EXP2(ChunkShaderFogComponent.Exp2::new, ImmutableList.of("USE_FOG", "USE_FOG_EXP2")),
    SMOOTH(ChunkShaderFogComponent.Smooth::new, ImmutableList.of("USE_FOG", "USE_FOG_SMOOTH"));

    private final Function<ShaderBindingContext, ChunkShaderFogComponent> factory;
    private final List<String> defines;

    ChunkFogMode(Function<ShaderBindingContext, ChunkShaderFogComponent> factory, List<String> defines) {
        this.factory = factory;
        this.defines = defines;
    }

    public Function<ShaderBindingContext, ChunkShaderFogComponent> getFactory() {
        return this.factory;
    }

    public List<String> getDefines() {
        return this.defines;
    }

    public static ChunkFogMode fromGLMode(int mode) {
        switch (mode) {
            case 0:
                return ChunkFogMode.NONE;
            case GL20.GL_EXP2:
            case GL20.GL_EXP:
                return ChunkFogMode.EXP2;
            case GL20.GL_LINEAR:
                return ChunkFogMode.SMOOTH;
            default:
                throw new UnsupportedOperationException("Unknown fog mode: " + mode);
        }
    }
}
