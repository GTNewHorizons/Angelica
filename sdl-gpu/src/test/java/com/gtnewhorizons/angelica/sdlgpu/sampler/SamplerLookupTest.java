package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SamplerLookupTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void movedAndUnmovedSamplersShareDefaultsAndExplicitAssignments(boolean fragment) {
        final ShaderManager.ProgramObject ordinary = program(fragment,
            List.of("tex", "lightmap", "normals", "specular", "custom"), List.of(), new boolean[0]);
        final ShaderManager.ProgramObject overflow = program(fragment, List.of("tex"),
            List.of("storageImage", "custom", "specular", "lightmap", "normals"), new boolean[]{false, true, true, true, true});

        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, SamplerLookup.resolvedUnits(ordinary, fragment));
        assertArrayEquals(new int[]{0}, SamplerLookup.resolvedUnits(overflow, fragment));
        assertArrayEquals(new int[]{-1, 0, 0, 0, 0}, SamplerLookup.movedSamplerUnits(overflow, fragment),
            "moving a sampler must preserve its default, while real storage images do not resolve sampler units");

        for (ShaderManager.ProgramObject prog : List.of(ordinary, overflow)) {
            prog.samplerTextureUnits.put("lightmap", 1);
            prog.samplerTextureUnits.put("normals", 2);
            prog.samplerTextureUnits.put("specular", 3);
            prog.samplerTextureUnits.put("custom", 9);
            prog.samplerUnitsDirty = true;
        }
        assertArrayEquals(new int[]{0, 1, 2, 3, 9}, SamplerLookup.resolvedUnits(ordinary, fragment));
        assertArrayEquals(new int[]{-1, 9, 3, 1, 2}, SamplerLookup.movedSamplerUnits(overflow, fragment));
    }

    private static ShaderManager.ProgramObject program(boolean fragment, List<String> samplers, List<String> images, boolean[] moved) {
        final ShaderManager.ProgramObject prog = new ShaderManager.ProgramObject();
        final ShaderManager.ResourceCounts resources = new ShaderManager.ResourceCounts(samplers.size(), 0, 0, images.size());
        if (fragment) {
            prog.fragmentResources = resources;
            prog.fragmentSamplerNames = samplers;
            prog.fragmentImageNames = images;
            prog.fragmentImageIsMovedSampler = moved;
            prog.fragmentMovedSamplerRead = moved;
        } else {
            prog.vertexResources = resources;
            prog.vertexSamplerNames = samplers;
            prog.vertexImageNames = images;
            prog.vertexImageIsMovedSampler = moved;
            prog.vertexMovedSamplerRead = moved;
        }
        prog.samplerUnitsDirty = true;
        return prog;
    }
}
