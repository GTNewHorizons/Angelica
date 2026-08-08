package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class PipelineCacheVertexFormatTest {

    private static int mapVertexFormat(int size, int glType, boolean normalized) {
        return PipelineCache.mapVertexFormat(size, glType, normalized, false);
    }

    @Test
    void requestedDiffersFromUsedForUnnormalizedUByte4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4, PipelineCache.requestedVertexFormat(4, GL11.GL_UNSIGNED_BYTE, false));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, false));
    }

    @Test
    void requestedDiffersFromUsedForUnnormalizedShort2AndUShort4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT2, PipelineCache.requestedVertexFormat(2, GL11.GL_SHORT, false));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_USHORT4, PipelineCache.requestedVertexFormat(4, GL11.GL_UNSIGNED_SHORT, false));
    }

    @Test
    void requestedMatchesUsedForFloatAndInt() {
        assertEquals(mapVertexFormat(4, GL11.GL_FLOAT, false), PipelineCache.requestedVertexFormat(4, GL11.GL_FLOAT, false));
        assertEquals(mapVertexFormat(4, GL11.GL_INT, false), PipelineCache.requestedVertexFormat(4, GL11.GL_INT, false));
    }

    @Test
    void requestedMatchesUsedWhenNormalizedWasAsked() {
        assertEquals(mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, true), PipelineCache.requestedVertexFormat(4, GL11.GL_UNSIGNED_BYTE, true));
    }

    @Test
    void requestedIsInvalidForPromotedSizes() {
        assertEquals(PipelineCache.INVALID_FMT, PipelineCache.requestedVertexFormat(3, GL11.GL_UNSIGNED_BYTE, false));
        assertEquals(PipelineCache.INVALID_FMT, PipelineCache.requestedVertexFormat(1, GL11.GL_SHORT, false));
    }

    @Test
    void integerRequestBypassesCoercionAtEveryConvertibleType() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4, PipelineCache.mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, false, true));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_BYTE4, PipelineCache.mapVertexFormat(4, GL11.GL_BYTE, false, true));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_USHORT2, PipelineCache.mapVertexFormat(2, GL11.GL_UNSIGNED_SHORT, false, true));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT2, PipelineCache.mapVertexFormat(2, GL11.GL_SHORT, false, true));
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_BYTE4, PipelineCache.mapVertexFormat(3, GL11.GL_BYTE, false, true));
    }

    @Test
    void requestedRejectsUnknownTypeAndSize() {
        assertEquals(PipelineCache.INVALID_FMT, PipelineCache.requestedVertexFormat(5, GL11.GL_FLOAT, false));
        assertEquals(PipelineCache.INVALID_FMT, PipelineCache.requestedVertexFormat(4, GL11.GL_DOUBLE, false));
    }

    @Test
    void testFloat1() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT, mapVertexFormat(1, GL11.GL_FLOAT, false));
    }

    @Test
    void testFloat2() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT2, mapVertexFormat(2, GL11.GL_FLOAT, false));
    }

    @Test
    void testFloat3() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT3, mapVertexFormat(3, GL11.GL_FLOAT, false));
    }

    @Test
    void testFloat4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, mapVertexFormat(4, GL11.GL_FLOAT, false));
    }

    @Test
    void testUByte4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, false));
    }

    @Test
    void testUByte4Norm() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, true));
    }

    @Test
    void testUByte4Integer() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4, PipelineCache.mapVertexFormat(4, GL11.GL_UNSIGNED_BYTE, false, true));
    }

    @Test
    void testShort2() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM, mapVertexFormat(2, GL11.GL_SHORT, false));
    }

    @Test
    void testShort2Norm() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM, mapVertexFormat(2, GL11.GL_SHORT, true));
    }

    @Test
    void testShort4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT4_NORM, mapVertexFormat(4, GL11.GL_SHORT, false));
    }

    @Test
    void testShort4Integer() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_SHORT4, PipelineCache.mapVertexFormat(4, GL11.GL_SHORT, false, true));
    }

    @Test
    void testInt1() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_INT, mapVertexFormat(1, GL11.GL_INT, false));
    }

    @Test
    void testInt4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_INT4, mapVertexFormat(4, GL11.GL_INT, false));
    }

    @Test
    void testUInt2() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_UINT2, mapVertexFormat(2, GL11.GL_UNSIGNED_INT, false));
    }

    @Test
    void testHalf2() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_HALF2, mapVertexFormat(2, GL30.GL_HALF_FLOAT, false));
    }

    @Test
    void testHalf4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_HALF4, mapVertexFormat(4, GL30.GL_HALF_FLOAT, false));
    }

    @Test
    void testByte4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM, mapVertexFormat(4, GL11.GL_BYTE, false));
    }

    @Test
    void testByte4Norm() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM, mapVertexFormat(4, GL11.GL_BYTE, true));
    }

    @Test
    void testByte4Integer() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_BYTE4, PipelineCache.mapVertexFormat(4, GL11.GL_BYTE, false, true));
    }

    @Test
    void testUShort2Norm() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_USHORT2_NORM, mapVertexFormat(2, GL11.GL_UNSIGNED_SHORT, true));
    }

    @Test
    void testUShort4() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_USHORT4_NORM, mapVertexFormat(4, GL11.GL_UNSIGNED_SHORT, false));
    }

    @Test
    void testUShort4Integer() {
        assertEquals(SDL_GPU_VERTEXELEMENTFORMAT_USHORT4, PipelineCache.mapVertexFormat(4, GL11.GL_UNSIGNED_SHORT, false, true));
    }
}
