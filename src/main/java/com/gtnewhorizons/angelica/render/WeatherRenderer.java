package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.compat.dragonapi.DragonAPICompat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Replaces EntityRenderer.renderRainSnow.
 *
 * <p>The whole grid is one instanced draw per precipitation type. Everything that varies per frame
 * (billboard axis, distance falloff, texture scroll, snow drift) happens in the vertex shader, and the
 * instance buffer is only rebuilt when the camera changes block or {@link WeatherColumns} actually
 * resamples something. Per-column data comes from that cache rather than being recomputed every frame.
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

    private static final WeatherColumns columns = new WeatherColumns();

    private static float[] axisX;
    private static float[] axisZ;

    private static int instanceBuffer;
    private static ByteBuffer instanceData;
    private static int instanceCapacity;
    private static int instanceCount;
    private static int rainInstances;

    private static int builtRevision = Integer.MIN_VALUE;
    private static int builtX = Integer.MIN_VALUE;
    private static int builtY = Integer.MIN_VALUE;
    private static int builtZ = Integer.MIN_VALUE;
    private static int builtRadius = -1;
    private static int builtUpdateCount = Integer.MIN_VALUE;
    private static int builtCameraY = Integer.MIN_VALUE;
    private static boolean rebuiltThisFrame;

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
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient world = mc.theWorld;
        final long startNanos = System.nanoTime();
        if (!AngelicaConfig.enableInstancedWeather) return false;
        if (world.provider.getWeatherRenderer() != null) {
            Tracy.plot(P_STRENGTH, 0.0);
            report(0, 0, startNanos);
            return false;
        }
        if (DragonAPICompat.forcingSeasonalSnow) {
            report(0, 0, startNanos);
            return false;
        }
        final float rainStrength = world.getRainStrength(partialTicks);
        Tracy.plot(P_STRENGTH, rainStrength);

        final int radius = SettingsManager.downfallDistance;
        if (rainStrength <= 0.0F || radius <= 0) {
            report(0, 0, startNanos);
            return true;
        }
        buildAxisTable();

        final EntityLivingBase view = mc.renderViewEntity;
        final int entityBlockX = MathHelper.floor_double(view.posX);
        final int entityBlockY = MathHelper.floor_double(view.posY);
        final int entityBlockZ = MathHelper.floor_double(view.posZ);

        final double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        final double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        final double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        final int cameraBlockY = MathHelper.floor_double(cameraY);

        final int updateCount = ((EntityRendererAccessor) mc.entityRenderer).getRendererUpdateCount();
        columns.update(world, entityBlockX, entityBlockZ, radius, updateCount);

        final DeferredWorldRenderingPipeline deferred = deferredPipeline();

        if (deferred != null && !deferred.supportsInstancing(Instancing.WEATHER)) {
            renderTessellator(mc, world, view, partialTicks, rainStrength, radius, updateCount,
                entityBlockX, entityBlockY, entityBlockZ, cameraX, cameraY, cameraZ, cameraBlockY, startNanos);
            return true;
        }

        renderInstanced(mc, world, view, deferred, partialTicks, rainStrength, radius, updateCount,
            entityBlockX, entityBlockY, entityBlockZ, cameraX, cameraY, cameraZ, cameraBlockY, startNanos);
        return true;
    }

    private static DeferredWorldRenderingPipeline deferredPipeline() {
        if (!AngelicaConfig.enableIris || !Iris.enabled) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline pipeline
            ? pipeline : null;
    }

    private static void renderInstanced(Minecraft mc, WorldClient world, EntityLivingBase view,
        DeferredWorldRenderingPipeline deferred, float partialTicks, float rainStrength, int radius,
        int updateCount, int entityBlockX, int entityBlockY, int entityBlockZ, double cameraX,
        double cameraY, double cameraZ, int cameraBlockY, long startNanos) {

        final boolean rebuild = SystemProperties.WEATHER_REBUILD_ALWAYS
            || updateCount - builtUpdateCount >= REBUILD_TICKS
            || columns.revision() != builtRevision
            || entityBlockX != builtX || entityBlockY != builtY || entityBlockZ != builtZ
            || cameraBlockY != builtCameraY
            || radius != builtRadius;
        if (rebuild) {
            rebuildInstances(world, radius, entityBlockX, entityBlockY, entityBlockZ, cameraBlockY);
            builtCameraY = cameraBlockY;
            builtRevision = columns.revision();
            builtX = entityBlockX;
            builtY = entityBlockY;
            builtZ = entityBlockZ;
            builtRadius = radius;
            builtUpdateCount = updateCount;
        }
        rebuiltThisFrame = rebuild;

        if (instanceCount == 0) {
            report(0, 0, startNanos);
            return;
        }

        if (deferred != null) {
            deferred.rebindCurrentPass();
            if (!deferred.hasInstancedVariant(Instancing.WEATHER)) {
                renderTessellator(mc, world, view, partialTicks, rainStrength, radius, updateCount,
                    entityBlockX, entityBlockY, entityBlockZ, cameraX, cameraY, cameraZ, cameraBlockY, startNanos);
                return;
            }
        }

        WeatherParams.set((float) (entityBlockX - cameraX), (float) -cameraY, (float) (entityBlockZ - cameraZ),
            1.0F / radius,
            (float) (view.posX - entityBlockX), (float) (view.posZ - entityBlockZ), partialTicks,
            updateCount + partialTicks,
            updateCount & 31, updateCount & 511, rainStrength);

        mc.entityRenderer.enableLightmap(partialTicks);
        GLStateManager.disableCull();
        GLStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
        GLStateManager.enableBlend();
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        int drawCalls = 0;
        drawCalls += drawRange(mc, deferred, RAIN_TEXTURE, 0, rainInstances);
        drawCalls += drawRange(mc, deferred, SNOW_TEXTURE, rainInstances, instanceCount - rainInstances);

        GLStateManager.enableCull();
        GLStateManager.disableBlend();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        mc.entityRenderer.disableLightmap(partialTicks);

        report(instanceCount, drawCalls, startNanos);
    }

    private static int drawRange(Minecraft mc, DeferredWorldRenderingPipeline deferred,
        ResourceLocation texture, int first, int count) {
        if (count <= 0) return 0;
        mc.getTextureManager().bindTexture(texture);

        if (deferred != null) {
            deferred.rebindCurrentPass();
            if (!deferred.hasInstancedVariant(Instancing.WEATHER)) return 0;
            deferred.bindInstancedVariant(Instancing.WEATHER);
        }

        GLStateManager.glBindVertexArray(WeatherQuadMesh.vao());
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        WeatherInstancedAttribs.pointInstanceAttribs((long) first * WeatherInstancedAttribs.STRIDE);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        VAOManager.setCurrentVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);
        if (deferred == null) GLStateManager.ffpInstancing = Instancing.WEATHER;
        GLStateManager.glDrawArraysInstanced(GL11.GL_TRIANGLE_FAN, 0, WeatherQuadMesh.VERTEX_COUNT, count);
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);

        if (deferred != null) deferred.rebindCurrentPass();
        return 1;
    }

    private static void rebuildInstances(WorldClient world, int radius, int entityBlockX, int entityBlockY,
        int entityBlockZ, int cameraBlockY) {
        final int slots = (radius * 2 + 1) * (radius * 2 + 1);
        final int stride = WeatherInstancedAttribs.STRIDE;
        if (instanceCapacity < slots) {
            instanceCapacity = slots;
            instanceData = ByteBuffer.allocateDirect(slots * stride).order(ByteOrder.nativeOrder());
            if (instanceBuffer == 0) instanceBuffer = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, (long) slots * stride, GL15.GL_DYNAMIC_DRAW);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            WeatherQuadMesh.bindInstanceBuffer(instanceBuffer);
        }

        int count = writeColumns(world, radius, entityBlockX, entityBlockY, entityBlockZ, cameraBlockY, true, 0);
        rainInstances = count;
        count = writeColumns(world, radius, entityBlockX, entityBlockY, entityBlockZ, cameraBlockY, false, count);
        instanceCount = count;
        if (count == 0) return;

        instanceData.position(0);
        instanceData.limit(count * stride);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        GLStateManager.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, instanceData);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        instanceData.clear();
    }

    private static int writeColumns(WorldClient world, int radius, int entityBlockX, int entityBlockY,
        int entityBlockZ, int cameraBlockY, boolean wantRain, int count) {
        final int stride = WeatherInstancedAttribs.STRIDE;
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
                int brightness = columns.lightAtSlot(world, slot, x, z, lightY);
                if (!rain) brightness = (brightness * 3 + 15728880) / 4;

                final int base = count * stride;
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN, x - entityBlockX);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN + 4, z - entityBlockZ);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN + 8, bottom);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN + 12, top);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_JITTER, columns.jitterAtSlot(slot, 0));
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_JITTER + 4, columns.jitterAtSlot(slot, 1));
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_JITTER + 8, columns.jitterAtSlot(slot, 2));
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_JITTER + 12, columns.jitterAtSlot(slot, 3));
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_PARAMS, brightness & 0xFFFF);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_PARAMS + 4, brightness >> 16 & 0xFFFF);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_PARAMS + 8, columns.hashAtSlot(slot) & 31);
                instanceData.putFloat(base + WeatherInstancedAttribs.OFFSET_PARAMS + 12, rain ? 0.0F : 1.0F);
                count++;
            }
        }
        return count;
    }

    private static void renderTessellator(Minecraft mc, WorldClient world, EntityLivingBase view,
        float partialTicks, float rainStrength, int radius, int updateCount, int entityBlockX,
        int entityBlockY, int entityBlockZ, double cameraX, double cameraY, double cameraZ,
        int cameraBlockY, long startNanos) {

        mc.entityRenderer.enableLightmap(partialTicks);

        final float age = updateCount + partialTicks;
        GLStateManager.disableCull();
        GLStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
        GLStateManager.enableBlend();
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        final Tessellator tessellator = Tessellator.instance;
        int bound = -1;
        int quads = 0;
        int drawCalls = 0;

        for (int z = entityBlockZ - radius; z <= entityBlockZ + radius; z++) {
            for (int x = entityBlockX - radius; x <= entityBlockX + radius; x++) {
                if (!columns.precipitates(x, z)) continue;

                final int precipitationHeight = columns.heightAt(x, z);
                final int bottom = Math.max(entityBlockY - radius, precipitationHeight);
                final int top = Math.max(entityBlockY + radius, precipitationHeight);
                if (bottom == top) continue;

                final int lightY = Math.max(precipitationHeight, cameraBlockY);
                final float temperature = columns.temperatureAt(x, z, bottom);
                final boolean rain = world.getWorldChunkManager()
                    .getTemperatureAtHeight(temperature, precipitationHeight) >= 0.15F;

                final int axis = (z - entityBlockZ + 16) * 32 + x - entityBlockX + 16;
                final float halfX = axisX[axis] * 0.5F;
                final float halfZ = axisZ[axis] * 0.5F;

                final double offsetX = (x + 0.5F) - view.posX;
                final double offsetZ = (z + 0.5F) - view.posZ;
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
                    final float scroll = ((updateCount + columns.hashAt(x, z) & 31) + partialTicks) / 32.0F
                        * (3.0F + columns.jitterAt(x, z, 0));
                    tessellator.setBrightness(columns.lightAt(world, x, z, lightY));
                    tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F,
                        ((1.0F - distance * distance) * 0.5F + 0.5F) * rainStrength);
                    emit(tessellator, x, z, bottom, top, halfX, halfZ, scroll, 0.0F, 0.0F,
                        cameraX, cameraY, cameraZ);
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
                    final float scroll = ((updateCount & 511) + partialTicks) / 512.0F;
                    final float driftU = columns.jitterAt(x, z, 0) + age * 0.01F * columns.jitterAt(x, z, 1);
                    final float driftV = columns.jitterAt(x, z, 2) + age * columns.jitterAt(x, z, 3) * 0.001F;
                    tessellator.setBrightness((columns.lightAt(world, x, z, lightY) * 3 + 15728880) / 4);
                    tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F,
                        ((1.0F - distance * distance) * 0.3F + 0.5F) * rainStrength);
                    emit(tessellator, x, z, bottom, top, halfX, halfZ, scroll, driftU, driftV,
                        cameraX, cameraY, cameraZ);
                }
                quads++;
            }
        }

        if (bound >= 0) {
            tessellator.draw();
            drawCalls++;
        }

        GLStateManager.enableCull();
        GLStateManager.disableBlend();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        mc.entityRenderer.disableLightmap(partialTicks);

        report(quads, drawCalls, startNanos);
    }

    private static void emit(Tessellator tessellator, int x, int z, int bottom, int top, float halfX,
        float halfZ, float scroll, float driftU, float driftV, double cameraX, double cameraY, double cameraZ) {
        tessellator.setTranslation(-cameraX, -cameraY, -cameraZ);
        tessellator.addVertexWithUV(x - halfX + 0.5D, bottom, z - halfZ + 0.5D, driftU, bottom / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x + halfX + 0.5D, bottom, z + halfZ + 0.5D, 1.0F + driftU, bottom / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x + halfX + 0.5D, top, z + halfZ + 0.5D, 1.0F + driftU, top / 4.0F + scroll + driftV);
        tessellator.addVertexWithUV(x - halfX + 0.5D, top, z - halfZ + 0.5D, driftU, top / 4.0F + scroll + driftV);
        tessellator.setTranslation(0.0D, 0.0D, 0.0D);
    }

    private static void report(int quads, int drawCalls, long startNanos) {
        Tracy.plotInt(P_QUADS, quads);
        Tracy.plotInt(P_DRAW_CALLS, drawCalls);
        Tracy.plotInt(P_REBUILDS, rebuiltThisFrame ? 1 : 0);
        Tracy.plot(P_MICROS, (System.nanoTime() - startNanos) / 1000.0);
        rebuiltThisFrame = false;
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
        WeatherQuadMesh.delete();
        if (instanceBuffer != 0) {
            GLStateManager.glDeleteBuffers(instanceBuffer);
            instanceBuffer = 0;
        }
        instanceData = null;
        instanceCapacity = 0;
        instanceCount = 0;
        rainInstances = 0;
    }
}
