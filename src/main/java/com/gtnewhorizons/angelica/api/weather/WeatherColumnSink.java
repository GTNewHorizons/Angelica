package com.gtnewhorizons.angelica.api.weather;

import net.minecraft.util.ResourceLocation;

public interface WeatherColumnSink {

    // scrollPeriod is a power of two no greater than 1024.
    void beginLayer(ResourceLocation texture, int scrollPeriod, float uDriftScale, float vDriftScale, float fadeScale);

    // x and z are world block coordinates; brightness is a packed lightmap coordinate below 2^24.
    void column(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift, int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a);
}
