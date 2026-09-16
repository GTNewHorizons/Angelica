package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ParticleParityFixture.Rotation;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkOverlayFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.client.particle.EntityHeartFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntityNoteFX;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.particle.EntitySnowShovelFX;
import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class ParticleInstancingGLTest {

    private static final float TOL = 1.0e-5f;
    private static final float[] PARTIAL_TICKS = { 0.0f, 0.37f, 0.999f };
    private static final int FRAMES = 4;
    private static final Rotation[] ROTATIONS = { Rotation.of(37f, 15f, false), Rotation.of(-110f, 62f, true) };
    private static final int STUB_TEXTURE_ID = 42;

    private static class NoOpTessellator extends Tessellator {

        @Override
        public void setBrightness(int value) {}

        @Override
        public void setColorRGBA(int red, int green, int blue, int alpha) {}

        @Override
        public void setTextureUV(double u, double v) {}

        @Override
        public void addVertexWithUV(double x, double y, double z, double u, double v) {}

        @Override
        public void addVertex(double x, double y, double z) {}

        @Override
        public void setNormal(float x, float y, float z) {}

        @Override
        public void startDrawingQuads() {}

        @Override
        public int draw() {
            return 0;
        }
    }

    private static final class RecordingTessellator extends Tessellator {

        final List<String> log = new ArrayList<>();

        @Override
        public void setBrightness(int value) {
            log.add("brightness(" + value + ")");
        }

        @Override
        public void setColorRGBA(int red, int green, int blue, int alpha) {
            log.add("color(" + red + "," + green + "," + blue + "," + alpha + ")");
        }

        @Override
        public void setTextureUV(double u, double v) {
            log.add("uv(" + u + "," + v + ")");
        }

        @Override
        public void addVertexWithUV(double x, double y, double z, double u, double v) {
            log.add("vertexUV(" + x + "," + y + "," + z + "," + u + "," + v + ")");
        }

        @Override
        public void addVertex(double x, double y, double z) {
            log.add("vertex(" + x + "," + y + "," + z + ")");
        }

        @Override
        public void setNormal(float x, float y, float z) {
            log.add("normal(" + x + "," + y + "," + z + ")");
        }

        @Override
        public void startDrawingQuads() {
            log.add("startQuads");
        }

        @Override
        public void startDrawing(int mode) {
            log.add("startDrawing(" + mode + ")");
        }

        @Override
        public int draw() {
            log.add("draw");
            return 0;
        }
    }

    private static final class ProbeBlock extends Block {

        ProbeBlock() {
            super(Material.rock);
        }
    }

    private static final class ProbeCritFX extends EntityCritFX {

        ProbeCritFX(double x, double y, double z, double mx, double my, double mz, float mult) {
            super(null, x, y, z, mx, my, mz, mult);
        }

        @Override
        public void onUpdate() {}
    }

    private static final class ProbeFireworkOverlay extends EntityFireworkOverlayFX {

        ProbeFireworkOverlay(double x, double y, double z) {
            super(null, x, y, z);
        }
    }

    private static final class ProbePixieTrailFX extends EntityFX {

        ProbePixieTrailFX(double x, double y, double z) {
            super(null, x, y, z);
        }

        @Override
        public void renderParticle(Tessellator tessellator, float par2, float par3, float par4, float par5, float par6, float par7) {
            float f = (particleAge + par2) / particleMaxAge * 32.0F;
            if (f < 0.0F) f = 0.0F;
            if (f > 1.0F) f = 1.0F;
            particleScale = particleScale * f;

            tessellator.draw();

            float f6 = (float) particleTextureIndexX / 16.0F;
            float f7 = f6 + 0.0624375F;
            float f8 = (float) particleTextureIndexY / 16.0F;
            float f9 = f8 + 0.0624375F;
            float f10 = 0.1F * particleScale;

            if (particleIcon != null) {
                f6 = particleIcon.getMinU();
                f7 = particleIcon.getMaxU();
                f8 = particleIcon.getMinV();
                f9 = particleIcon.getMaxV();
            }

            float f11 = (float) (prevPosX + (posX - prevPosX) * (double) par2 - interpPosX);
            float f12 = (float) (prevPosY + (posY - prevPosY) * (double) par2 - interpPosY);
            float f13 = (float) (prevPosZ + (posZ - prevPosZ) * (double) par2 - interpPosZ);

            tessellator.startDrawingQuads();
            tessellator.setBrightness(175);

            tessellator.setColorRGBA_F(particleRed, particleGreen, particleBlue, 1.0F);
            tessellator.addVertexWithUV(f11 - par3 * f10 - par6 * f10, f12 - par4 * f10, f13 - par5 * f10 - par7 * f10, f7, f9);
            tessellator.addVertexWithUV(f11 - par3 * f10 + par6 * f10, f12 + par4 * f10, f13 - par5 * f10 + par7 * f10, f7, f8);
            tessellator.addVertexWithUV(f11 + par3 * f10 + par6 * f10, f12 + par4 * f10, f13 + par5 * f10 + par7 * f10, f6, f8);
            tessellator.addVertexWithUV(f11 + par3 * f10 - par6 * f10, f12 - par4 * f10, f13 + par5 * f10 - par7 * f10, f6, f9);

            tessellator.draw();

            tessellator.startDrawingQuads();
        }
    }

    private record StubIcon(float minU, float maxU, float minV, float maxV) implements IIcon {

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public float getMinU() {
            return minU;
        }

        @Override
        public float getMaxU() {
            return maxU;
        }

        @Override
        public float getInterpolatedU(double u) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getMinV() {
            return minV;
        }

        @Override
        public float getMaxV() {
            return maxV;
        }

        @Override
        public float getInterpolatedV(double v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getIconName() {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeEach
    @AfterEach
    void resetState() {
        ParticleDescriptorRegistry.clearCache();
        BopParticleDescriptors.invalidate();
        EntityFX.interpPosX = 0;
        EntityFX.interpPosY = 0;
        EntityFX.interpPosZ = 0;
    }

    private static ParticleRenderState checkFrame(ParticleDescriptor descriptor, EntityFX reference, EntityFX candidate, float partialTicks, Rotation r, int startBrightness) {
        final NoOpTessellator run = new NoOpTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();
        capture.begin(startBrightness, run, GLStateManager.getModelViewMatrix());
        reference.renderParticle(capture, partialTicks, r.x(), r.xz(), r.z(), r.yz(), r.xy());

        assertFalse(capture.spilled());
        assertEquals(4, capture.captured());

        final ParticleParams expected = new ParticleParams();
        final boolean decoded = ParticleQuadDecoder.decode(capture.vertices(), capture.captured(), r.x(), r.xz(), r.z(), r.yz(), r.xy(), expected);
        assertTrue(decoded);

        final int expectedColor = capture.capturedColor();
        final int expectedBrightness = capture.capturedBrightness();

        final ParticleParams actual = new ParticleParams();
        actual.brightness = startBrightness;
        final ParticleRenderState state = new ParticleRenderState();
        final boolean ok = descriptor.describe(candidate, partialTicks, actual, state);
        assertTrue(ok);

        assertEquals(expected.centerX, actual.centerX, TOL);
        assertEquals(expected.centerY, actual.centerY, TOL);
        assertEquals(expected.centerZ, actual.centerZ, TOL);
        assertEquals(expected.half, actual.half, TOL);
        assertEquals(expected.u0, actual.u0, TOL);
        assertEquals(expected.v0, actual.v0, TOL);
        assertEquals(expected.u1, actual.u1, TOL);
        assertEquals(expected.v1, actual.v1, TOL);
        assertEquals(expectedColor, actual.colorABGR);
        assertEquals(expectedBrightness, actual.brightness);
        return state;
    }

    private static void runParity(Class<? extends EntityFX> cls, Supplier<EntityFX> factory) {
        final EntityFX reference = factory.get();
        final EntityFX candidate = factory.get();
        final ParticleDescriptor descriptor = ParticleDescriptorRegistry.forClass(cls);
        for (int frame = 0; frame < FRAMES; frame++) {
            for (Rotation r : ROTATIONS) {
                for (float pt : PARTIAL_TICKS) {
                    checkFrame(descriptor, reference, candidate, pt, r, 50 + frame * 10);
                }
            }
            reference.particleAge++;
            candidate.particleAge++;
        }
    }

    private static void seedPositions(EntityFX fx, double x, double y, double z) {
        fx.posX = x;
        fx.posY = y;
        fx.posZ = z;
        fx.prevPosX = x - 0.1;
        fx.prevPosY = y - 0.1;
        fx.prevPosZ = z - 0.1;
    }

    private static Arguments particle(Class<? extends EntityFX> cls, Supplier<EntityFX> factory) {
        return Arguments.of(cls, factory);
    }

    static Stream<Arguments> particles() {
        return Stream.of(
            particle(EntityFlameFX.class, () -> {
                final EntityFlameFX fx = new EntityFlameFX(null, 1.5, 10.0, -2.25, 0.0, 0.0, 0.0);
                seedPositions(fx, 1.5, 10.0, -2.25);
                fx.particleAge = 2;
                fx.particleMaxAge = 20;
                fx.particleScale = 1.3f;
                Reflect.set(fx, "flameScale", 1.3f);
                fx.particleRed = 0.9f;
                fx.particleGreen = 0.5f;
                fx.particleBlue = 0.2f;
                fx.particleAlpha = 0.8f;
                fx.particleTextureIndexX = 3;
                fx.particleTextureIndexY = 1;
                return fx;
            }),
            particle(EntitySmokeFX.class, () -> {
                final EntitySmokeFX fx = new EntitySmokeFX(null, 2.0, 5.0, 1.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 2.0, 5.0, 1.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 30;
                fx.particleScale = 0.9f;
                Reflect.set(fx, "smokeParticleScale", 0.9f);
                fx.particleRed = 1.5f;
                fx.particleGreen = 0.1f;
                fx.particleBlue = 0.1f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityCloudFX.class, () -> {
                final EntityCloudFX fx = new EntityCloudFX(null, -3.0, 20.0, 4.0, 0.0, 0.0, 0.0);
                seedPositions(fx, -3.0, 20.0, 4.0);
                fx.particleAge = 3;
                fx.particleMaxAge = 40;
                fx.particleScale = 2.4f;
                Reflect.set(fx, "field_70569_a", 2.4f);
                fx.particleRed = 0.8f;
                fx.particleGreen = 0.8f;
                fx.particleBlue = 0.8f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityCritFX.class, () -> {
                final ProbeCritFX fx = new ProbeCritFX(0.5, 1.0, 0.5, 0.0, 0.0, 0.0, 1.0f);
                seedPositions(fx, 0.5, 1.0, 0.5);
                fx.particleAge = 1;
                fx.particleMaxAge = 10;
                fx.particleScale = 0.6f;
                Reflect.set(fx, "initialParticleScale", 0.6f);
                fx.particleRed = 0.7f;
                fx.particleGreen = 0.7f;
                fx.particleBlue = 0.7f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityHeartFX.class, () -> {
                final EntityHeartFX fx = new EntityHeartFX(null, 4.0, 66.0, -1.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 4.0, 66.0, -1.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 16;
                fx.particleScale = 3.0f;
                Reflect.set(fx, "particleScaleOverTime", 3.0f);
                fx.particleRed = 1.0f;
                fx.particleGreen = 0.2f;
                fx.particleBlue = 0.2f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityLavaFX.class, () -> {
                final EntityLavaFX fx = new EntityLavaFX(null, 8.0, 12.0, 8.0);
                seedPositions(fx, 8.0, 12.0, 8.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 18;
                fx.particleScale = 1.7f;
                Reflect.set(fx, "lavaParticleScale", 1.7f);
                fx.particleRed = 1.0f;
                fx.particleGreen = 1.0f;
                fx.particleBlue = 1.0f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityNoteFX.class, () -> {
                final EntityNoteFX fx = new EntityNoteFX(null, 0.0, 70.0, 0.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 0.0, 70.0, 0.0);
                fx.particleAge = 0;
                fx.particleMaxAge = 6;
                fx.particleScale = 1.5f;
                Reflect.set(fx, "noteParticleScale", 1.5f);
                fx.particleRed = 0.4f;
                fx.particleGreen = 0.9f;
                fx.particleBlue = 0.6f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntityPortalFX.class, () -> {
                final EntityPortalFX fx = new EntityPortalFX(null, 5.0, 40.0, 5.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 5.0, 40.0, 5.0);
                fx.particleAge = 2;
                fx.particleMaxAge = 45;
                fx.particleScale = 0.6f;
                Reflect.set(fx, "portalParticleScale", 0.6f);
                fx.particleRed = 0.85f;
                fx.particleGreen = 0.1f;
                fx.particleBlue = 0.9f;
                fx.particleAlpha = 1.0f;
                fx.particleTextureIndexX = 2;
                fx.particleTextureIndexY = 0;
                return fx;
            }),
            particle(EntityReddustFX.class, () -> {
                final EntityReddustFX fx = new EntityReddustFX(null, -2.0, 8.0, -2.0, 1.0f, 0.4f, 0.4f);
                seedPositions(fx, -2.0, 8.0, -2.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 20;
                fx.particleScale = 1.1f;
                Reflect.set(fx, "reddustParticleScale", 1.1f);
                fx.particleRed = 0.9f;
                fx.particleGreen = 0.35f;
                fx.particleBlue = 0.35f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntitySnowShovelFX.class, () -> {
                final EntitySnowShovelFX fx = new EntitySnowShovelFX(null, 3.0, 15.0, 3.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 3.0, 15.0, 3.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 22;
                fx.particleScale = 0.8f;
                Reflect.set(fx, "snowDigParticleScale", 0.8f);
                fx.particleRed = 0.9f;
                fx.particleGreen = 0.9f;
                fx.particleBlue = 1.0f;
                fx.particleAlpha = 1.0f;
                return fx;
            }),
            particle(EntitySpellParticleFX.class, () -> {
                final EntitySpellParticleFX fx = new EntitySpellParticleFX(null, 1.0, 60.0, 1.0, 0.0, 0.0, 0.0);
                seedPositions(fx, 1.0, 60.0, 1.0);
                fx.particleAge = 1;
                fx.particleMaxAge = 25;
                fx.particleScale = 1.1f;
                fx.particleRed = 0.6f;
                fx.particleGreen = 0.3f;
                fx.particleBlue = 0.9f;
                fx.particleAlpha = 1.0f;
                fx.particleIcon = new StubIcon(0.1f, 0.2f, 0.3f, 0.4f);
                return fx;
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("particles")
    void descriptorMatchesVanillaAcrossFrames(Class<? extends EntityFX> cls, Supplier<EntityFX> factory) {
        runParity(cls, factory);
    }

    @Test
    void fireworkOverlayFxMatchesVanillaIncludingNegativeHalfFrames() {
        runParity(EntityFireworkOverlayFX.class, () -> {
            final ProbeFireworkOverlay fx = new ProbeFireworkOverlay(6.0, 70.0, -4.0);
            seedPositions(fx, 6.0, 70.0, -4.0);
            fx.particleRed = 1.0f;
            fx.particleGreen = 0.6f;
            fx.particleBlue = 0.2f;
            assertTrue(fx.particleAge == 0);
            return fx;
        });
    }

    private static EntityFireworkSparkFX twinklingSpark(int age) {
        final EntityFireworkSparkFX fx = new EntityFireworkSparkFX(null, -1.0, 65.0, 3.0, 0.0, 0.0, 0.0, null);
        seedPositions(fx, -1.0, 65.0, 3.0);
        fx.particleAge = age;
        fx.particleMaxAge = 30;
        fx.particleScale = 0.75f;
        fx.particleRed = 1.0f;
        fx.particleGreen = 0.5f;
        fx.particleBlue = 0.0f;
        fx.particleAlpha = 1.0f;
        Reflect.set(fx, "field_92048_ay", true);
        return fx;
    }

    @Test
    void fireworkSparkFxMatchesVanillaAcrossTheTwinkleCycle() {
        final ParticleDescriptor descriptor = ParticleDescriptorRegistry.forClass(EntityFireworkSparkFX.class);
        final Rotation r = ROTATIONS[0];
        int drawn = 0;
        int skipped = 0;
        for (int age = 0; age <= 30; age++) {
            final EntityFireworkSparkFX reference = twinklingSpark(age);
            final EntityFireworkSparkFX candidate = twinklingSpark(age);
            final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();
            capture.begin(100, new NoOpTessellator(), GLStateManager.getModelViewMatrix());
            reference.renderParticle(capture, 0.4f, r.x(), r.xz(), r.z(), r.yz(), r.xy());
            assertFalse(capture.spilled());
            if (capture.captured() == 0) {
                skipped++;
                assertFalse(descriptor.describe(candidate, 0.4f, new ParticleParams(), new ParticleRenderState()), "age " + age);
            } else {
                drawn++;
                checkFrame(descriptor, reference, candidate, 0.4f, r, 100);
            }
        }
        assertTrue(drawn > 0 && skipped > 0, "drawn=" + drawn + " skipped=" + skipped);
    }

    @Test
    void diggingFxMatchesVanillaAcrossFramesWithAnUnstitchedBlockIcon() {
        final ProbeBlock block = new ProbeBlock();
        runParity(EntityDiggingFX.class, () -> {
            final EntityDiggingFX fx = new EntityDiggingFX(null, 2.0, 5.0, 2.0, 0.0, 0.0, 0.0, block, 0, 3);
            seedPositions(fx, 2.0, 5.0, 2.0);
            fx.particleAge = 1;
            fx.particleMaxAge = 15;
            fx.particleScale = 0.5f;
            fx.particleRed = 0.6f;
            fx.particleGreen = 0.6f;
            fx.particleBlue = 0.6f;
            fx.particleTextureIndexX = 4;
            fx.particleTextureIndexY = 2;
            fx.particleTextureJitterX = 1.5f;
            fx.particleTextureJitterY = 2.0f;
            assertTrue(fx.particleIcon == null);
            return fx;
        });
    }

    private static ProbePixieTrailFX pixieTrail() {
        final ProbePixieTrailFX fx = new ProbePixieTrailFX(1.0, 65.0, -2.0);
        fx.posX = 1.0;
        fx.posY = 65.0;
        fx.posZ = -2.0;
        fx.prevPosX = 0.9;
        fx.prevPosY = 64.9;
        fx.prevPosZ = -2.1;
        fx.particleAge = 0;
        fx.particleMaxAge = 64;
        fx.particleScale = 2.0f;
        fx.particleRed = 0.4f;
        fx.particleGreen = 0.9f;
        fx.particleBlue = 0.7f;
        fx.particleTextureIndexX = 6;
        fx.particleTextureIndexY = 3;
        return fx;
    }

    @Test
    void pixieTrailFxMatchesVanillaAcrossFramesWithACachedTextureId() {
        Reflect.setStatic(BopParticleDescriptors.class, "pixieTrailTexture", STUB_TEXTURE_ID);
        ParticleDescriptorRegistry.register(ProbePixieTrailFX.class.getName(), BopParticleDescriptors.PIXIE_TRAIL);
        final ParticleDescriptor descriptor = ParticleDescriptorRegistry.forClass(ProbePixieTrailFX.class);
        assertSame(BopParticleDescriptors.PIXIE_TRAIL, descriptor);

        final ProbePixieTrailFX reference = pixieTrail();
        final ProbePixieTrailFX candidate = pixieTrail();

        for (int frame = 0; frame < FRAMES; frame++) {
            for (Rotation r : ROTATIONS) {
                for (float pt : PARTIAL_TICKS) {
                    final ParticleRenderState state = checkFrame(descriptor, reference, candidate, pt, r, 50 + frame * 10);
                    assertTrue(state.blend);
                    assertEquals(GL11.GL_SRC_ALPHA, state.blendSrc);
                    assertEquals(GL11.GL_ONE, state.blendDst);
                    assertFalse(state.depthMask);
                    assertEquals(STUB_TEXTURE_ID, state.texture);
                }
            }
            reference.particleAge++;
            candidate.particleAge++;
        }
    }

    private static void feedQuad(ParticleCaptureTessellator capture) {
        capture.addVertexWithUV(0.0, 0.0, 0.0, 0.0, 1.0);
        capture.addVertexWithUV(0.0, 1.0, 0.0, 0.0, 0.0);
        capture.addVertexWithUV(1.0, 1.0, 0.0, 1.0, 0.0);
        capture.addVertexWithUV(1.0, 0.0, 0.0, 1.0, 1.0);
    }

    @Test
    void aSecondQuadAfterASelfDrawSpillsIntoItsOwnBatchWhenTheRunIsNotDrawing() {
        final RecordingTessellator run = new RecordingTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();

        capture.begin(100, run, GLStateManager.getModelViewMatrix());
        capture.setColorRGBA(5, 6, 7, 8);
        feedQuad(capture);
        capture.draw();
        run.log.clear();

        capture.addVertexWithUV(2.0, 2.0, 0.0, 0.5, 0.5);

        assertTrue(capture.spilled(), "a fifth vertex after a self-draw is a multi-quad particle");
        assertEquals("startQuads", run.log.get(1), () -> "the replay must open its own batch: " + run.log);
        assertEquals(1, run.log.stream().filter("startQuads"::equals).count());
        assertEquals("draw", run.log.get(run.log.size() - 2), () -> "the closed quad must be drawn on spill: " + run.log);
        assertEquals("vertexUV(2.0,2.0,0.0,0.5,0.5)", run.log.get(run.log.size() - 1));
        assertEquals(5, run.log.stream().filter(l -> l.startsWith("vertexUV(")).count());
        assertFalse(run.isDrawing, "a run that was not drawing must be left not drawing");
    }

    @Test
    void aClosedSpillReplaysInTheSameOrder() {
        final RecordingTessellator run = new RecordingTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();

        capture.begin(100, run, GLStateManager.getModelViewMatrix());
        capture.setColorRGBA(5, 6, 7, 8);
        feedQuad(capture);
        capture.draw();
        run.isDrawing = true;
        run.log.clear();

        capture.startDrawing(GL11.GL_LINES);

        final List<String> expected = List.of(
            "brightness(100)",
            "color(5,6,7,8)",
            "uv(1.0,1.0)",
            "vertexUV(0.0,0.0,0.0,0.0,1.0)",
            "vertexUV(0.0,1.0,0.0,0.0,0.0)",
            "vertexUV(1.0,1.0,0.0,1.0,0.0)",
            "vertexUV(1.0,0.0,0.0,1.0,1.0)",
            "draw",
            "startQuads",
            "startDrawing(" + GL11.GL_LINES + ")");
        assertEquals(expected, run.log);
    }

    @Test
    void aFifthVertexSpillsAndForwardsRemainingCallsToRun() {
        final RecordingTessellator run = new RecordingTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();

        capture.begin(100, run, GLStateManager.getModelViewMatrix());
        capture.setColorRGBA(5, 6, 7, 8);
        feedQuad(capture);
        assertFalse(capture.spilled());

        capture.addVertexWithUV(2.0, 2.0, 0.0, 0.5, 0.5);

        assertTrue(capture.spilled(), "a fifth vertex must taint/spill the capture");
        assertEquals(0, capture.captured());
        assertFalse(run.log.isEmpty(), "the spilled quad must be replayed into the run tessellator");
        assertTrue(run.log.get(0).equals("brightness(100)"), () -> "unexpected replay log: " + run.log);
        assertEquals("vertexUV(2.0,2.0,0.0,0.5,0.5)", run.log.get(run.log.size() - 1), "the overflowing vertex must keep its own uv");

        capture.setBrightness(999);
        capture.setColorRGBA(9, 9, 9, 9);
        assertEquals("brightness(999)", run.log.get(run.log.size() - 2));
        assertEquals("color(9,9,9,9)", run.log.get(run.log.size() - 1));
    }

    @Test
    void setNormalSpillsImmediately() {
        final RecordingTessellator run = new RecordingTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();

        capture.begin(0, run, GLStateManager.getModelViewMatrix());
        capture.setColorRGBA(1, 1, 1, 1);
        capture.addVertexWithUV(0.0, 0.0, 0.0, 0.0, 0.0);
        capture.addVertexWithUV(0.0, 1.0, 0.0, 0.0, 1.0);

        capture.setNormal(0.0f, 1.0f, 0.0f);

        assertTrue(capture.spilled());
        assertEquals(0, capture.captured());
        assertTrue(run.log.contains("normal(0.0,1.0,0.0)"));
        assertTrue(run.log.contains("vertexUV(0.0,0.0,0.0,0.0,0.0)"));
        assertTrue(run.log.contains("vertexUV(0.0,1.0,0.0,0.0,1.0)"));
        assertEquals(run.log.size() - 1, run.log.indexOf("normal(0.0,1.0,0.0)"), "normal must be forwarded after the replayed vertices");
    }

    @Test
    void addVertexBeforeAnyUvSpillsImmediately() {
        final RecordingTessellator run = new RecordingTessellator();
        final ParticleCaptureTessellator capture = new ParticleCaptureTessellator();

        capture.begin(0, run, GLStateManager.getModelViewMatrix());
        capture.addVertex(3.0, 4.0, 5.0);

        assertTrue(capture.spilled());
        assertEquals(List.of("brightness(0)", "vertex(3.0,4.0,5.0)"), run.log);
    }
}
