package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizons.angelica.config.FontConfig;

record GlyphScale(int scaleBits, int aspectBits, float x, float y) {

    private static GlyphScale current = of(FontConfig.glyphScale, FontConfig.glyphAspect);

    private static GlyphScale of(float scale, float aspect) {
        final double pow = Math.pow(2, aspect);
        return new GlyphScale(Float.floatToRawIntBits(scale), Float.floatToRawIntBits(aspect), (float) (scale * pow), (float) (scale / pow));
    }

    static GlyphScale current() {
        final float scale = FontConfig.glyphScale;
        final float aspect = FontConfig.glyphAspect;
        GlyphScale holder = current;
        if (holder.scaleBits != Float.floatToRawIntBits(scale) || holder.aspectBits != Float.floatToRawIntBits(aspect)) {
            holder = of(scale, aspect);
            current = holder;
        }
        return holder;
    }
}
