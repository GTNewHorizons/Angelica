package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLSMFog;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;

public class AngelicaFogService implements FogService {
    public static final AngelicaFogService INSTANCE = new AngelicaFogService();

    private final float[] fogColorArray = new float[4];

    @Override
    public float getFogEnd() {
        return GLSMFog.getFogState().getEnd();
    }

    @Override
    public float getFogStart() {
        return GLSMFog.getFogState().getStart();
    }

    @Override
    public float getFogDensity() {
        return GLSMFog.getFogState().getDensity();
    }

    @Override
    public int getFogShapeIndex() {
        return 0; // 1.7.10 only has one fog shape
    }

    @Override
    public float getFogCutoff() {
        return getFogEnd();
    }

    @Override
    public float[] getFogColor() {
        final var fogState = GLSMFog.getFogState();
        final var color = fogState.getFogColor();
        fogColorArray[0] = (float) color.x;
        fogColorArray[1] = (float) color.y;
        fogColorArray[2] = (float) color.z;
        fogColorArray[3] = fogState.getFogAlpha();
        return fogColorArray;
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (!GLSMFog.getFogMode().isEnabled()) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GLSMFog.getFogState().getFogMode());
    }
}
