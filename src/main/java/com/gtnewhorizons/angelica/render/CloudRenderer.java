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

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.uniform.GlUniformFloat2v;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class CloudRenderer implements IResourceManagerReloadListener {
    private static final float PX_SIZE = 1 / 256F;

    private static final int TOP_SECTIONS = 12;
    private static final int HEIGHT = 4;
    private static final float INSET = 0.001F;
    private static final float ALPHA = 0.8F;

    private static final boolean WIREFRAME = false;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ResourceLocation texture = new ResourceLocation("textures/environment/clouds.png");

    private IVertexArrayObject vao;
    private int cloudMode = -1;
    private int renderDistance = -1;
    private int cloudElevation = -1;
    private int scaleMult = -1;

    private int texW;
    private int texH;

    private GlProgram<CloudUniforms> program;
    private final Matrix4f mvpScratch = new Matrix4f();

    public CloudRenderer() {
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
        final float scale = getScale();
        return (float)Math.ceil(value / scale) * scale;
    }

    private float meshExtent() {
        return ceilToScale(renderDistance * 4 * 16);
    }

    private void vertices(Tessellator tessellator) {
        final GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
        final boolean fancy = cloudGraphicsQuality == GraphicsQualityOff.FANCY || cloudGraphicsQuality == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics;

        final float scale = getScale();
        final float CULL_DIST = 2 * scale;

        final float bCol = fancy ? 0.7F : 1F;

        final float sectEnd = meshExtent();
        final float sectStart = -sectEnd;

        final float sectStep = ceilToScale(sectEnd * 2 / TOP_SECTIONS);
        final float sectPx = PX_SIZE / scale;

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

                final float u0 = sectX0 * sectPx;
                final float u1 = sectX1 * sectPx;
                final float v0 = sectZ0 * sectPx;
                final float v1 = sectZ1 * sectPx;

                // Bottom
                tessellator.setColorRGBA_F(bCol, bCol, bCol, ALPHA);
                tessellator.addVertexWithUV(sectX0, 0, sectZ0, u0, v0);
                tessellator.addVertexWithUV(sectX1, 0, sectZ0, u1, v0);
                tessellator.addVertexWithUV(sectX1, 0, sectZ1, u1, v1);
                tessellator.addVertexWithUV(sectX0, 0, sectZ1, u0, v1);

                if (fancy) {
                    final int height = HEIGHT * scaleMult;
                    // Top
                    tessellator.setColorRGBA_F(1, 1, 1, ALPHA);
                    tessellator.addVertexWithUV(sectX0, height, sectZ0, u0, v0);
                    tessellator.addVertexWithUV(sectX0, height, sectZ1, u0, v1);
                    tessellator.addVertexWithUV(sectX1, height, sectZ1, u1, v1);
                    tessellator.addVertexWithUV(sectX1, height, sectZ0, u1, v0);

                    float slice;
                    float sliceCoord0;
                    float sliceCoord1;

                    for (slice = sectX0; slice < sectX1; ) {
                        sliceCoord0 = slice * sectPx;
                        sliceCoord1 = sliceCoord0 + PX_SIZE;

                        // X sides
                        if (slice > -CULL_DIST) {
                            slice += INSET;
                            tessellator.setColorRGBA_F(0.9F, 0.9F, 0.9F, ALPHA);
                            tessellator.addVertexWithUV(slice, 0, sectZ1, sliceCoord0, v1);
                            tessellator.addVertexWithUV(slice, height, sectZ1, sliceCoord1, v1);
                            tessellator.addVertexWithUV(slice, height, sectZ0, sliceCoord1, v0);
                            tessellator.addVertexWithUV(slice, 0, sectZ0, sliceCoord0, v0);
                            slice -= INSET;
                        }

                        slice += scale;

                        if (slice <= CULL_DIST) {
                            slice -= INSET;
                            tessellator.setColorRGBA_F(0.9F, 0.9F, 0.9F, ALPHA);
                            tessellator.addVertexWithUV(slice, 0, sectZ0, sliceCoord0, v0);
                            tessellator.addVertexWithUV(slice, height, sectZ0, sliceCoord1, v0);
                            tessellator.addVertexWithUV(slice, height, sectZ1, sliceCoord1, v1);
                            tessellator.addVertexWithUV(slice, 0, sectZ1, sliceCoord0, v1);
                            slice += INSET;
                        }
                    }

                    for (slice = sectZ0; slice < sectZ1; ) {
                        sliceCoord0 = slice * sectPx;
                        sliceCoord1 = sliceCoord0 + PX_SIZE;

                        // Z sides
                        if (slice > -CULL_DIST) {
                            slice += INSET;
                            tessellator.setColorRGBA_F(0.8F, 0.8F, 0.8F, ALPHA);
                            tessellator.addVertexWithUV(sectX0, 0, slice, u0, sliceCoord0);
                            tessellator.addVertexWithUV(sectX0, height, slice, u0, sliceCoord1);
                            tessellator.addVertexWithUV(sectX1, height, slice, u1, sliceCoord1);
                            tessellator.addVertexWithUV(sectX1, 0, slice, u1, sliceCoord0);
                            slice -= INSET;
                        }

                        slice += scale;

                        if (slice <= CULL_DIST) {
                            slice -= INSET;
                            tessellator.setColorRGBA_F(0.8F, 0.8F, 0.8F, ALPHA);
                            tessellator.addVertexWithUV(sectX1, 0, slice, u1, sliceCoord0);
                            tessellator.addVertexWithUV(sectX1, height, slice, u1, sliceCoord1);
                            tessellator.addVertexWithUV(sectX0, height, slice, u0, sliceCoord1);
                            tessellator.addVertexWithUV(sectX0, 0, slice, u0, sliceCoord0);
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
        if (vao != null) {
            vao.delete();
            vao = null;
        }
        if (program != null) {
            program.delete();
            program = null;
        }
    }

    private void build() {
        final DirectTessellator tessellator = TessellatorManager.startCapturingDirect();
        this.vertices(tessellator);
        this.vao = DirectTessellator.stopCapturingToVBO(VertexBufferType.IMMUTABLE);
    }

    private int fullCoord(double coord, int scale) {   // Corrects misalignment of UV offset when on negative coords.
        return ((int) coord / scale) - (coord < 0 ? 1 : 0);
    }

    private boolean isBuilt() {
        return vao != null;
    }

    public void checkSettings() {
        final GraphicsQualityOff cloudGraphicsQuality = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
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

    private void initProgram() {
        if (program != null) return;

        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:cloud.vert", ShaderConstants.EMPTY);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:cloud.frag", ShaderConstants.EMPTY);
        try {
            program = GlProgram.builder("angelica:cloud")
                .attachShader(vert)
                .attachShader(frag)
                .link(CloudUniforms::new);
        } finally {
            vert.delete();
            frag.delete();
        }

        final CloudUniforms u = program.getInterface();
        final float start = renderDistance * 16.0f;
        final float end = meshExtent();
        final float range = end - start;

        program.bind();
        u.cloudTex.setInt(0);
        u.fogParams.set(new float[] {
            range != 0.0f ? -1.0f / range : 0.0f,
            range != 0.0f ? end / range : 1.0f,
            0.0f, 0.0f
        });
        program.unbind();
    }

    private void uploadFogUniforms(float partialTicks) {
        final CloudUniforms u = program.getInterface();
        final boolean fogEnabled = GLStateManager.getFogMode().isEnabled();
        u.setFogEnabled(fogEnabled);

        if (fogEnabled) {
            final Vec3 sky = mc.theWorld.getSkyColor(mc.renderViewEntity, partialTicks);
            u.setFogColor((float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord);
        }
    }

    private static class CloudUniforms {
        final GlUniformMatrix4f mvp;
        final GlUniformFloat3v colorMult;
        final GlUniformFloat2v texOffset;
        final GlUniformFloat2v fogOrigin;
        final GlUniformFloat4v fogParams;
        final GlUniformFloat4v fogColor;
        final GlUniformInt fogEnabled;
        final GlUniformInt cloudTex;

        private int lastFogEnabled = -1;
        private float lastFogR = Float.NaN, lastFogG = Float.NaN, lastFogB = Float.NaN;
        private float lastColorR = Float.NaN, lastColorG = Float.NaN, lastColorB = Float.NaN;
        private final float[] fogColorBuf = new float[4];

        CloudUniforms(ShaderBindingContext ctx) {
            mvp = ctx.bindUniform("u_MVPMatrix", GlUniformMatrix4f::new);
            colorMult = ctx.bindUniform("u_ColorMultiplier", GlUniformFloat3v::new);
            texOffset = ctx.bindUniform("u_TexOffset", GlUniformFloat2v::new);
            fogOrigin = ctx.bindUniform("u_FogOrigin", GlUniformFloat2v::new);
            fogParams = ctx.bindUniform("u_FogParams", GlUniformFloat4v::new);
            fogColor = ctx.bindUniform("u_FogColor", GlUniformFloat4v::new);
            fogEnabled = ctx.bindUniform("u_FogEnabled", GlUniformInt::new);
            cloudTex = ctx.bindUniform("u_CloudTexture", GlUniformInt::new);
        }

        void setFogEnabled(boolean enabled) {
            final int val = enabled ? 1 : 0;
            if (val != lastFogEnabled) {
                lastFogEnabled = val;
                fogEnabled.setInt(val);
            }
        }

        void setFogColor(float r, float g, float b) {
            if (r != lastFogR || g != lastFogG || b != lastFogB) {
                lastFogR = r; lastFogG = g; lastFogB = b;
                fogColorBuf[0] = r; fogColorBuf[1] = g; fogColorBuf[2] = b; fogColorBuf[3] = 1.0f;
                fogColor.set(fogColorBuf);
            }
        }

        void setColorMult(float r, float g, float b) {
            if (r != lastColorR || g != lastColorG || b != lastColorB) {
                lastColorR = r; lastColorG = g; lastColorB = b;
                colorMult.set(r, g, b);
            }
        }
    }

    public boolean render(int cloudTicks, float partialTicks) {
        if (!isBuilt())
            return false;

        if (program == null) {
            initProgram();
            if (program == null) return false;
        }

        final Entity entity = mc.renderViewEntity;

        final double totalOffset = cloudTicks + partialTicks;

        final double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks + totalOffset * 0.03;
        final double y = cloudElevation - (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks) + 0.33;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

        final int scale = getScale();

        if (cloudMode == 2)
            z += 0.33 * scale;

        int offU = fullCoord(x, scale);
        int offV = fullCoord(z, scale);

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslated((offU * scale) - x, y, (offV * scale) - z);

        final float fogOriginX = (float)(x - offU * scale);
        final float fogOriginZ = (float)(z - offV * scale);

        offU = offU % texW;
        offV = offV % texH;

        GLStateManager.disableCull();

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        final Vec3 color = mc.theWorld.getCloudColour(partialTicks);
        float r = (float) color.xCoord;
        float g = (float) color.yCoord;
        float b = (float) color.zCoord;

        if (mc.gameSettings.anaglyph) {
            final float tempR = r * 0.3F + g * 0.59F + b * 0.11F;
            final float tempG = r * 0.3F + g * 0.7F;
            final float tempB = r * 0.3F + b * 0.7F;
            r = tempR;
            g = tempG;
            b = tempB;
        }

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        mc.renderEngine.bindTexture(texture);
        program.bind();

        final CloudUniforms u = program.getInterface();
        final Matrix4fStack mv = GLStateManager.getModelViewMatrix();
        GLStateManager.getProjectionMatrix().mul(mv, mvpScratch);
        u.mvp.set(mvpScratch);
        u.texOffset.set(offU * PX_SIZE, offV * PX_SIZE);
        u.setColorMult(r, g, b);
        u.fogOrigin.set(fogOriginX, fogOriginZ);

        uploadFogUniforms(partialTicks);

        vao.bind();

        // Depth pass to prevent insides rendering from the outside.
        GLStateManager.glColorMask(false, false, false, false);
        vao.draw();

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

        if (WIREFRAME) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            GLStateManager.glLineWidth(2.0F);
            GLStateManager.glDepthMask(false);
            vao.draw();
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GLStateManager.glDepthMask(true);
        }

        vao.draw();
        vao.unbind(); // Unbind buffer.

        program.unbind();

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
        if (program != null) {
            program.delete();
            program = null;
        }
    }
}
