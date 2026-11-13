package com.gtnewhorizons.angelica.api.weather;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;

public interface WeatherColumnProvider {

    // False suppresses vanilla rain and snow in this dimension.
    boolean rendersVanillaWeather();

    // Called each instanced weather frame before columnRevision and collectColumns; returns the tick counter that drives scroll and drift.
    int beginFrame(WorldClient world, EntityLivingBase view, float partialTicks);

    // Must change whenever any collected column changes; columns are re-collected only then or when the dimension or the player's block X/Z changes.
    int columnRevision();

    // Finite positive block distance d0 in alpha = ((1 - (d / d0)^2) * fadeScale + 0.5) * a.
    float fadeRadius();

    void collectColumns(WeatherColumnSink sink);
}
