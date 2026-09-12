package com.gtnewhorizons.angelica.client.font;

/**
 * A per-glyph text effect another mod can attach to an unused {@code §} format code through
 * {@link FontEffectRegistry}. While the code is active the batched font renderer invokes the
 * effect for every visible glyph it emits.
 * <p>
 * The format code toggles the effect, and any colour code ({@code §0}-{@code §f}, {@code §x},
 * {@code §g}, {@code §q}) or {@code §r} clears it, as with the renderer's own colour-bound
 * effects. Wave and dinnerbone are positional and leave it alone.
 */
public interface CustomGlyphEffect {

    /**
     * Transforms the colour a glyph is about to be drawn with. Called once for the main pass and
     * once for the shadow pass (receiving the already-darkened shadow colour). Implementations
     * should preserve the alpha channel of the input.
     */
    int transformColor(int argb, boolean shadowPass, int glyphIndex);

    /**
     * Quad drawn behind the glyph as {@code 0xAARRGGBB}, or {@code 0} for none. Lets an
     * effect mark a span without taking over the text colour. Where two active effects
     * both draw one, the later format-code letter wins.
     */
    default int backgroundColor(int glyphIndex) {
        return 0;
    }

    /** Horizontal offset added to the glyph quad, in text-space pixels. */
    default float offsetX(int glyphIndex) {
        return 0.0f;
    }

    /** Vertical offset added to the glyph quad, in text-space pixels. */
    default float offsetY(int glyphIndex) {
        return 0.0f;
    }
}
