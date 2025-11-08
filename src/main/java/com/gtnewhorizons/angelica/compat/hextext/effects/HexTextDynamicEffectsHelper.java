package com.gtnewhorizons.angelica.compat.hextext.effects;

import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat;
import kamkeel.hextext.api.rendering.DynamicEffectService;

/**
 * Wrapper around HexText's dynamic text effect computations.
 */
public final class HexTextDynamicEffectsHelper implements HexTextCompat.EffectsHelper {

    private final DynamicEffectService dynamicService;
    private final boolean active;

    public HexTextDynamicEffectsHelper(DynamicEffectService dynamicService) {
        this.dynamicService = dynamicService;
        this.active = dynamicService != null;
    }

    @Override
    public int computeRainbowColor(long now, int glyphIndex, int anchorIndex) {
        if (!active) {
            return 0;
        }
        return dynamicService.computeRainbowColor(now, glyphIndex, anchorIndex);
    }

    @Override
    public int computeIgniteColor(long now, int baseColor) {
        if (!active) {
            return baseColor & 0x00FFFFFF;
        }
        return dynamicService.computeIgniteColor(now, baseColor);
    }

    @Override
    public float computeShakeOffset(long now, int glyphIndex) {
        if (!active) {
            return 0.0f;
        }
        return dynamicService.computeShakeOffset(now, glyphIndex);
    }

    @Override
    public boolean isOperational() {
        return active;
    }
}
