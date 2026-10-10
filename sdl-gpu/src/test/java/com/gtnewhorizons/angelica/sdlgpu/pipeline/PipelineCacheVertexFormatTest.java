package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.stream.Stream;

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

    static Stream<Arguments> formats() {
        return Stream.of(
            Arguments.of("Float1", SDL_GPU_VERTEXELEMENTFORMAT_FLOAT, 1, GL11.GL_FLOAT, false, false),
            Arguments.of("Float2", SDL_GPU_VERTEXELEMENTFORMAT_FLOAT2, 2, GL11.GL_FLOAT, false, false),
            Arguments.of("Float3", SDL_GPU_VERTEXELEMENTFORMAT_FLOAT3, 3, GL11.GL_FLOAT, false, false),
            Arguments.of("Float4", SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4, 4, GL11.GL_FLOAT, false, false),
            Arguments.of("UByte4", SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, 4, GL11.GL_UNSIGNED_BYTE, false, false),
            Arguments.of("UByte4Norm", SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM, 4, GL11.GL_UNSIGNED_BYTE, true, false),
            Arguments.of("UByte4Integer", SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4, 4, GL11.GL_UNSIGNED_BYTE, false, true),
            Arguments.of("Short2", SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM, 2, GL11.GL_SHORT, false, false),
            Arguments.of("Short2Norm", SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM, 2, GL11.GL_SHORT, true, false),
            Arguments.of("Short4", SDL_GPU_VERTEXELEMENTFORMAT_SHORT4_NORM, 4, GL11.GL_SHORT, false, false),
            Arguments.of("Short4Integer", SDL_GPU_VERTEXELEMENTFORMAT_SHORT4, 4, GL11.GL_SHORT, false, true),
            Arguments.of("Int1", SDL_GPU_VERTEXELEMENTFORMAT_INT, 1, GL11.GL_INT, false, false),
            Arguments.of("Int4", SDL_GPU_VERTEXELEMENTFORMAT_INT4, 4, GL11.GL_INT, false, false),
            Arguments.of("UInt2", SDL_GPU_VERTEXELEMENTFORMAT_UINT2, 2, GL11.GL_UNSIGNED_INT, false, false),
            Arguments.of("Half2", SDL_GPU_VERTEXELEMENTFORMAT_HALF2, 2, GL30.GL_HALF_FLOAT, false, false),
            Arguments.of("Half4", SDL_GPU_VERTEXELEMENTFORMAT_HALF4, 4, GL30.GL_HALF_FLOAT, false, false),
            Arguments.of("Byte4", SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM, 4, GL11.GL_BYTE, false, false),
            Arguments.of("Byte4Norm", SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM, 4, GL11.GL_BYTE, true, false),
            Arguments.of("Byte4Integer", SDL_GPU_VERTEXELEMENTFORMAT_BYTE4, 4, GL11.GL_BYTE, false, true),
            Arguments.of("UShort2Norm", SDL_GPU_VERTEXELEMENTFORMAT_USHORT2_NORM, 2, GL11.GL_UNSIGNED_SHORT, true, false),
            Arguments.of("UShort4", SDL_GPU_VERTEXELEMENTFORMAT_USHORT4_NORM, 4, GL11.GL_UNSIGNED_SHORT, false, false),
            Arguments.of("UShort4Integer", SDL_GPU_VERTEXELEMENTFORMAT_USHORT4, 4, GL11.GL_UNSIGNED_SHORT, false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formats")
    void mapsToSdlFormat(String name, int expected, int size, int glType, boolean normalized, boolean integer) {
        assertEquals(expected, PipelineCache.mapVertexFormat(size, glType, normalized, integer));
    }
}
