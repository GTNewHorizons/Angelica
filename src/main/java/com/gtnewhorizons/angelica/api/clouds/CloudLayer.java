package com.gtnewhorizons.angelica.api.clouds;

import net.minecraft.util.ResourceLocation;

public record CloudLayer(
    String id,
    ResourceLocation texture,
    float height,
    float cellWidth,
    float cellHeight,
    float coordinateWidth,
    double offsetX,
    double offsetZ,
    float red,
    float green,
    float blue,
    float alpha) {

    public CloudLayer {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Cloud layer id must not be empty");
        if (texture == null) throw new IllegalArgumentException("Cloud layer texture must not be null");
        if (!Float.isFinite(height)) throw new IllegalArgumentException("Cloud layer height must be finite");
        if (!Float.isFinite(cellWidth) || cellWidth <= 0
            || !Float.isFinite(cellHeight) || cellHeight <= 0
            || !Float.isFinite(coordinateWidth) || coordinateWidth <= 0) {
            throw new IllegalArgumentException("Cloud layer dimensions must be finite and positive");
        }
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetZ)) {
            throw new IllegalArgumentException("Cloud layer offsets must be finite");
        }
        if (!Float.isFinite(red) || red < 0 || !Float.isFinite(green) || green < 0
            || !Float.isFinite(blue) || blue < 0) {
            throw new IllegalArgumentException("Cloud layer colors must be finite and nonnegative");
        }
        if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Cloud layer alpha must be between zero and one");
        }
    }
}
