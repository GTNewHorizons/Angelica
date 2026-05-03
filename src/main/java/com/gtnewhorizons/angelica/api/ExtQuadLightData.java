package com.gtnewhorizons.angelica.api;

/**
 * Interface for {@code QuadLightData} providing per-vertex RGB light values.
 */
public interface ExtQuadLightData {

    float[] angelica$getR();
    float[] angelica$getG();
    float[] angelica$getB();

    boolean angelica$isRGBValid();
    void angelica$setRGBValid(boolean valid);

    float[] angelica$getSkyR();
    float[] angelica$getSkyG();
    float[] angelica$getSkyB();

    boolean angelica$isSkyRGBValid();
    void angelica$setSkyRGBValid(boolean valid);
}
