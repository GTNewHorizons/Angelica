package com.gtnewhorizons.angelica.api.tesr;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.concurrent.ConcurrentHashMap;

/** Per-bucket render attributes. Interned: equal settings build the same instance. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public final class TesrMaterial {

    public enum Transparency { OPAQUE, TRANSLUCENT, ADDITIVE }

    private static final ConcurrentHashMap<TesrMaterial, TesrMaterial> INTERNED = new ConcurrentHashMap<>();

    public static final TesrMaterial CURRENT_STATE = intern(new TesrMaterial(false, 0, 0, 0, 0, false, 0, 0, false, false, false, false, 0, false, Transparency.OPAQUE, null));

    private static TesrMaterial intern(TesrMaterial material) {
        final TesrMaterial existing = INTERNED.putIfAbsent(material, material);
        return existing == null ? material : existing;
    }

    private final boolean hasColor;
    private final float colorRed, colorGreen, colorBlue, colorAlpha;
    private final boolean hasLightmap;
    private final float lightmapX, lightmapY;
    private final boolean isNoCull;
    private final boolean isUnlit;
    private final boolean isNoDepthWrite;
    private final boolean isStream;
    private final float cutoutAlpha;
    private final boolean isDepthEqual;
    private final Transparency transparency;
    private final TesrShader shader;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean hasColor;
        private float red, green, blue, alpha;
        private boolean hasLightmap;
        private float lightmapX, lightmapY;
        private boolean noCull;
        private boolean unlit;
        private boolean noDepthWrite;
        private boolean stream;
        private float cutoutAlpha;
        private boolean depthEqual;
        private Transparency transparency = Transparency.OPAQUE;
        private TesrShader shader;

        public Builder color(float red, float green, float blue, float alpha) {
            this.hasColor = true;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            return this;
        }

        public Builder lightmap(float x, float y) {
            this.hasLightmap = true;
            this.lightmapX = x;
            this.lightmapY = y;
            return this;
        }

        public Builder noCull() {
            this.noCull = true;
            return this;
        }

        /** Disables FFP lighting */
        public Builder unlit() {
            this.unlit = true;
            return this;
        }

        public Builder noDepthWrite() {
            this.noDepthWrite = true;
            return this;
        }

        /** Changes every frame, not cached */
        public Builder stream() {
            this.stream = true;
            return this;
        }

        /**  Discards pixels with alpha at or below threshold - hard-edged cutout, no blending. ALPHA_TEST GREATER */
        public Builder cutout(float threshold) {
            this.cutoutAlpha = threshold;
            return this;
        }

        /** Draws only where geometry already exists at the same depth; for decal/overlay passes. GL_EQUAL depth */
        public Builder depthEqual() {
            this.depthEqual = true;
            return this;
        }

        /** Standard alpha blending (glass, liquids). SRC_ALPHA, ONE_MINUS_SRC_ALPHA */
        public Builder translucent() {
            this.transparency = Transparency.TRANSLUCENT;
            return this;
        }

        /** Additive glow; alpha ignored. ONE, ONE */
        public Builder additive() {
            this.transparency = Transparency.ADDITIVE;
            return this;
        }

        /** Custom shader around this material's batched draws; see {@link TesrShaders#register} */
        public Builder shader(TesrShader shader) {
            this.shader = shader;
            return this;
        }

        public TesrMaterial build() {
            return intern(new TesrMaterial(hasColor, red, green, blue, alpha, hasLightmap, lightmapX, lightmapY, noCull, unlit, noDepthWrite, stream, cutoutAlpha, depthEqual, transparency, shader));
        }
    }
}
