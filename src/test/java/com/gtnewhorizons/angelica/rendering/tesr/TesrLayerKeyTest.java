package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TesrLayerKeyTest {

    private static final ResourceLocation TEX = new ResourceLocation("angelica", "textures/test.png");
    private static final TesrShader SHADER = TesrShaders.register("test:layer_key", () -> {}, () -> {});
    private static final TesrMaterial.SpecialRender NONE = TesrMaterial.SpecialRender.NONE;
    private static final PassOverride NO_PASS = PassOverride.NONE;
    private static final PassOverride EYES = new PassOverride(SpecialCondition.ENTITY_EYES, null);
    private static final PassOverride TRANSLUCENT = new PassOverride(null, Boolean.TRUE);

    @Test
    void scratchProbeMatchesCopiedKey() {
        final TesrBatchRenderer.LayerKey scratch = new TesrBatchRenderer.LayerKey().set(TEX, TesrMaterial.Transparency.OPAQUE, true, false, false, false, 0.1f, false, NONE, SHADER, false, NO_PASS, 0.0f, 0.0f);
        final TesrBatchRenderer.LayerKey stored = scratch.copy();
        assertNotSame(scratch, stored);
        assertEquals(scratch, stored);
        assertEquals(scratch.hashCode(), stored.hashCode());

        scratch.set(TEX, TesrMaterial.Transparency.OPAQUE, true, false, false, false, 0.1f, false, NONE, SHADER, false, NO_PASS, 0.0f, 0.0f);
        assertEquals(stored, scratch, "re-set scratch still matches stored copy");
    }

    @Test
    void fieldChangesBreakEquality() {
        final TesrBatchRenderer.LayerKey base = new TesrBatchRenderer.LayerKey().set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        final TesrBatchRenderer.LayerKey other = base.copy();

        other.set(null, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.TRANSLUCENT, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0.5f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, true, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, TesrMaterial.SpecialRender.GLINT, null, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, SHADER, false, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, true, NO_PASS, 0.0f, 0.0f);
        assertNotEquals(base, other);

        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        assertEquals(base, other);
    }

    @Test
    void passSeparatesOtherwiseIdenticalKeys() {
        final TesrBatchRenderer.LayerKey plain = new TesrBatchRenderer.LayerKey().set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, 0.0f, 0.0f);
        final TesrBatchRenderer.LayerKey eyes = plain.copy();
        eyes.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, EYES, 0.0f, 0.0f);
        assertNotEquals(plain, eyes);

        final TesrBatchRenderer.LayerKey translucent = plain.copy();
        translucent.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, TRANSLUCENT, 0.0f, 0.0f);
        assertNotEquals(plain, translucent);
        assertNotEquals(eyes, translucent);

        assertEquals(eyes, eyes.copy());
        assertEquals(eyes.hashCode(), eyes.copy().hashCode());

        // Polygon offset is the other thing a batch outlives -- the coplanar eye overlay needs it at emit time.
        final TesrBatchRenderer.LayerKey offset = plain.copy();
        offset.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, false, 0f, false, NONE, null, false, NO_PASS, -1.0f, -1.0f);
        assertNotEquals(plain, offset);
        assertEquals(offset, offset.copy());
        assertEquals(offset.hashCode(), offset.copy().hashCode());
    }

    @Test
    void nullTextureAndShaderHash() {
        final TesrBatchRenderer.LayerKey key = new TesrBatchRenderer.LayerKey().set(null, null, false, false, false, false, 0f, false, null, null, false, NO_PASS, 0.0f, 0.0f);
        assertEquals(key, key.copy());
        assertEquals(key.hashCode(), key.copy().hashCode());
    }
}
