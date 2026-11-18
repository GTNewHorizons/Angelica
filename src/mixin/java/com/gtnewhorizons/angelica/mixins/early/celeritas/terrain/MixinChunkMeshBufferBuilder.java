package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bilinear AO correction assumes the four vertices are the corners of a parallelogram, in order: it derives the
 * twist from c1 + c3 - c0 - c2 and the shader weights it by each fragment's position in that parallelogram. A triangle
 * sent through the tessellator as a quad with a repeated vertex has no such parallelogram, so the computed twist is just
 * the difference between two of its corners, and the correction lands along an edge the neighbouring triangle does not
 * correct. That shows up as a visible brightness step along every such edge.
 */
@Mixin(value = ChunkMeshBufferBuilder.class, remap = false)
public abstract class MixinChunkMeshBufferBuilder {

    @Inject(method = "postprocessVertices", at = @At("HEAD"), cancellable = true)
    private static void angelica$skipCorrectionForTriangles(ChunkVertexEncoder.Vertex[] vertices, CallbackInfo ci) {
        if (vertices.length != 4 || !angelica$hasRepeatedVertex(vertices)) {
            return;
        }
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            vertex.rdhFactor = 0;
        }
        ci.cancel();
    }

    @Unique
    private static boolean angelica$hasRepeatedVertex(ChunkVertexEncoder.Vertex[] vertices) {
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                final ChunkVertexEncoder.Vertex a = vertices[i];
                final ChunkVertexEncoder.Vertex b = vertices[j];
                if (a.x == b.x && a.y == b.y && a.z == b.z) {
                    return true;
                }
            }
        }
        return false;
    }
}
