package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.List;

public final class SamplerLookup {
    private SamplerLookup() {}

    public static int[] resolvedUnits(ShaderManager.ProgramObject prog, boolean fragment) {
        if (prog.samplerUnitsDirty) {
            prog.vertexSamplerUnits = resolveStage(prog.vertexSamplerNames, prog.samplerTextureUnits, prog.vertexResources.numSamplers());
            prog.fragmentSamplerUnits = resolveStage(prog.fragmentSamplerNames, prog.samplerTextureUnits, prog.fragmentResources.numSamplers());
            prog.samplerUnitsDirty = false;
        }
        return fragment ? prog.fragmentSamplerUnits : prog.vertexSamplerUnits;
    }

    private static int[] resolveStage(List<String> samplerNames, Object2IntMap<String> textureUnits, int samplerCount) {
        if (samplerCount <= 0) return new int[0];
        final int[] units = new int[samplerCount];
        for (int i = 0; i < samplerCount; i++) {
            units[i] = getSamplerTextureUnit(samplerNames, textureUnits, i);
        }
        return units;
    }

    public static int getSamplerTextureUnit(List<String> samplerNames, Object2IntMap<String> textureUnits, int bindingIndex) {
        if (bindingIndex >= samplerNames.size()) return bindingIndex;
        final String name = samplerNames.get(bindingIndex);
        final int unit = textureUnits.getInt(name);
        if (unit != -1) return unit;
        return switch (name) {
            case "tex", "texture", "gtexture" -> 0;
            case "lightmap" -> 1;
            case "normals" -> 2;
            case "specular" -> 3;
            default -> bindingIndex;
        };
    }
}
