package com.gtnewhorizons.angelica.glsm.backend;

import java.util.function.IntSupplier;

/**
 * How to replay a graphics program's vertex-stage image writes.
 *
 * @param inputLocations    per input, the declared location or -1 for whatever the graphics program linked
 * @param images            per image the replay uses, in binding order: the written ones, then the read-only ones
 * @param writtenImageCount how many of {@code images} the replay writes
 * @param samplerNames      samplers the graphics program may not have, because only the stripped writes read them
 * @param samplerUnits      per sampler name, the texture unit it reads at each draw, or -1 to use samplerTextures
 * @param samplerTextures   per sampler name, its texture when samplerUnits is -1
 * @param samplerObjects    per sampler name, the GL sampler object to sample it with when samplerUnits is -1, or 0
 * @param vertexBufferBinding storage buffer binding of the first vertex buffer the replay program declares
 * @param vertexBufferCount   how many vertex buffers it declares, at consecutive bindings
 * @param indexBufferBinding  storage buffer binding of its index buffer
 */
public record VertexWriteReplaySetup(int replayProgram, String[] inputNames, int[] inputLocations,
                                     IntSupplier[] images, int writtenImageCount,
                                     String[] samplerNames, int[] samplerUnits, IntSupplier[] samplerTextures, int[] samplerObjects,
                                     int vertexBufferBinding, int vertexBufferCount, int indexBufferBinding) {
}
