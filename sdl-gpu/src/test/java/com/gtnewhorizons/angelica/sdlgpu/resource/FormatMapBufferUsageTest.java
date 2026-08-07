package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_WRITE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_INDEX;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_INDIRECT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_BUFFERUSAGE_VERTEX;

class FormatMapBufferUsageTest {

    @Test
    void geometryTargets_shareVertexAndIndexUsage() {
        final int expected = SDL_GPU_BUFFERUSAGE_VERTEX | SDL_GPU_BUFFERUSAGE_INDEX | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ;
        assertEquals(expected, FormatMap.mapBufferUsage(GL15.GL_ARRAY_BUFFER));
        assertEquals(expected, FormatMap.mapBufferUsage(GL15.GL_ELEMENT_ARRAY_BUFFER));
    }

    @Test
    void storageBufferTarget_hasNoVertexUsage() {
        assertEquals(0, FormatMap.mapBufferUsage(GL43.GL_SHADER_STORAGE_BUFFER) & SDL_GPU_BUFFERUSAGE_VERTEX);
    }

    @Test
    void drawIndirectBufferTarget_singleIndirectFlag() {
        assertEquals(SDL_GPU_BUFFERUSAGE_INDIRECT, FormatMap.mapBufferUsage(GL40.GL_DRAW_INDIRECT_BUFFER));
    }

    @Test
    void unknownTarget_multiFlagFallback() {
        final int expected = SDL_GPU_BUFFERUSAGE_VERTEX | SDL_GPU_BUFFERUSAGE_INDEX | SDL_GPU_BUFFERUSAGE_INDIRECT;
        assertEquals(expected, FormatMap.mapBufferUsage(0));
    }

    @Test
    void uniformAndStorageBufferTargets_preserved() {
        assertEquals(SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ, FormatMap.mapBufferUsage(GL31.GL_UNIFORM_BUFFER));

        final int ssbo = SDL_GPU_BUFFERUSAGE_GRAPHICS_STORAGE_READ
            | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_READ
            | SDL_GPU_BUFFERUSAGE_COMPUTE_STORAGE_WRITE
            | SDL_GPU_BUFFERUSAGE_INDIRECT;
        assertEquals(ssbo, FormatMap.mapBufferUsage(GL43.GL_SHADER_STORAGE_BUFFER));
    }
}
