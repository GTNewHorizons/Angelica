package com.gtnewhorizons.angelica.compat.hextext;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.compat.hextext.HexTextCompat.Bridge;
import kamkeel.hextext.client.render.RenderTextData;
import kamkeel.hextext.client.render.RenderTextProcessor;

/**
 * Bridges render preprocessing information from HexText to Angelica's colour resolver.
 */
final class HexTextRenderBridge implements Bridge {

    @Override
    public RenderTextData prepare(CharSequence text, boolean rawMode) {
        String asString = text == null ? "" : text.toString();
        try {
            return RenderTextProcessor.prepare(asString, rawMode);
        } catch (Throwable t) {
            ModStatus.LOGGER.warn("Failed to query HexText render data", t);
            return RenderTextData.unchanged();
        }
    }
}
