package com.gtnewhorizons.angelica.client.font;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;

public final class AngelicaTextPreprocessor implements FontRendering.TextPreprocessor {

    @Override
    public String apply(final String text) {
        return ColorCodeUtils.convertImpl(text);
    }

    @Override
    public boolean handlesAmpCodes() {
        return true;
    }
}
