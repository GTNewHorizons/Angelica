/*
 * Backported from Minecraft Forge 1.12 under the LGPL 2.1
 *
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.api.clouds.CloudLayer;
import com.gtnewhorizons.angelica.api.clouds.CloudLayerProvider;
import com.gtnewhorizons.angelica.compat.galaxyspace.GalaxySpaceClouds;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IRenderHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.render.CloudDisc.CELLS_PER_CHUNK;
import static com.gtnewhorizons.angelica.render.CloudDisc.MARGIN_CELLS;
import static com.gtnewhorizons.angelica.render.CloudLayerRenderer.MODE_FANCY;
import static com.gtnewhorizons.angelica.render.CloudLayerRenderer.MODE_FAST;

public class CloudRenderer implements IResourceManagerReloadListener {

    private static final ResourceLocation VANILLA_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
    private static final float MAX_FAR_PLANE_DISTANCE = 65536.0f;
    private static CloudRenderer instance;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final CloudRenderResources resources = new CloudRenderResources();
    private final CloudView cloudView = new CloudView();
    private final Map<String, CloudLayerRenderer> layers = new HashMap<>();
    private final List<CloudLayerRenderer> drawOrder = new ArrayList<>();
    private final Set<String> activeIds = new HashSet<>();
    private WorldClient world;
    private int cloudMode = MODE_FAST, renderDistance, scaleMult = 1, lastCloudTicks;
    private boolean enabled;

    public CloudRenderer() {
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
    }

    public static CloudRenderer getCloudRenderer() {
        if (instance == null) instance = new CloudRenderer();
        return instance;
    }

    public static int getCloudTextureWidth() {
        if (instance == null) return 256;
        final int width = instance.resources.texture(VANILLA_TEXTURE).width;
        return width > 0 ? width : 256;
    }

    public static int getScaleMult() {
        return instance != null && instance.scaleMult > 0 ? instance.scaleMult : 1;
    }

    public static boolean supportsLayers(WorldClient world, IRenderHandler handler) {
        if (world == null) return false;
        if (handler instanceof CloudLayerProvider) return true;
        if (world.provider instanceof CloudLayerProvider) return true;
        return GalaxySpaceClouds.supports(world, handler);
    }

    public void checkSettings() {
        if (world != mc.theWorld) {
            clearLayers();
            resources.clear();
            world = mc.theWorld;
            lastCloudTicks = 0;
        }
        final GraphicsQualityOff quality = (GraphicsQualityOff) Settings.MODE_CLOUDS.option.getStore();
        cloudMode = quality == GraphicsQualityOff.FANCY
            || quality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics ? MODE_FANCY : MODE_FAST;
        renderDistance = Math.max(mc.gameSettings.renderDistanceChunks, (int) Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        scaleMult = (int) Settings.CLOUD_SCALE.option.getStore();
        enabled = quality != GraphicsQualityOff.OFF && mc.gameSettings.shouldRenderClouds()
            && world != null && scaleMult > 0;
        if (!enabled && !layers.isEmpty()) clearLayers();
    }

    public boolean render(int cloudTicks, float partialTicks) {
        if (mc.theWorld == null) return false;
        return render(cloudTicks, partialTicks, mc.theWorld.provider.getCloudRenderer());
    }

    public boolean render(int cloudTicks, float partialTicks, IRenderHandler handler) {
        checkSettings();
        if (!enabled || mc.renderViewEntity == null) return false;
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null && pipeline.getCloudSetting() == CloudSetting.OFF) return false;
        lastCloudTicks = cloudTicks;

        final List<CloudLayer> descriptions = describeLayers(handler, cloudTicks, partialTicks);
        activeIds.clear();
        drawOrder.clear();
        for (CloudLayer description : descriptions) {
            if (!activeIds.add(description.id())) {
                throw new IllegalArgumentException("Duplicate cloud layer id: " + description.id());
            }
            CloudLayerRenderer renderer = layers.get(description.id());
            if (renderer != null && !renderer.texture.location.equals(description.texture())) {
                renderer.delete();
                layers.remove(description.id());
                renderer = null;
            }
            if (renderer == null) {
                renderer = new CloudLayerRenderer(resources, resources.texture(description.texture()));
                layers.put(description.id(), renderer);
            }
            renderer.configure(description, cloudMode, renderDistance, scaleMult);
            if (description.alpha() > 0.0f) drawOrder.add(renderer);
        }
        layers.entrySet().removeIf(entry -> {
            if (activeIds.contains(entry.getKey())) return false;
            entry.getValue().delete();
            return true;
        });

        final Entity view = mc.renderViewEntity;
        final double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        drawOrder.sort((a, b) -> Double.compare(distanceToLayer(b.layer, cameraY), distanceToLayer(a.layer, cameraY)));
        final ViewportState viewport = GLStateManager.getViewportState();
        cloudView.update(GLStateManager.getProjectionMatrix(), GLStateManager.getModelViewMatrix(), viewport.width, viewport.height);
        boolean rendered = descriptions.isEmpty();
        for (CloudLayerRenderer renderer : drawOrder) rendered |= renderer.render(partialTicks, cloudView);
        return rendered;
    }

    private double distanceToLayer(CloudLayer layer, double cameraY) {
        final double bottom = layer.height() + 0.33;
        final double top = bottom + (cloudMode == MODE_FANCY ? layer.cellHeight() * scaleMult : 0.0);
        return Math.max(0.0, Math.max(bottom - cameraY, cameraY - top));
    }

    private List<CloudLayer> describeLayers(IRenderHandler handler, int cloudTicks, float partialTicks) {
        if (handler instanceof CloudLayerProvider provider) {
            return provider.getCloudLayers(world, cloudTicks, partialTicks);
        }
        if (world.provider instanceof CloudLayerProvider provider) {
            return provider.getCloudLayers(world, cloudTicks, partialTicks);
        }
        if (GalaxySpaceClouds.supports(world, handler)) {
            return GalaxySpaceClouds.getLayers(world, cloudTicks, partialTicks);
        }
        if (handler != null || !world.provider.isSurfaceWorld()) return List.of();
        final float height = world.provider.getCloudHeight();
        if (!Float.isFinite(height)) return List.of();
        final Vec3 color = world.getCloudColour(partialTicks);
        return List.of(new CloudLayer("minecraft:clouds", VANILLA_TEXTURE, height,
            12.0f, 4.0f, 12.0f, (cloudTicks + (double) partialTicks) * 0.03 / (12.0 * scaleMult),
            0.33000001311302185, (float) color.xCoord, (float) color.yCoord, (float) color.zCoord, CloudUniforms.ALPHA));
    }

    public float getRequiredFarPlaneDistance() {
        checkSettings();
        if (!enabled || renderDistance <= 0) return 0.0f;
        float required = 0.0f;
        final Entity view = mc.renderViewEntity;
        for (CloudLayer layer : describeLayers(world.provider.getCloudRenderer(), lastCloudTicks, 1.0f)) {
            if (layer.alpha() == 0.0f) continue;
            final float width = layer.cellWidth() * scaleMult;
            final int radiusChunks = (int) Math.ceil(renderDistance * 64.0f / (CELLS_PER_CHUNK * width));
            final int marginChunks = (MARGIN_CELLS + CELLS_PER_CHUNK - 1) / CELLS_PER_CHUNK;
            float radius = (radiusChunks + marginChunks) * CELLS_PER_CHUNK * width;
            if (cloudMode != MODE_FANCY) radius *= (float) Math.sqrt(2);
            radius += MARGIN_CELLS * width;
            final float cameraY = view == null ? 0.0f : (float) view.posY;
            final float verticalSlack = Math.abs(layer.height() + 0.33f - cameraY) + layer.cellHeight() * scaleMult;
            required = Math.max(required, Math.min(radius + verticalSlack, MAX_FAR_PLANE_DISTANCE));
        }
        return required;
    }

    private void clearLayers() {
        for (CloudLayerRenderer layer : layers.values()) layer.delete();
        layers.clear();
        drawOrder.clear();
        activeIds.clear();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        clearLayers();
        resources.clear();
    }
}
