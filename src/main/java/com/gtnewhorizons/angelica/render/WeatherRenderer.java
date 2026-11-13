package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.api.weather.WeatherColumnProvider;
import com.gtnewhorizons.angelica.api.weather.WeatherColumnSink;
import com.gtnewhorizons.angelica.compat.dragonapi.DragonAPICompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherParams;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherQuadMesh;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import jss.notfine.core.SettingsManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.EntityRainFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

/**
 * Replaces EntityRenderer.renderRainSnow.
 *
 * <p>The whole grid is one instanced draw per layer: vanilla rain, vanilla snow, and each provider layer. Everything that varies per frame
 * (billboard axis, distance falloff, texture scroll, snow drift) happens in the vertex shader, and the
 * instance buffer is only rebuilt when the camera changes block or {@link WeatherColumns} actually
 * resamples something. Per-column data comes from that cache rather than being recomputed every frame.
 * {@link WeatherColumnProvider} columns are collected from the provider instead, and re-collected only when its revision,
 * the dimension, or the player's block X/Z changes.
 */
public final class WeatherRenderer {

    private static final ResourceLocation RAIN_TEXTURE = new ResourceLocation("textures/environment/rain.png");
    private static final ResourceLocation SNOW_TEXTURE = new ResourceLocation("textures/environment/snow.png");

    private static final int REBUILD_TICKS = 20;

    private static final long P_STRENGTH = Tracy.plotHandle("weather.strength");
    private static final long P_QUADS = Tracy.plotHandle("weather.quads");
    private static final long P_DRAW_CALLS = Tracy.plotHandle("weather.drawCalls");
    private static final long P_MICROS = Tracy.plotHandle("weather.micros");
    private static final long P_REBUILDS = Tracy.plotHandle("weather.rebuilds");
    private static final long P_PARTICLE_MICROS = Tracy.plotHandle("weather.particleMicros");
    private static final long P_PARTICLES_SPAWNED = Tracy.plotHandle("weather.particlesSpawned");
    private static final long P_PROVIDER_COLUMNS = Tracy.plotHandle("weather.providerColumns");
    private static final long P_PROVIDER_DRAWS = Tracy.plotHandle("weather.providerDraws");
    private static final long P_PROVIDER_REBUILDS = Tracy.plotHandle("weather.providerRebuilds");
    private static final long P_LEGACY_HANDLER = Tracy.plotHandle("weather.legacyHandler");

    private static final WeatherColumns columns = new WeatherColumns();

    private static float[] axisX;
    private static float[] axisZ;

    private static final WeatherBatch VANILLA = new WeatherBatch();
    private static final WeatherBatch PROVIDER = new WeatherBatch();

    private static int builtRevision = Integer.MIN_VALUE;
    private static int builtX = Integer.MIN_VALUE;
    private static int builtY = Integer.MIN_VALUE;
    private static int builtZ = Integer.MIN_VALUE;
    private static int builtRadius = -1;
    private static int builtUpdateCount = Integer.MIN_VALUE;
    private static int builtCameraY = Integer.MIN_VALUE;
    private static boolean rebuiltThisFrame;

    private static int builtProviderRevision = Integer.MIN_VALUE;
    private static int builtProviderDimension = Integer.MIN_VALUE;
    private static int builtProviderX = Integer.MIN_VALUE;
    private static int builtProviderZ = Integer.MIN_VALUE;
    private static boolean providerRebuiltThisFrame;

    private static int frameQuads;
    private static int frameDrawCalls;
    private static int providerColumns;
    private static int providerDraws;
    private static boolean legacyHandler;

    private static int entityBlockX;
    private static int entityBlockY;
    private static int entityBlockZ;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static int cameraBlockY;
    private static double viewPosX;
    private static double viewPosZ;
    private static int updateCount;
    private static int radius;
    private static float rainStrength;
    private static float partialTicks;

    private static final int PARTICLE_RADIUS = 10;
    private static final Random particleRandom = new Random();
    private static int rainSoundCounter;

    private WeatherRenderer() {}

    /** Replaces EntityRenderer.addRainParticles */
    public static boolean spawnRainParticles() {
        if (!AngelicaConfig.enableInstancedWeather) return false;
        if (SettingsManager.downfallDistance < PARTICLE_RADIUS) return false;

        final long startNanos = System.nanoTime();
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient world = mc.theWorld;
        if (world.provider.getWeatherRenderer() != null) return false;
        float strength = world.getRainStrength(1.0F);
        if (!mc.gameSettings.fancyGraphics) strength /= 2.0F;
        if (strength == 0.0F) {
            Tracy.plot(P_PARTICLE_MICROS, 0.0);
            Tracy.plotInt(P_PARTICLES_SPAWNED, 0);
            return true;
        }

        int attempts = (int) (100.0F * strength * strength);
        if (mc.gameSettings.particleSetting == 1) attempts >>= 1;
        else if (mc.gameSettings.particleSetting == 2) attempts = 0;

        final int updateCount = ((EntityRendererAccessor) mc.entityRenderer).getRendererUpdateCount();
        particleRandom.setSeed(updateCount * 312987231L);

        final EntityLivingBase view = mc.renderViewEntity;
        final int originX = MathHelper.floor_double(view.posX);
        final int originY = MathHelper.floor_double(view.posY);
        final int originZ = MathHelper.floor_double(view.posZ);

        double soundX = 0.0D, soundY = 0.0D, soundZ = 0.0D;
        int landed = 0;
        int spawned = 0;

        for (int attempt = 0; attempt < attempts; attempt++) {
            final int x = originX + particleRandom.nextInt(PARTICLE_RADIUS) - particleRandom.nextInt(PARTICLE_RADIUS);
            final int z = originZ + particleRandom.nextInt(PARTICLE_RADIUS) - particleRandom.nextInt(PARTICLE_RADIUS);

            final int height;
            final boolean eligible;
            if (columns.covers(x, z)) {
                height = columns.heightAt(x, z);
                eligible = columns.strikesLightning(x, z)
                    && height <= originY + PARTICLE_RADIUS && height >= originY - PARTICLE_RADIUS
                    && columns.temperatureAt(x, z, height) >= 0.15F;
            } else {
                height = world.getPrecipitationHeight(x, z);
                final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                eligible = height <= originY + PARTICLE_RADIUS && height >= originY - PARTICLE_RADIUS
                    && biome.canSpawnLightningBolt() && biome.getFloatTemperature(x, height, z) >= 0.15F;
            }
            if (!eligible) continue;

            final float offsetX = particleRandom.nextFloat();
            final float offsetZ = particleRandom.nextFloat();

            final Block block = world.getBlock(x, height - 1, z);
            final Material material = block.getMaterial();
            if (material == Material.air) continue;
            final double dropY = (height + 0.1F) - block.getBlockBoundsMinY();

            if (material == Material.lava) {
                mc.effectRenderer.addEffect(new EntitySmokeFX(world, x + offsetX, dropY, z + offsetZ, 0.0D, 0.0D, 0.0D));
            } else {
                landed++;
                if (particleRandom.nextInt(landed) == 0) {
                    soundX = x + offsetX;
                    soundY = dropY;
                    soundZ = z + offsetZ;
                }
                mc.effectRenderer.addEffect(new EntityRainFX(world, x + offsetX, dropY, z + offsetZ));
            }
            spawned++;
        }
        Tracy.plotInt(P_PARTICLES_SPAWNED, spawned);
        Tracy.plot(P_PARTICLE_MICROS, (System.nanoTime() - startNanos) / 1000.0);

        if (landed > 0 && particleRandom.nextInt(3) < rainSoundCounter++) {
            rainSoundCounter = 0;
            final int playerHeight = columns.covers(originX, originZ) && columns.precipitates(originX, originZ)
                ? columns.heightAt(originX, originZ)
                : world.getPrecipitationHeight(originX, originZ);
            if (soundY > view.posY + 1.0D && playerHeight > originY) {
                world.playSound(soundX, soundY, soundZ, "ambient.weather.rain", 0.1F, 0.5F, false);
            } else {
                world.playSound(soundX, soundY, soundZ, "ambient.weather.rain", 0.2F, 1.0F, false);
            }
        }
        return true;
    }

    public static boolean render(float partialTicks) {
        final long startNanos = System.nanoTime();
        if (!AngelicaConfig.enableInstancedWeather) return false;
        frameQuads = 0;
        frameDrawCalls = 0;
        providerColumns = 0;
        providerDraws = 0;
        legacyHandler = false;
        final boolean handled = renderFrame(partialTicks);
        report(startNanos);
        return handled;
    }

    private static boolean renderFrame(float partialTicks) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient world = mc.theWorld;
        final IRenderHandler handler = world.provider.getWeatherRenderer();
        final WeatherColumnProvider provider;
        if (handler == null) {
            if (DragonAPICompat.forcingSeasonalSnow) return false;
            provider = null;
        } else if (handler instanceof WeatherColumnProvider columnProvider) {
            provider = columnProvider;
        } else {
            return useLegacyHandler();
        }

        rainStrength = provider == null || provider.rendersVanillaWeather() ? world.getRainStrength(partialTicks) : 0.0F;
        radius = SettingsManager.downfallDistance;
        final boolean vanillaActive = rainStrength > 0.0F && radius > 0;
        if (provider == null && !vanillaActive) {
            Tracy.plot(P_STRENGTH, rainStrength);
            return true;
        }

        final EntityLivingBase view = mc.renderViewEntity;
        WeatherRenderer.partialTicks = partialTicks;
        entityBlockX = MathHelper.floor_double(view.posX);
        entityBlockY = MathHelper.floor_double(view.posY);
        entityBlockZ = MathHelper.floor_double(view.posZ);
        cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        cameraBlockY = MathHelper.floor_double(cameraY);
        viewPosX = view.posX;
        viewPosZ = view.posZ;
        updateCount = ((EntityRendererAccessor) mc.entityRenderer).getRendererUpdateCount();

        final DeferredWorldRenderingPipeline deferred = deferredPipeline();
        mc.entityRenderer.enableLightmap(partialTicks);
        GLSMRasterizer.disableCull();
        GLSMCurrentAttribs.glNormal3f(0.0F, 1.0F, 0.0F);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        final boolean instanced;
        if (deferred == null) {
            instanced = true;
        } else if (deferred.supportsInstancing(Instancing.WEATHER)) {
            deferred.rebindCurrentPass();
            instanced = deferred.hasInstancedVariant(Instancing.WEATHER);
        } else {
            instanced = false;
        }
        if (provider != null && !instanced) {
            exitState(mc, partialTicks);
            return useLegacyHandler();
        }
        Tracy.plot(P_STRENGTH, rainStrength);

        if (vanillaActive) {
            buildAxisTable();
            columns.update(world, entityBlockX, entityBlockZ, radius, updateCount);
            if (instanced) {
                final boolean rebuild = SystemProperties.WEATHER_REBUILD_ALWAYS
                    || updateCount - builtUpdateCount >= REBUILD_TICKS
                    || columns.revision() != builtRevision
                    || entityBlockX != builtX || entityBlockY != builtY || entityBlockZ != builtZ
                    || cameraBlockY != builtCameraY
                    || radius != builtRadius;
                if (rebuild) {
                    VANILLA.begin(entityBlockX, entityBlockZ, WeatherInstancedAttribs.SOURCE_VANILLA);
                    VANILLA.beginLayer(RAIN_TEXTURE, WeatherInstancedAttribs.RAIN_PERIOD, 0.0F, 0.0F, WeatherInstancedAttribs.RAIN_FADE);
                    writeColumns(world, true);
                    VANILLA.beginLayer(SNOW_TEXTURE, WeatherInstancedAttribs.SNOW_PERIOD, WeatherInstancedAttribs.SNOW_U_DRIFT, WeatherInstancedAttribs.SNOW_V_DRIFT, WeatherInstancedAttribs.SNOW_FADE);
                    writeColumns(world, false);
                    VANILLA.upload();
                    builtCameraY = cameraBlockY;
                    builtRevision = columns.revision();
                    builtX = entityBlockX;
                    builtY = entityBlockY;
                    builtZ = entityBlockZ;
                    builtRadius = radius;
                    builtUpdateCount = updateCount;
                }
                rebuiltThisFrame = rebuild;
            } else {
                renderTessellator(mc, world);
            }
        }

        int providerTick = 0;
        float providerAge = 0.0F;
        float providerInvRadius = 0.0F;
        if (provider != null) {
            providerTick = provider.beginFrame(world, view, partialTicks);
            final int revision = provider.columnRevision();
            final float fadeRadius = provider.fadeRadius();
            if (!Float.isFinite(fadeRadius) || fadeRadius <= 0.0F) {
                throw new IllegalArgumentException("Weather fade radius must be finite and positive");
            }
            final int dimension = world.provider.dimensionId;
            if (SystemProperties.WEATHER_REBUILD_ALWAYS
                || revision != builtProviderRevision || dimension != builtProviderDimension
                || entityBlockX != builtProviderX || entityBlockZ != builtProviderZ) {
                PROVIDER.begin(entityBlockX, entityBlockZ, WeatherInstancedAttribs.SOURCE_PROVIDER);
                provider.collectColumns(PROVIDER);
                PROVIDER.upload();
                builtProviderRevision = revision;
                builtProviderDimension = dimension;
                builtProviderX = entityBlockX;
                builtProviderZ = entityBlockZ;
                providerRebuiltThisFrame = true;
            }
            providerAge = providerTick + partialTicks;
            providerInvRadius = 1.0F / fadeRadius;
        }

        if (instanced && (vanillaActive && VANILLA.count > 0 || provider != null && PROVIDER.count > 0)) {
            WeatherParams.set((float) (entityBlockX - cameraX), (float) -cameraY, (float) (entityBlockZ - cameraZ),
                radius > 0 ? 1.0F / radius : 0.0F,
                (float) (viewPosX - entityBlockX), (float) (viewPosZ - entityBlockZ), partialTicks,
                updateCount + partialTicks,
                updateCount, rainStrength, providerTick, providerAge, providerInvRadius);

            GLSMVertexInput.glBindVertexArray(WeatherQuadMesh.vao());
            if (deferred == null) GLSMPrograms.setFfpInstancing(Instancing.WEATHER);
            if (vanillaActive) {
                frameDrawCalls += VANILLA.draw(mc, deferred);
                frameQuads += VANILLA.count;
            }
            if (provider != null) {
                providerDraws = PROVIDER.draw(mc, deferred);
                providerColumns = PROVIDER.count;
            }
            if (deferred == null) GLSMPrograms.setFfpInstancing(Instancing.NONE);
            GLSMVertexInput.glBindVertexArray(0);
            if (deferred != null) deferred.rebindCurrentPass();
        }
        exitState(mc, partialTicks);
        return true;
    }

    private static boolean useLegacyHandler() {
        Tracy.plot(P_STRENGTH, 0.0);
        legacyHandler = true;
        return false;
    }

    private static void exitState(Minecraft mc, float partialTicks) {
        GLSMRasterizer.enableCull();
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        mc.entityRenderer.disableLightmap(partialTicks);
    }

    private static DeferredWorldRenderingPipeline deferredPipeline() {
        if (!AngelicaConfig.enableIris || !Iris.enabled) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline
            ? pipeline : null;
    }

    private static void writeColumns(WorldClient world, boolean wantRain) {
        final WorldChunkManager chunkManager = world.getWorldChunkManager();
        for (int z = entityBlockZ - radius; z <= entityBlockZ + radius; z++) {
            for (int x = entityBlockX - radius; x <= entityBlockX + radius; x++) {
                final int slot = columns.slotAt(x, z);
                if (!columns.precipitatesAtSlot(slot)) continue;

                final int precipitationHeight = columns.heightAtSlot(slot);
                final int bottom = Math.max(entityBlockY - radius, precipitationHeight);
                final int top = Math.max(entityBlockY + radius, precipitationHeight);
                if (bottom == top) continue;

                final float temperature = columns.temperatureAtSlot(slot, bottom);
                final boolean rain = chunkManager.getTemperatureAtHeight(temperature, precipitationHeight) >= 0.15F;
                if (rain != wantRain) continue;

                final int lightY = Math.max(precipitationHeight, cameraBlockY);
                final int light = columns.lightAtSlot(world, slot, x, z, lightY);

                final float j0 = columns.jitterAtSlot(slot, 0);
                VANILLA.put(x, z, bottom, top,
                    rain ? 0.0F : j0, rain ? 0.0F : columns.jitterAtSlot(slot, 1),
                    rain ? 0.0F : columns.jitterAtSlot(slot, 2), rain ? 0.0F : columns.jitterAtSlot(slot, 3),
                    rain ? light : snowBrightness(light), rain ? columns.hashAtSlot(slot) : 0,
                    rain ? WeatherInstancedAttribs.RAIN_SCROLL_BASE + j0 : 1.0F,
                    1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private static void renderTessellator(Minecraft mc, WorldClient world) {
        final float age = updateCount + partialTicks;
        final Tessellator tessellator = Tessellator.instance;
        final WorldChunkManager chunkManager = world.getWorldChunkManager();
        int bound = -1;
        int quads = 0;
        int drawCalls = 0;

        for (int z = entityBlockZ - radius; z <= entityBlockZ + radius; z++) {
            for (int x = entityBlockX - radius; x <= entityBlockX + radius; x++) {
                final int slot = columns.slotAt(x, z);
                if (!columns.precipitatesAtSlot(slot)) continue;

                final int precipitationHeight = columns.heightAtSlot(slot);
                final int bottom = Math.max(entityBlockY - radius, precipitationHeight);
                final int top = Math.max(entityBlockY + radius, precipitationHeight);
                if (bottom == top) continue;

                final int lightY = Math.max(precipitationHeight, cameraBlockY);
                final int light = columns.lightAtSlot(world, slot, x, z, lightY);
                final float temperature = columns.temperatureAtSlot(slot, bottom);
                final boolean rain = chunkManager.getTemperatureAtHeight(temperature, precipitationHeight) >= 0.15F;

                final int axis = (z - entityBlockZ + 16) * 32 + x - entityBlockX + 16;
                final float halfX = axisX[axis] * 0.5F;
                final float halfZ = axisZ[axis] * 0.5F;

                final double offsetX = (x + 0.5F) - viewPosX;
                final double offsetZ = (z + 0.5F) - viewPosZ;
                final float distance = MathHelper.sqrt_double(offsetX * offsetX + offsetZ * offsetZ) / radius;

                if (rain) {
                    if (bound != 0) {
                        if (bound >= 0) {
                            tessellator.draw();
                            drawCalls++;
                        }
                        bound = 0;
                        mc.getTextureManager().bindTexture(RAIN_TEXTURE);
                        tessellator.startDrawingQuads();
                    }
                    final float scroll = ((updateCount + columns.hashAtSlot(slot) & WeatherInstancedAttribs.RAIN_PERIOD - 1) + partialTicks)
                        / WeatherInstancedAttribs.RAIN_PERIOD * (WeatherInstancedAttribs.RAIN_SCROLL_BASE + columns.jitterAtSlot(slot, 0));
                    tessellator.setBrightness(light);
                    tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F,
                        ((1.0F - distance * distance) * WeatherInstancedAttribs.RAIN_FADE + 0.5F) * rainStrength);
                    emit(tessellator, x, z, bottom, top, halfX, halfZ, scroll, 0.0F, 0.0F);
                } else {
                    if (bound != 1) {
                        if (bound == 0) {
                            tessellator.draw();
                            drawCalls++;
                        }
                        bound = 1;
                        mc.getTextureManager().bindTexture(SNOW_TEXTURE);
                        tessellator.startDrawingQuads();
                    }
                    final float scroll = ((updateCount & WeatherInstancedAttribs.SNOW_PERIOD - 1) + partialTicks)
                        / WeatherInstancedAttribs.SNOW_PERIOD;
                    final float driftU = columns.jitterAtSlot(slot, 0) + age * WeatherInstancedAttribs.SNOW_U_DRIFT * columns.jitterAtSlot(slot, 1);
                    final float driftV = columns.jitterAtSlot(slot, 2) + age * columns.jitterAtSlot(slot, 3) * WeatherInstancedAttribs.SNOW_V_DRIFT;
                    tessellator.setBrightness(snowBrightness(light));
                    tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F,
                        ((1.0F - distance * distance) * WeatherInstancedAttribs.SNOW_FADE + 0.5F) * rainStrength);
                    emit(tessellator, x, z, bottom, top, halfX, halfZ, scroll, driftU, driftV);
                }
                quads++;
            }
        }

        if (bound >= 0) {
            tessellator.draw();
            drawCalls++;
        }

        frameQuads += quads;
        frameDrawCalls += drawCalls;
    }

    private static int snowBrightness(int light) {
        return (light * 3 + 15728880) / 4;
    }

    private static void emit(Tessellator tessellator, int x, int z, int bottom, int top, float halfX,
        float halfZ, float scroll, float driftU, float driftV) {
        tessellator.setTranslation(-cameraX, -cameraY, -cameraZ);
        tessellator.addVertexWithUV(x - halfX + 0.5D, bottom, z - halfZ + 0.5D, driftU, bottom / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x + halfX + 0.5D, bottom, z + halfZ + 0.5D, 1.0F + driftU, bottom / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x + halfX + 0.5D, top, z + halfZ + 0.5D, 1.0F + driftU, top / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x - halfX + 0.5D, top, z - halfZ + 0.5D, driftU, top / 4.0F + scroll + driftV);
        tessellator.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static void report(long startNanos) {
        Tracy.plotInt(P_QUADS, frameQuads);
        Tracy.plotInt(P_DRAW_CALLS, frameDrawCalls);
        Tracy.plotInt(P_REBUILDS, rebuiltThisFrame ? 1 : 0);
        Tracy.plotInt(P_PROVIDER_COLUMNS, providerColumns);
        Tracy.plotInt(P_PROVIDER_DRAWS, providerDraws);
        Tracy.plotInt(P_PROVIDER_REBUILDS, providerRebuiltThisFrame ? 1 : 0);
        Tracy.plotInt(P_LEGACY_HANDLER, legacyHandler ? 1 : 0);
        Tracy.plot(P_MICROS, (System.nanoTime() - startNanos) / 1000.0);
        rebuiltThisFrame = false;
        providerRebuiltThisFrame = false;
    }

    private static void buildAxisTable() {
        if (axisX != null) return;
        axisX = new float[1024];
        axisZ = new float[1024];
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                final float dx = x - 16;
                final float dz = z - 16;
                final float length = MathHelper.sqrt_float(dx * dx + dz * dz);
                axisX[z << 5 | x] = -dz / length;
                axisZ[z << 5 | x] = dx / length;
            }
        }
    }

    public static void clear() {
        VANILLA.release();
        PROVIDER.release();
        WeatherQuadMesh.delete();
        builtRevision = Integer.MIN_VALUE;
        builtX = Integer.MIN_VALUE;
        builtY = Integer.MIN_VALUE;
        builtZ = Integer.MIN_VALUE;
        builtRadius = -1;
        builtUpdateCount = Integer.MIN_VALUE;
        builtCameraY = Integer.MIN_VALUE;
        builtProviderRevision = Integer.MIN_VALUE;
        builtProviderDimension = Integer.MIN_VALUE;
        builtProviderX = Integer.MIN_VALUE;
        builtProviderZ = Integer.MIN_VALUE;
    }

    private static final class WeatherBatch implements WeatherColumnSink {

        private static final int STRIDE = WeatherInstancedAttribs.STRIDE;
        private static final int MIN_CAPACITY = 256;

        private ByteBuffer data;
        private int capacity;
        private int count;
        private ResourceLocation[] textures = new ResourceLocation[4];
        private int[] firsts = new int[4];
        private int layers;
        private int originX;
        private int originZ;
        private float source;
        private int period;
        private float uDriftScale;
        private float vDriftScale;
        private float fadeScale;
        private int buffer;
        private int bufferCapacity;

        void begin(int originX, int originZ, float source) {
            this.originX = originX;
            this.originZ = originZ;
            this.source = source;
            Arrays.fill(textures, 0, layers, null);
            layers = 0;
            count = 0;
        }

        void upload() {
            if (count == 0) return;
            if (buffer == 0) buffer = GLSMBuffers.glGenBuffers();
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            if (bufferCapacity < count) {
                bufferCapacity = capacity;
                GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, (long) bufferCapacity * STRIDE, GL15.GL_DYNAMIC_DRAW);
            }
            data.position(0);
            data.limit(count * STRIDE);
            GLSMBuffers.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, data);
            data.clear();
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        int draw(Minecraft mc, DeferredWorldRenderingPipeline deferred) {
            int draws = 0;
            for (int layer = 0; layer < layers; layer++) {
                final int first = firsts[layer];
                final int layerCount = (layer + 1 < layers ? firsts[layer + 1] : count) - first;
                if (layerCount == 0) continue;
                mc.getTextureManager().bindTexture(textures[layer]);
                if (deferred != null) deferred.bindInstancedVariant(Instancing.WEATHER);
                GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                WeatherInstancedAttribs.pointInstanceAttribs((long) first * STRIDE);
                GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                VAOManager.setCurrentVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);
                GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLE_FAN, 0, WeatherQuadMesh.VERTEX_COUNT, layerCount);
                draws++;
            }
            return draws;
        }

        void release() {
            begin(0, 0, 0.0F);
            data = null;
            capacity = 0;
            if (buffer != 0) {
                GLSMBuffers.glDeleteBuffers(buffer);
                buffer = 0;
            }
            bufferCapacity = 0;
        }

        @Override
        public void beginLayer(ResourceLocation texture, int scrollPeriod, float uDriftScale, float vDriftScale, float fadeScale) {
            if (texture == null) throw new IllegalArgumentException("Weather layer texture must not be null");
            if (scrollPeriod < 1 || scrollPeriod > WeatherParams.TICK_WRAP || (scrollPeriod & scrollPeriod - 1) != 0) {
                throw new IllegalArgumentException("Weather layer scroll period must be a power of two between 1 and " + WeatherParams.TICK_WRAP);
            }
            if (!Float.isFinite(uDriftScale) || !Float.isFinite(vDriftScale) || !Float.isFinite(fadeScale)) {
                throw new IllegalArgumentException("Weather layer scales must be finite");
            }
            if (layers == textures.length) {
                textures = Arrays.copyOf(textures, layers * 2);
                firsts = Arrays.copyOf(firsts, layers * 2);
            }
            textures[layers] = texture;
            firsts[layers] = count;
            layers++;
            period = scrollPeriod;
            this.uDriftScale = uDriftScale;
            this.vDriftScale = vDriftScale;
            this.fadeScale = fadeScale;
        }

        @Override
        public void column(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
                           int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a) {
            if (layers == 0) throw new IllegalStateException("Weather column written before beginLayer");
            if (brightness < 0 || brightness >= 1 << 24) {
                throw new IllegalArgumentException("Weather column brightness must be a packed lightmap coordinate below 2^24");
            }
            put(x, z, bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale, r, g, b, a);
        }

        void put(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift, int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a) {
            if (count == capacity) grow();
            WeatherInstancedAttribs.putColumn(data, count, x - originX, z - originZ, bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale, source, r, g, b, a, period, uDriftScale, vDriftScale, fadeScale);
            count++;
        }

        private void grow() {
            final int grownCapacity = Math.max(MIN_CAPACITY, capacity * 2);
            final ByteBuffer grown = ByteBuffer.allocateDirect(grownCapacity * STRIDE).order(ByteOrder.nativeOrder());
            if (count > 0) {
                data.position(0);
                data.limit(count * STRIDE);
                grown.put(data);
                grown.clear();
            }
            data = grown;
            capacity = grownCapacity;
        }
    }
}
