package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.ffp.ParticleParityFixture;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleInstancingTest {

    @BeforeEach
    @AfterEach
    void resetState() {
        ParticleDescriptorRegistry.clearCache();
    }

    private static class PlainFx extends EntityFX {

        PlainFx() {
            super(null, 0, 0, 0);
        }
    }

    private static class OverridingFx extends EntityFX {

        OverridingFx() {
            super(null, 0, 0, 0);
        }

        @Override
        public void renderParticle(Tessellator t, float a, float b, float c, float d, float e, float f) {}
    }

    private static float[] entityFxQuad(float cx, float cy, float cz, float half, ParticleParityFixture.Rotation r, float minU, float maxU, float minV, float maxV) {
        final float[] u = { maxU, maxU, minU, minU };
        final float[] v = { maxV, minV, minV, maxV };
        final float[] verts = new float[20];
        for (int i = 0, o = 0; i < 4; i++, o += 5) {
            final float a = ParticleParityFixture.cornerA(i) * half;
            final float b = ParticleParityFixture.cornerB(i) * half;
            verts[o] = cx + r.offsetX(a, b);
            verts[o + 1] = cy + r.offsetY(b);
            verts[o + 2] = cz + r.offsetZ(a, b);
            verts[o + 3] = u[i];
            verts[o + 4] = v[i];
        }
        return verts;
    }

    @Test
    void resolvesToVanillaUnlessRenderParticleIsOverridden() {
        assertSame(ParticleDescriptorRegistry.VANILLA, ParticleDescriptorRegistry.forClass(PlainFx.class));
        assertSame(ParticleDescriptorRegistry.CAPTURE, ParticleDescriptorRegistry.forClass(OverridingFx.class));
    }

    @Test
    void decodesAtCameraRelativeDistanceWithScaledTolerance() {
        final ParticleParityFixture.Rotation r = ParticleParityFixture.Rotation.of(-64f, 33f, false);
        final float cx = 214.0f, cy = -8.0f, cz = 196.5f, half = 0.045f;
        final float[] verts = entityFxQuad(cx, cy, cz, half, r, 0.0f, 0.0625f, 0.0f, 0.0625f);

        final ParticleParams out = new ParticleParams();
        final boolean ok = ParticleQuadDecoder.decode(verts, 4, r.x(), r.xz(), r.z(), r.yz(), r.xy(), out);

        assertTrue(ok, "expected decode to succeed at camera-relative distance ~200");
        final float scale = Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz)));
        final float tol = 1.0e-5f * (1.0f + Math.abs(half) + scale);
        assertEquals(cx, out.centerX, tol);
        assertEquals(cy, out.centerY, tol);
        assertEquals(cz, out.centerZ, tol);
    }

    @Test
    void rejectsQuadBuiltWithAMismatchedRotationBasis() {
        final ParticleParityFixture.Rotation built = ParticleParityFixture.Rotation.of(0f, 0f, false);
        final ParticleParityFixture.Rotation queried = ParticleParityFixture.Rotation.of(90f, 30f, false);
        final float[] verts = entityFxQuad(0.5f, 1.0f, 0.5f, 0.1f, built, 0f, 1f, 0f, 1f);

        final boolean ok = ParticleQuadDecoder.decode(verts, 4, queried.x(), queried.xz(), queried.z(), queried.yz(), queried.xy(), new ParticleParams());

        assertFalse(ok, "a quad built for a different camera orientation must not decode");
    }

    @Test
    void rejectsUvNotMatchingTheBillboardPattern() {
        final ParticleParityFixture.Rotation r = ParticleParityFixture.Rotation.of(10f, 10f, false);
        final float[] verts = entityFxQuad(1f, 2f, 3f, 0.1f, r, 0.0f, 0.0625f, 0.0f, 0.0625f);
        verts[8] = verts[3] + 0.01f;

        final boolean ok = ParticleQuadDecoder.decode(verts, 4, r.x(), r.xz(), r.z(), r.yz(), r.xy(), new ParticleParams());
        assertFalse(ok);
    }
}
