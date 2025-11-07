package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat.EffectsHelper;
import kamkeel.hextext.common.util.ColorMath;
import kamkeel.hextext.common.util.TextEffectMath;
import kamkeel.hextext.config.HexTextConfig;

/**
 * Wrapper around HexText's dynamic text effect computations.
 */
final class HexTextDynamicEffectsHelper implements EffectsHelper {

    private static final float RAINBOW_SPREAD = 12.0f;
    private static final float SHAKE_VERTICAL_RANGE = 1.05f;
    private static final long SHAKE_Y_SALT = 0xC6A4A7935BD1E995L;
    private static final float IGNITE_MIN_FACTOR = 0.35f;

    @Override
    public int computeRainbowColor(long now, int glyphIndex, int anchorIndex) {
        return TextEffectMath.computeRainbowColor(
            now,
            HexTextConfig.getRainbowSpeed(),
            glyphIndex,
            anchorIndex,
            RAINBOW_SPREAD
        );
    }

    @Override
    public int computeIgniteColor(long now, int baseColor) {
        float brightness = TextEffectMath.computeIgniteBrightness(
            now,
            HexTextConfig.getIgniteInterval(),
            IGNITE_MIN_FACTOR
        );
        return ColorMath.scaleBrightness(baseColor, brightness);
    }

    @Override
    public float computeShakeOffset(long now, int glyphIndex) {
        long seed = TextEffectMath.computeShakeSeed(glyphIndex, now, HexTextConfig.getShakeInterval());
        return TextEffectMath.computeShakeOffset(seed ^ SHAKE_Y_SALT, SHAKE_VERTICAL_RANGE);
    }
}
