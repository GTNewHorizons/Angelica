package com.gtnewhorizons.angelica.compat;

import java.util.function.Function;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;
import com.gtnewhorizons.angelica.client.font.ColorCodeUtils;
import com.gtnewhorizons.angelica.config.AngelicaConfig;

public class GTNHLibCompat {

    public static final boolean HAS_TEXT_PREPROCESSOR;

    static {
        boolean found = false;
        try {
            FontRendering.class.getMethod("setTextPreprocessor", Function.class);
            found = true;
        } catch (NoSuchMethodException ignored) {}
        HAS_TEXT_PREPROCESSOR = found;
    }

    /** Only call when {@link #HAS_TEXT_PREPROCESSOR} is true. */
    public static void registerPreprocessor() {
        FontRendering.setTextPreprocessor(new FontRendering.TextPreprocessor() {

            @Override
            public String apply(String text) {
                return AngelicaConfig.enableAmpersandConversion
                        ? ColorCodeUtils.convertAmpersandToSectionX(text)
                        : text;
            }

            @Override
            public boolean handlesAmpCodes() {
                return AngelicaConfig.enableAmpersandConversion;
            }
        });
    }
}
