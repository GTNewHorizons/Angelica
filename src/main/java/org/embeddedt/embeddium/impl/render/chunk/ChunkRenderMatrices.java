package org.embeddedt.embeddium.impl.render.chunk;

import org.joml.Matrix4fc;

public record ChunkRenderMatrices(Matrix4fc projection, Matrix4fc modelView) {

}
