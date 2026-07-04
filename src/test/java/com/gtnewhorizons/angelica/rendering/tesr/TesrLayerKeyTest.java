package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TesrLayerKeyTest {

    private static final ResourceLocation TEX = new ResourceLocation("angelica", "textures/test.png");
    private static final TesrShader SHADER = TesrShaders.register("test:layer_key", () -> {}, () -> {});

    @Test
    void scratchProbeMatchesCopiedKey() {
        final TesrBatchRenderer.LayerKey scratch = new TesrBatchRenderer.LayerKey().set(TEX, TesrMaterial.Transparency.OPAQUE, true, false, false, 0.1f, false, SHADER, false);
        final TesrBatchRenderer.LayerKey stored = scratch.copy();
        assertNotSame(scratch, stored);
        assertEquals(scratch, stored);
        assertEquals(scratch.hashCode(), stored.hashCode());

        scratch.set(TEX, TesrMaterial.Transparency.OPAQUE, true, false, false, 0.1f, false, SHADER, false);
        assertEquals(stored, scratch, "re-set scratch still matches stored copy");
    }

    @Test
    void fieldChangesBreakEquality() {
        final TesrBatchRenderer.LayerKey base = new TesrBatchRenderer.LayerKey().set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, 0f, false, null, false);
        final TesrBatchRenderer.LayerKey other = base.copy();

        other.set(null, TesrMaterial.Transparency.OPAQUE, false, false, false, 0f, false, null, false);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.TRANSLUCENT, false, false, false, 0f, false, null, false);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, 0.5f, false, null, false);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, 0f, false, SHADER, false);
        assertNotEquals(base, other);
        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, 0f, false, null, true);
        assertNotEquals(base, other);

        other.set(TEX, TesrMaterial.Transparency.OPAQUE, false, false, false, 0f, false, null, false);
        assertEquals(base, other);
    }

    @Test
    void nullTextureAndShaderHash() {
        final TesrBatchRenderer.LayerKey key = new TesrBatchRenderer.LayerKey().set(null, null, false, false, false, 0f, false, null, false);
        assertEquals(key, key.copy());
        assertEquals(key.hashCode(), key.copy().hashCode());
    }
}
