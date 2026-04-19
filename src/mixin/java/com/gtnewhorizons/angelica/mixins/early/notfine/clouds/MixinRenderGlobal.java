package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.GraphicsQualityOff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Unique
    private static int angelica$cloudMipmapTexId = -1;
    @Unique
    private static byte[] angelica$cellAlpha;
    @Unique
    private static int angelica$cellW;
    @Unique
    private static int angelica$cellH;
    @Unique
    private static int angelica$cellTexId = -1;
    @Unique
    private static final int ANGELICA_CELL_EMPTY_THRESHOLD = 10;

    @Unique
    private static void angelica$setupCloudTexture() {
        final int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (bound != angelica$cloudMipmapTexId) {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            GLStateManager.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            angelica$cloudMipmapTexId = bound;
        }
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        if (bound != angelica$cellTexId || angelica$cellAlpha == null) {
            final int w = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            final int h = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w > 0 && h > 0) {
                final java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                final byte[] alpha = new byte[w * h];
                for (int i = 0; i < w * h; i++) {
                    alpha[i] = buf.get(i * 4 + 3);
                }
                angelica$cellAlpha = alpha;
                angelica$cellW = w;
                angelica$cellH = h;
                angelica$cellTexId = bound;
            }
        }
    }

    @Unique
    private static boolean angelica$cellOpaque(int x, int z) {
        final byte[] a = angelica$cellAlpha;
        if (a == null) return true;
        final int w = angelica$cellW;
        final int h = angelica$cellH;
        final int ix = Math.floorMod(x, w);
        final int iz = Math.floorMod(z, h);
        return (a[ix + iz * w] & 0xFF) >= ANGELICA_CELL_EMPTY_THRESHOLD;
    }

    /**
     * @author jss2a98aj
     * @reason Adjust how cloud render mode is selected.
     */
    @Overwrite
    public void renderClouds(float partialTicks) {
        IRenderHandler renderer;
        if((renderer = theWorld.provider.getCloudRenderer()) != null) {
            renderer.render(partialTicks, theWorld, mc);
            return;
        }
        if(mc.theWorld.provider.isSurfaceWorld()) {
            GraphicsQualityOff cloudMode = (GraphicsQualityOff)Settings.MODE_CLOUDS.option.getStore();
            if(cloudMode == GraphicsQualityOff.FANCY || cloudMode == GraphicsQualityOff.DEFAULT && mc.gameSettings.fancyGraphics) {
                renderCloudsFancy(partialTicks);
            } else {
                renderCloudsFast(partialTicks);
            }
        }
    }

    /**
     * @author jss2a98aj
     * @reason Adjust fancy cloud render.
     */
    @Overwrite
    public void renderCloudsFancy(float partialTicks) {
        Tessellator tessellator = Tessellator.instance;
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        GLStateManager.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        renderEngine.bindTexture(locationCloudsPng);
        angelica$setupCloudTexture();

        Vec3 color = theWorld.getCloudColour(partialTicks);
        float red = (float)color.xCoord;
        float green = (float)color.yCoord;
        float blue = (float)color.zCoord;
        if(mc.gameSettings.anaglyph) {
            float altRed = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float altGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float altBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = altRed;
            green = altGreen;
            blue = altBlue;
        }
        double cloudTick = ((float)cloudTickCounter + partialTicks);

        float cloudScale = (int)Settings.CLOUD_SCALE.option.getStore();
        float cloudInteriorWidth = 12.0F * cloudScale;
        float cloudInteriorHeight = 4.0F * cloudScale;
        float cameraOffsetY = (float)(mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * (double)partialTicks);
        double cameraOffsetX = (mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * (double)partialTicks + cloudTick * 0.03D) / (double)cloudInteriorWidth;
        double cameraOffsetZ = (mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * (double)partialTicks) / (double)cloudInteriorWidth + 0.33D;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / 2048.0D) * 2048;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / 2048.0D) * 2048;

        int cloudElevation = (int)theWorld.provider.getCloudHeight();
        if (cloudElevation >= 96) {
            cloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }
        float cameraRelativeY = cloudElevation - cameraOffsetY + 0.33F;
        float cameraRelativeX = (float)(cameraOffsetX - (double)MathHelper.floor_double(cameraOffsetX));
        float cameraRelativeZ = (float)(cameraOffsetZ - (double)MathHelper.floor_double(cameraOffsetZ));

        float scrollSpeed = 0.00390625F;
        float cloudScrollingX = (float)MathHelper.floor_double(cameraOffsetX) * scrollSpeed;
        float cloudScrollingZ = (float)MathHelper.floor_double(cameraOffsetZ) * scrollSpeed;

        float cloudWidth = 8f;
        final int cloudTargetDistance = Math.max(mc.gameSettings.renderDistanceChunks,
            (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        int renderRadius = (int)Math.ceil(cloudTargetDistance * 64.0f / (cloudWidth * cloudInteriorWidth));
        float edgeOverlap = 0.0001f;//0.001F;
        GLStateManager.glScalef(cloudInteriorWidth, 1.0F, cloudInteriorWidth);

        final boolean emitTopFace    = cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean emitBottomFace = cameraRelativeY                       <= 0.0F;
        // Render-range radius in cells
        final int cellsPerChunk = (int)cloudWidth;
        final int radiusCells   = renderRadius * cellsPerChunk;
        final int radiusCellsSq = radiusCells * radiusCells;

        final int floorOffsetX = MathHelper.floor_double(cameraOffsetX);
        final int floorOffsetZ = MathHelper.floor_double(cameraOffsetZ);

        final boolean cameraInsideY = cameraRelativeY <= 0.0F
            && cameraRelativeY + cloudInteriorHeight >= 0.0F;
        final boolean cameraInsideCloud = cameraInsideY
            && angelica$cellOpaque(floorOffsetX, floorOffsetZ);

        if (mc.gameSettings.anaglyph) {
            if (EntityRenderer.anaglyphField == 0) {
                GLStateManager.glColorMask(false, true, true, true);
            } else {
                GLStateManager.glColorMask(true, false, false, true);
            }
        } else {
            GLStateManager.glColorMask(true, true, true, true);
        }

        for(int chunkX = -renderRadius + 1; chunkX <= renderRadius; ++chunkX) {
            for(int chunkZ = -renderRadius + 1; chunkZ <= renderRadius; ++chunkZ) {
                tessellator.startDrawingQuads();
                final float chunkOffsetX = (chunkX * cloudWidth);
                final float chunkOffsetZ = (chunkZ * cloudWidth);
                final float startX = chunkOffsetX - cameraRelativeX;
                final float startZ = chunkOffsetZ - cameraRelativeZ;
                final int baseU = chunkX * cellsPerChunk + floorOffsetX;
                final int baseV = chunkZ * cellsPerChunk + floorOffsetZ;

                for (int k = 0; k < cellsPerChunk; k++) {
                    for (int j = 0; j < cellsPerChunk; j++) {
                        final int cu = baseU + k;
                        final int cv = baseV + j;
                        if (!angelica$cellOpaque(cu, cv)) continue;

                        final int relX = chunkX * cellsPerChunk + k;
                        final int relZ = chunkZ * cellsPerChunk + j;
                        if (relX * relX + relZ * relZ > radiusCellsSq) continue;

                        // Only render the cell the player is in
                        if (cameraInsideCloud && (relX != 0 || relZ != 0)) continue;

                        final boolean isCenter = cameraInsideCloud
                            && Math.abs(relX) <= 1 && Math.abs(relZ) <= 1;
                        final float a = 0.8F;

                        final double x0 = startX + k;
                        final double x1 = startX + k + 1;
                        final double z0 = startZ + j;
                        final double z1 = startZ + j + 1;
                        final double y1 = cameraRelativeY + cloudInteriorHeight;

                        final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                        final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;
                        final float uL = (chunkOffsetX + k)     * scrollSpeed + cloudScrollingX;
                        final float uR = (chunkOffsetX + k + 1) * scrollSpeed + cloudScrollingX;
                        final float vN = (chunkOffsetZ + j)     * scrollSpeed + cloudScrollingZ;
                        final float vS = (chunkOffsetZ + j + 1) * scrollSpeed + cloudScrollingZ;

                        final double ye = y1 - edgeOverlap;
                        final double xe = x1 - edgeOverlap;
                        final double ze = z1 - edgeOverlap;

                        final boolean westEmpty  = !angelica$cellOpaque(cu - 1, cv);
                        final boolean eastEmpty  = !angelica$cellOpaque(cu + 1, cv);
                        final boolean northEmpty = !angelica$cellOpaque(cu, cv - 1);
                        final boolean southEmpty = !angelica$cellOpaque(cu, cv + 1);

                        // -Y normal
                        if (emitTopFace) {
                            tessellator.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, a);
                            tessellator.setNormal(0.0F, -1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, uL, vN);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z0, uR, vN);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z1, uR, vS);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z1, uL, vS);
                        }
                        // +Y normal
                        if (emitBottomFace) {
                            tessellator.setColorRGBA_F(red, green, blue, a);
                            tessellator.setNormal(0.0F, 1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, ye, z1, uL, vS);
                            tessellator.addVertexWithUV(x1, ye, z1, uR, vS);
                            tessellator.addVertexWithUV(x1, ye, z0, uR, vN);
                            tessellator.addVertexWithUV(x0, ye, z0, uL, vN);
                        }
                        // -X normal
                        if (relX > 0 && westEmpty) {
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, cellUF, vN);
                        }
                        // +X normal
                        if (relX < 0 && eastEmpty) {
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(xe, cameraRelativeY, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, cameraRelativeY, z1, cellUF, vS);
                        }
                        // -Z normal
                        if (relZ > 0 && northEmpty) {
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, -1.0F);
                            tessellator.addVertexWithUV(x0, y1, z0, uL, cellVF);
                            tessellator.addVertexWithUV(x1, y1, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, uL, cellVF);
                        }
                        // +Z normal
                        if (relZ < 0 && southEmpty) {
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, 1.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, ze, uL, cellVF);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x1, y1, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x0, y1, ze, uL, cellVF);
                        }

                        // Modern MC's FLAG_INSIDE_FACE....kinda. emit for 3x3 center cluster,
                        // with reversed vertex order so the rasterizer's back-face culling makes
                        // these visible only when the camera is inside the cell's cuboid.
                        if (isCenter) {
                            // Interior top
                            tessellator.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, a);
                            tessellator.setNormal(0.0F, 1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z1, uL, vS);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z1, uR, vS);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z0, uR, vN);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, uL, vN);
                            // Interior bottom
                            tessellator.setColorRGBA_F(red, green, blue, a);
                            tessellator.setNormal(0.0F, -1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, ye, z0, uL, vN);
                            tessellator.addVertexWithUV(x1, ye, z0, uR, vN);
                            tessellator.addVertexWithUV(x1, ye, z1, uR, vS);
                            tessellator.addVertexWithUV(x0, ye, z1, uL, vS);
                            // Interior west
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z1, cellUF, vS);
                            // Interior east
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(xe, cameraRelativeY, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, cameraRelativeY, z0, cellUF, vN);
                            // Interior north
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, 1.0F);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, z0, uL, cellVF);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x1, y1, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x0, y1, z0, uL, cellVF);
                            // Interior south
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, -1.0F);
                            tessellator.addVertexWithUV(x0, y1, ze, uL, cellVF);
                            tessellator.addVertexWithUV(x1, y1, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x1, cameraRelativeY, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x0, cameraRelativeY, ze, uL, cellVF);
                        }
                    }
                }
                tessellator.draw();
            }
        }

        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
    }

    public void renderCloudsFast(float partialTicks) {
        Tessellator tessellator = Tessellator.instance;
        GLStateManager.glDisable(GL11.GL_CULL_FACE);
        GLStateManager.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        renderEngine.bindTexture(locationCloudsPng);
        angelica$setupCloudTexture();

        Vec3 color = theWorld.getCloudColour(partialTicks);
        float red = (float)color.xCoord;
        float green = (float)color.yCoord;
        float blue = (float)color.zCoord;
        if(mc.gameSettings.anaglyph) {
            float altRed = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float altGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float altBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = altRed;
            green = altGreen;
            blue = altBlue;
        }
        double cloudTick = ((float)cloudTickCounter + partialTicks);
        float cameraOffsetY = (float)(mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * (double)partialTicks);
        double cameraOffsetX = mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * (double)partialTicks + cloudTick * 0.03D;
        double cameraOffsetZ = mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * (double)partialTicks;

        final int cloudSettingScale = (int)Settings.CLOUD_SCALE.option.getStore();
        final int fastScale = 8 * cloudSettingScale;
        final int fastTargetDistance = Math.max(mc.gameSettings.renderDistanceChunks,
            (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        float renderRadius = fastTargetDistance * 64.0f;
        double uvScale = (1.0 / 256.0) / fastScale;

        final double fastTextureCycleWorld = 256.0 * fastScale;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / fastTextureCycleWorld) * fastTextureCycleWorld;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / fastTextureCycleWorld) * fastTextureCycleWorld;

        float uvShiftX = (float)(cameraOffsetX * uvScale);
        float uvShiftZ = (float)(cameraOffsetZ * uvScale);

        int fastCloudElevation = (int)theWorld.provider.getCloudHeight();
        if (fastCloudElevation >= 96) {
            fastCloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }
        double cameraRelativeY = fastCloudElevation - cameraOffsetY + 0.33F;
        double neg = -renderRadius;

        double startXUv = neg * uvScale + uvShiftX;
        double startZUv = neg * uvScale + uvShiftZ;
        double movedXUv = (double) renderRadius * uvScale + uvShiftX;
        double movedZUv = (double) renderRadius * uvScale + uvShiftZ;

        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(red, green, blue, 0.8F);
        tessellator.addVertexWithUV(neg, cameraRelativeY, renderRadius, startXUv, movedZUv);
        tessellator.addVertexWithUV(renderRadius, cameraRelativeY, renderRadius, movedXUv, movedZUv);
        tessellator.addVertexWithUV(renderRadius, cameraRelativeY, neg, movedXUv, startZUv);
        tessellator.addVertexWithUV(neg, cameraRelativeY, neg, startXUv, startZUv);
        tessellator.draw();

        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
    }

    @Shadow @Final
    private static ResourceLocation locationCloudsPng;
    @Shadow @Final
    private TextureManager renderEngine;

    @Shadow private WorldClient theWorld;
    @Shadow private Minecraft mc;
    @Shadow private int cloudTickCounter;

}
