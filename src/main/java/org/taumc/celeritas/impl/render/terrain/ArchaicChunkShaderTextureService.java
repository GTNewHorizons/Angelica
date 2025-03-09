package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;

public class ArchaicChunkShaderTextureService implements ChunkShaderTextureService {
    @Override
    public int bindAndGetUniformValue(ChunkShaderTextureSlot textureSlot) {
        return switch (textureSlot) {
            case BLOCK -> 0;
            case LIGHT -> 1;
        };
    }
}
