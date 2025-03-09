package org.embeddedt.embeddium.impl.render.chunk.fog;

import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;

public interface FogService {
    float getFogEnd();
    float getFogStart();
    float getFogDensity();
    int getFogShapeIndex();
    float getFogCutoff();
    float[] getFogColor();
    ChunkFogMode getFogMode();
}
