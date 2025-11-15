package com.gtnewhorizons.angelica.client.font.color;

/**
 * Parses formatting markers into a {@link ResolvedText} description that the
 * batching font renderer can consume.
 */
public interface AngelicaColorResolver {

    ResolvedText resolve(CharSequence text, int start, int end, int baseColor, int baseShadowColor);
}
