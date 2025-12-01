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

import com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class CloudRenderer implements IResourceManagerReloadListener {
    // Shared constants.
    private static final float PX_SIZE = 1 / 256F;

    // Building constants.
    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_TEXTURE_COLOR;
    private static final int TOP_SECTIONS = 12;    // Number of slices a top face will span.
    private static final int HEIGHT = 4;
    private static final float INSET = 0.001F;
    private static final float ALPHA = 0.8F;

    // Debug
    private static final boolean WIREFRAME = false;

    // Instance fields
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ResourceLocation texture = new ResourceLocation("textures/environment/clouds.png");

    private VertexBuffer vbo;
    private int cloudMode = -1;
    private int renderDistance = -1;
    private int cloudElevation = -1;
    private int scaleMult = -1;

    private DynamicTexture COLOR_TEX = null;
    private int texColor;

    private int texW;
    private int texH;

    public CloudRenderer() {
        // Resource manager should always be reloadable.
        ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
    }

    private static CloudRenderer instance;
    public static CloudRenderer getCloudRenderer() {
        if (instance == null)
            instance = new CloudRenderer();
        return instance;
    }

    private int getScale() {
        return (cloudMode == 2 ? 12 : 8) * scaleMult;
    }

    private float ceilToScale(float value) {
        float scale = getScale();
        return (float)Math.ceil(value / scale) * scale;
    }

    private void vertices(CapturingTessellator tessellator) {
        GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
        boolean fancy = cloudGraphicsQuality == GraphicsQualityOff.FANCY || cloudGraphicsQuality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics;

        float scale = getScale();
        float CULL_DIST = 2 * scale;

        float bCol = fancy ? 0.7F : 1F;

        float sectEnd = ceilToScale((renderDistance * 2) * 16);
        float sectStart = -sectEnd;

        float sectStep = ceilToScale(sectEnd * 2 / TOP_SECTIONS);
        float sectPx = PX_SIZE / scale;

        tessellator.startDrawingQuads();

        float sectX0 = sectStart;
        float sectX1 = sectX0;

        while (sectX1 < sectEnd) {
            sectX1 += sectStep;

            if (sectX1 > sectEnd)
                sectX1 = sectEnd;

            float sectZ0 = sectStart;
            float sectZ1 = sectZ0;

            while (sectZ1 < sectEnd) {
                sectZ1 += sectStep;

                if (sectZ1 > sectEnd)
                    sectZ1 = sectEnd;

                float u0 = sectX0 * sectPx;
                float u1 = sectX1 * sectPx;
                float v0 = sectZ0 * sectPx;
                float v1 = sectZ1 * sectPx;

                // Bottom
                tessellator.pos(sectX0, 0, sectZ0).tex(u0, v0).color(bCol, bCol, bCol, ALPHA).endVertex();
                tessellator.pos(sectX1, 0, sectZ0).tex(u1, v0).color(bCol, bCol, bCol, ALPHA).endVertex();
                tessellator.pos(sectX1, 0, sectZ1).tex(u1, v1).color(bCol, bCol, bCol, ALPHA).endVertex();
                tessellator.pos(sectX0, 0, sectZ1).tex(u0, v1).color(bCol, bCol, bCol, ALPHA).endVertex();

                if (fancy) {
                    final int height = HEIGHT * scaleMult;
                    // Top
                    tessellator.pos(sectX0, height, sectZ0).tex(u0, v0).color(1, 1, 1, ALPHA).endVertex();
                    tessellator.pos(sectX0, height, sectZ1).tex(u0, v1).color(1, 1, 1, ALPHA).endVertex();
                    tessellator.pos(sectX1, height, sectZ1).tex(u1, v1).color(1, 1, 1, ALPHA).endVertex();
                    tessellator.pos(sectX1, height, sectZ0).tex(u1, v0).color(1, 1, 1, ALPHA).endVertex();

                    float slice;
                    float sliceCoord0;
                    float sliceCoord1;

                    for (slice = sectX0; slice < sectX1; ) {
                        sliceCoord0 = slice * sectPx;
                        sliceCoord1 = sliceCoord0 + PX_SIZE;

                        // X sides
                        if (slice > -CULL_DIST) {
                            slice += INSET;
                            tessellator.pos(slice, 0, sectZ1).tex(sliceCoord0, v1).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, height, sectZ1).tex(sliceCoord1, v1).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, height, sectZ0).tex(sliceCoord1, v0).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, 0, sectZ0).tex(sliceCoord0, v0).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            slice -= INSET;
                        }

                        slice += scale;

                        if (slice <= CULL_DIST) {
                            slice -= INSET;
                            tessellator.pos(slice, 0, sectZ0).tex(sliceCoord0, v0).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, height, sectZ0).tex(sliceCoord1, v0).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, height, sectZ1).tex(sliceCoord1, v1).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            tessellator.pos(slice, 0, sectZ1).tex(sliceCoord0, v1).color(0.9F, 0.9F, 0.9F, ALPHA).endVertex();
                            slice += INSET;
                        }
                    }

                    for (slice = sectZ0; slice < sectZ1; ) {
                        sliceCoord0 = slice * sectPx;
                        sliceCoord1 = sliceCoord0 + PX_SIZE;

                        // Z sides
                        if (slice > -CULL_DIST) {
                            slice += INSET;
                            tessellator.pos(sectX0, 0, slice).tex(u0, sliceCoord0).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX0, height, slice).tex(u0, sliceCoord1).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX1, height, slice).tex(u1, sliceCoord1).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX1, 0, slice).tex(u1, sliceCoord0).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            slice -= INSET;
                        }

                        slice += scale;

                        if (slice <= CULL_DIST) {
                            slice -= INSET;
                            tessellator.pos(sectX1, 0, slice).tex(u1, sliceCoord0).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX1, height, slice).tex(u1, sliceCoord1).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX0, height, slice).tex(u0, sliceCoord1).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            tessellator.pos(sectX0, 0, slice).tex(u0, sliceCoord0).color(0.8F, 0.8F, 0.8F, ALPHA).endVertex();
                            slice += INSET;
                        }
                    }
                }

                sectZ0 = sectZ1;
            }

            sectX0 = sectX1;
        }
    }

    private void dispose() {
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
    }

    private void build() {
        CapturingTessellator tess = TessellatorManager.startCapturingAndGet();

        vertices(tess);

        this.vbo = TessellatorManager.stopCapturingToVAO(FORMAT);
    }

    private int fullCoord(double coord, int scale) {   // Corrects misalignment of UV offset when on negative coords.
        return ((int) coord / scale) - (coord < 0 ? 1 : 0);
    }

    private boolean isBuilt() {
        return vbo != null;
    }

    public void checkSettings() {
        GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
        final int cloudQualitySetting = cloudGraphicsQuality == GraphicsQualityOff.FANCY
            || cloudGraphicsQuality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics ? 2 : 1;
        final boolean newEnabled = cloudGraphicsQuality != GraphicsQualityOff.OFF
            && mc.gameSettings.shouldRenderClouds()
            && mc.theWorld != null
            && mc.theWorld.provider.isSurfaceWorld();
        final int targetDistance = Math.max(mc.gameSettings.renderDistanceChunks, (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        final int cloudScaleMult = (int)Settings.CLOUD_SCALE.option.getStore();

        if (isBuilt()
            && (!newEnabled
            || cloudQualitySetting != cloudMode
            || targetDistance != renderDistance
            || cloudScaleMult != scaleMult)) {
            dispose();
        }

        cloudMode = cloudQualitySetting;
        renderDistance = targetDistance;
        scaleMult = cloudScaleMult;

        cloudElevation = mc.theWorld == null ? 128 : (int)mc.theWorld.provider.getCloudHeight();
        //Allows the setting to work with RFG and similar without hardcoding.
        //The minimum height check is so stuff like Aether cloud height doesn't get messed up.
        if(cloudElevation >= 96) {
            cloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }

        if (newEnabled && !isBuilt()) {
            build();
        }
    }

    public boolean render(int cloudTicks, float partialTicks) {
        if (!isBuilt())
            return false;

        Entity entity = mc.renderViewEntity;

        double totalOffset = cloudTicks + partialTicks;

        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks + totalOffset * 0.03;
        double y = cloudElevation - (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks) + 0.33;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

        int scale = getScale();

        if (cloudMode == 2)
            z += 0.33 * scale;

        // Integer UVs to translate the texture matrix by.
        int offU = fullCoord(x, scale);
        int offV = fullCoord(z, scale);

        GLStateManager.glPushMatrix();

        // Translate by the remainder after the UV offset.
        GLStateManager.glTranslated((offU * scale) - x, y, (offV * scale) - z);

        // Modulo to prevent texture samples becoming inaccurate at extreme offsets.
        offU = offU % texW;
        offV = offV % texH;

        // Translate the texture.
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glTranslatef(offU * PX_SIZE, offV * PX_SIZE, 0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        GLStateManager.disableCull();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        // Color multiplier.
        Vec3 color = mc.theWorld.getCloudColour(partialTicks);
        float r = (float) color.xCoord;
        float g = (float) color.yCoord;
        float b = (float) color.zCoord;

        if (mc.gameSettings.anaglyph) {
            float tempR = r * 0.3F + g * 0.59F + b * 0.11F;
            float tempG = r * 0.3F + g * 0.7F;
            float tempB = r * 0.3F + b * 0.7F;
            r = tempR;
            g = tempG;
            b = tempB;
        }

        // Apply a color multiplier through a texture upload if shaders aren't supported.
        final int newColor = 0xFF000000
            | ((int) (r * 255)) << 16
            | ((int) (g * 255)) << 8
            | (int) (b * 255);
        if (texColor != newColor) {
            if (COLOR_TEX == null) {
                COLOR_TEX = new DynamicTexture(1, 1);
            }
            COLOR_TEX.getTextureData()[0] = newColor;
            COLOR_TEX.updateDynamicTexture();
            texColor = newColor;
        }

        GLStateManager.glActiveTexture(OpenGlHelper.lightmapTexUnit);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, COLOR_TEX.getGlTextureId());
        GLStateManager.enableTexture();

        // Bind the clouds texture last so the shader's sampler2D is correct.
        GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);
        mc.renderEngine.bindTexture(texture);

        vbo.setupState();

        // Depth pass to prevent insides rendering from the outside.
        GLStateManager.glColorMask(false, false, false, false);

        vbo.draw();

        // Full render.
        if (!mc.gameSettings.anaglyph) {
            GLStateManager.glColorMask(true, true, true, true);
        } else {
            switch (EntityRenderer.anaglyphField) {
                case 0:
                    GLStateManager.glColorMask(false, true, true, true);
                    break;
                case 1:
                    GLStateManager.glColorMask(true, false, false, true);
                    break;
            }
        }

        // Wireframe for debug.
        if (WIREFRAME) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            GL11.glLineWidth(2.0F);
            GLStateManager.disableTexture();
            GLStateManager.glDepthMask(false);
            GLStateManager.disableFog();
            vbo.draw();
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GLStateManager.glDepthMask(true);
            GLStateManager.enableTexture();
            GLStateManager.enableFog();
        }

        vbo.draw();
        vbo.cleanupState(); // Unbind buffer and disable pointers.

        // Disable our coloring.
        GLStateManager.glActiveTexture(OpenGlHelper.lightmapTexUnit);
        GLStateManager.disableTexture();
        GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);

        // Reset texture matrix.
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        GLStateManager.disableBlend();
        GLStateManager.enableCull();

        GLStateManager.glPopMatrix();

        return true;
    }

    private void reloadTextures() {
        if (mc.renderEngine != null) {
            mc.renderEngine.bindTexture(texture);
            texW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            texH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        reloadTextures();
    }
}
