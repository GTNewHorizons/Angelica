package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
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
    private static long[] angelica$cellOpaqueBits;
    @Unique
    private static int angelica$cellW;
    @Unique
    private static int angelica$cellH;
    @Unique
    private static int angelica$cellWMask;
    @Unique
    private static int angelica$cellHMask;
    @Unique
    private static boolean angelica$cellWHPow2;
    @Unique
    private static int angelica$cellTexId = -1;
    @Unique
    private static final int ANGELICA_CELL_EMPTY_THRESHOLD = 10;

    // Caching the cloud mesh. Stored cell-local (no camera offset baked into
    // vertices) so the per-frame camera position is applied as a translation
    // around the draw and camera motion within a cell doesn't invalidate the
    // cache. Immutable VBO so the depth prepass and color pass both reuse
    // GPU-resident geometry; Angelica picks up glDrawArrays in the cloud phase
    // and binds gbuffers_clouds on its own.
    @Unique private static IVertexArrayObject angelica$cloudVao;
    @Unique private static int angelica$cachedCellTexId = Integer.MIN_VALUE;
    @Unique private static int angelica$cachedFloorOffsetX = Integer.MIN_VALUE;
    @Unique private static int angelica$cachedFloorOffsetZ = Integer.MIN_VALUE;
    @Unique private static boolean angelica$cachedEmitTopFace;
    @Unique private static boolean angelica$cachedEmitBottomFace;
    @Unique private static boolean angelica$cachedCameraInsideCloud;
    @Unique private static int angelica$cachedRenderRadius = -1;
    @Unique private static float angelica$cachedCloudInteriorHeight = -1f;
    @Unique private static float angelica$cachedCloudScrollingX = Float.NaN;
    @Unique private static float angelica$cachedCloudScrollingZ = Float.NaN;
    @Unique private static float angelica$cachedRed = Float.NaN;
    @Unique private static float angelica$cachedGreen = Float.NaN;
    @Unique private static float angelica$cachedBlue = Float.NaN;
    // Cloud color is baked per-vertex, so any sky/sun color drift would
    // rebuild the cache every frame.
    //TODO: Make this better
    @Unique private static final float ANGELICA_CLOUD_COLOR_REBUILD_DELTA = 0.005f;

    // Per-chunk opacity. cellsPerChunk=8 hardcoded by cloudWidth=8;
    // grid is 10×10 (chunk + 1-cell border on each side). Each int holds one
    // row of 10 bits (bit n = opacity of cell at column n). Built once per
    // chunk; consumed by per-cell neighbor lookups + greedy meshing instead of
    // hitting the global bitset 4× per cell.
    @Unique private static final int[] angelica$chunkOpaqueRows = new int[10];

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

        if (bound != angelica$cellTexId || angelica$cellOpaqueBits == null) {
            final int w = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            final int h = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w > 0 && h > 0) {
                final java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
                final long[] bits = new long[(w * h + 63) >>> 6];
                for (int i = 0; i < w * h; i++) {
                    if ((buf.get(i * 4 + 3) & 0xFF) >= ANGELICA_CELL_EMPTY_THRESHOLD) {
                        bits[i >>> 6] |= 1L << (i & 63);
                    }
                }
                angelica$cellOpaqueBits = bits;
                angelica$cellW = w;
                angelica$cellH = h;
                angelica$cellWMask = w - 1;
                angelica$cellHMask = h - 1;
                angelica$cellWHPow2 = (w & (w - 1)) == 0 && (h & (h - 1)) == 0;
                angelica$cellTexId = bound;
            }
        }
    }

    @Unique
    private static boolean angelica$cellOpaque(int x, int z) {
        final long[] bits = angelica$cellOpaqueBits;
        if (bits == null) return true;
        final int w = angelica$cellW;
        final int xw, zh;
        if (angelica$cellWHPow2) {
            xw = x & angelica$cellWMask;
            zh = z & angelica$cellHMask;
        } else {
            xw = Math.floorMod(x, w);
            zh = Math.floorMod(z, angelica$cellH);
        }
        final int idx = xw + zh * w;
        return ((bits[idx >>> 6] >>> (idx & 63)) & 1L) != 0L;
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
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
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

        final boolean cacheValid = angelica$cloudVao != null
            && angelica$cachedCellTexId == angelica$cellTexId
            && angelica$cachedFloorOffsetX == floorOffsetX
            && angelica$cachedFloorOffsetZ == floorOffsetZ
            && angelica$cachedEmitTopFace == emitTopFace
            && angelica$cachedEmitBottomFace == emitBottomFace
            && angelica$cachedCameraInsideCloud == cameraInsideCloud
            && angelica$cachedRenderRadius == renderRadius
            && angelica$cachedCloudInteriorHeight == cloudInteriorHeight
            && angelica$cachedCloudScrollingX == cloudScrollingX
            && angelica$cachedCloudScrollingZ == cloudScrollingZ
            && Math.abs(angelica$cachedRed - red) < ANGELICA_CLOUD_COLOR_REBUILD_DELTA
            && Math.abs(angelica$cachedGreen - green) < ANGELICA_CLOUD_COLOR_REBUILD_DELTA
            && Math.abs(angelica$cachedBlue - blue) < ANGELICA_CLOUD_COLOR_REBUILD_DELTA;

        if (!cacheValid) {
            if (angelica$cloudVao != null) {
                angelica$cloudVao.delete();
                angelica$cloudVao = null;
            }
            final DirectTessellator capture = TessellatorManager.startCapturingDirect();
            // do this or suffer issues
            capture.startDrawingQuads();
            angelica$emitCloudGeometry(capture, renderRadius, cloudWidth, cellsPerChunk,
                radiusCellsSq, floorOffsetX, floorOffsetZ,
                cloudInteriorHeight,
                scrollSpeed, cloudScrollingX, cloudScrollingZ, edgeOverlap,
                emitTopFace, emitBottomFace, cameraInsideCloud,
                red, green, blue);
            final int vertexCount = capture.getVertexCount();
            if (vertexCount == 0) {
                angelica$cloudVao = null;
            } else {
                angelica$cloudVao = VertexBufferType.IMMUTABLE.allocate(
                    capture.getVertexFormat(), GL11.GL_QUADS, capture.getWriteBuffer(), vertexCount);
            }
            TessellatorManager.stopCapturingDirect();

            angelica$cachedCellTexId = angelica$cellTexId;
            angelica$cachedFloorOffsetX = floorOffsetX;
            angelica$cachedFloorOffsetZ = floorOffsetZ;
            angelica$cachedEmitTopFace = emitTopFace;
            angelica$cachedEmitBottomFace = emitBottomFace;
            angelica$cachedCameraInsideCloud = cameraInsideCloud;
            angelica$cachedRenderRadius = renderRadius;
            angelica$cachedCloudInteriorHeight = cloudInteriorHeight;
            angelica$cachedCloudScrollingX = cloudScrollingX;
            angelica$cachedCloudScrollingZ = cloudScrollingZ;
            angelica$cachedRed = red;
            angelica$cachedGreen = green;
            angelica$cachedBlue = blue;
        }

        if (angelica$cloudVao == null) {
            return;
        }

        angelica$cloudVao.bind();

        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(-cameraRelativeX, cameraRelativeY, -cameraRelativeZ);

        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(true);
        angelica$cloudVao.draw();

        if (mc.gameSettings.anaglyph) {
            if (EntityRenderer.anaglyphField == 0) {
                GLStateManager.glColorMask(false, true, true, true);
            } else {
                GLStateManager.glColorMask(true, false, false, true);
            }
        } else {
            GLStateManager.glColorMask(true, true, true, true);
        }
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glDepthMask(false);
        angelica$cloudVao.draw();
        GLStateManager.glPopMatrix();

        angelica$cloudVao.unbind();

        GLStateManager.glDepthMask(true);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
    }

    @Unique
    private static void angelica$emitCloudGeometry(
        Tessellator tessellator,
        int renderRadius,
        float cloudWidth,
        int cellsPerChunk,
        int radiusCellsSq,
        int floorOffsetX,
        int floorOffsetZ,
        float cloudInteriorHeight,
        float scrollSpeed,
        float cloudScrollingX,
        float cloudScrollingZ,
        float edgeOverlap,
        boolean emitTopFace,
        boolean emitBottomFace,
        boolean cameraInsideCloud,
        float red,
        float green,
        float blue
    ) {
        final float a = 0.8F;
        final double y1 = cloudInteriorHeight;
        final double ye = y1 - edgeOverlap;
        final int[] opaqueRows = angelica$chunkOpaqueRows;
        final int chunkLo = cameraInsideCloud ? -1 : -renderRadius;
        final int chunkHi = cameraInsideCloud ?  0 :  renderRadius;
        for (int chunkX = chunkLo; chunkX <= chunkHi; ++chunkX) {
            for (int chunkZ = chunkLo; chunkZ <= chunkHi; ++chunkZ) {
                final float chunkOffsetX = (chunkX * cloudWidth);
                final float chunkOffsetZ = (chunkZ * cloudWidth);
                final int baseU = chunkX * cellsPerChunk + floorOffsetX;
                final int baseV = chunkZ * cellsPerChunk + floorOffsetZ;

                // Build local opacity grid (10×10 = chunk + 1-cell border).
                // opaqueRows[r] holds 10 bits where bit c = cell at chunk-local
                // (c-1, r-1) is opaque. Per-cell self/neighbor checks below
                // shift+AND from this instead of calling the global bitset.
                for (int row = 0; row < 10; row++) {
                    final int cv = baseV + row - 1;
                    int rowBits = 0;
                    for (int col = 0; col < 10; col++) {
                        if (angelica$cellOpaque(baseU + col - 1, cv)) {
                            rowBits |= 1 << col;
                        }
                    }
                    opaqueRows[row] = rowBits;
                }

                // Mask of cells in this chunk able to emit (opaque + within
                // radius circle + within 3×3 cluster when cameraInsideCloud).
                // Bit (j*8 + k) is set if cell (k, j) emits. Used for both the
                // greedy top/bottom mesh and the per-cell side/interior pass.
                long emittableBits = 0L;
                for (int j = 0; j < cellsPerChunk; j++) {
                    int rowOpaque = (opaqueRows[j + 1] >>> 1) & 0xFF;
                    while (rowOpaque != 0) {
                        final int k = Integer.numberOfTrailingZeros(rowOpaque);
                        rowOpaque &= rowOpaque - 1;
                        final int relX = chunkX * cellsPerChunk + k;
                        final int relZ = chunkZ * cellsPerChunk + j;
                        if (relX * relX + relZ * relZ > radiusCellsSq) continue;
                        if (cameraInsideCloud && (Math.abs(relX) > 1 || Math.abs(relZ) > 1)) continue;
                        emittableBits |= 1L << (j * cellsPerChunk + k);
                    }
                }

                if (emittableBits == 0L) continue;

                // Greedy mesh top + bottom faces.
                if (emitTopFace || emitBottomFace) {
                    long meshTodo = emittableBits;
                    while (meshTodo != 0L) {
                        final int startBit = Long.numberOfTrailingZeros(meshTodo);
                        final int k0 = startBit & 7;
                        final int j0 = startBit >>> 3;

                        // Extend +k while contiguous run of set bits in row.
                        int w = 1;
                        while (k0 + w < cellsPerChunk
                            && ((meshTodo >>> (j0 * cellsPerChunk + k0 + w)) & 1L) != 0L) {
                            w++;
                        }

                        // Extend +j while every cell in [k0, k0+w) of next row is set.
                        final long widthMask = ((1L << w) - 1L);
                        int h = 1;
                        while (j0 + h < cellsPerChunk) {
                            final long rowMask = widthMask << ((j0 + h) * cellsPerChunk + k0);
                            if ((meshTodo & rowMask) != rowMask) break;
                            h++;
                        }

                        long mergedMask = 0L;
                        for (int dh = 0; dh < h; dh++) {
                            mergedMask |= widthMask << ((j0 + dh) * cellsPerChunk + k0);
                        }
                        meshTodo &= ~mergedMask;

                        final double x0 = chunkOffsetX + k0;
                        final double x1 = chunkOffsetX + k0 + w;
                        final double z0 = chunkOffsetZ + j0;
                        final double z1 = chunkOffsetZ + j0 + h;
                        final float uL = (chunkOffsetX + k0)     * scrollSpeed + cloudScrollingX;
                        final float uR = (chunkOffsetX + k0 + w) * scrollSpeed + cloudScrollingX;
                        final float vN = (chunkOffsetZ + j0)     * scrollSpeed + cloudScrollingZ;
                        final float vS = (chunkOffsetZ + j0 + h) * scrollSpeed + cloudScrollingZ;

                        if (emitTopFace) {
                            tessellator.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, a);
                            tessellator.setNormal(0.0F, -1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, 0.0, z0, uL, vN);
                            tessellator.addVertexWithUV(x1, 0.0, z0, uR, vN);
                            tessellator.addVertexWithUV(x1, 0.0, z1, uR, vS);
                            tessellator.addVertexWithUV(x0, 0.0, z1, uL, vS);
                        }
                        if (emitBottomFace) {
                            tessellator.setColorRGBA_F(red, green, blue, a);
                            tessellator.setNormal(0.0F, 1.0F, 0.0F);
                            tessellator.addVertexWithUV(x0, ye, z1, uL, vS);
                            tessellator.addVertexWithUV(x1, ye, z1, uR, vS);
                            tessellator.addVertexWithUV(x1, ye, z0, uR, vN);
                            tessellator.addVertexWithUV(x0, ye, z0, uL, vN);
                        }
                    }
                }

                // If every cell in chunk + border is opaque, no cell
                // has an empty neighbor, so no side faces emit. Skip mask
                // build + 4 greedy mesh loops.
                int rowsAnd = 0x3FF;
                for (int r = 0; r < 10 && rowsAnd == 0x3FF; r++) rowsAnd &= opaqueRows[r];
                final boolean hasBoundary = rowsAnd != 0x3FF;

                // Build per-direction side face masks. Each cell can emit at
                // most one X-axis face (-X if relX>0, +X if relX<0) and one
                // Z-axis face. Greedy meshes below run along the perpendicular
                // axis (X-faces along Z; Z-faces along X) since the face's
                // own axis is fixed at the cell boundary.
                long westFace = 0L, eastFace = 0L, northFace = 0L, southFace = 0L;
                if (hasBoundary) {
                    long bits = emittableBits;
                    while (bits != 0L) {
                        final int bit = Long.numberOfTrailingZeros(bits);
                        bits &= bits - 1;
                        final int k = bit & 7;
                        final int j = bit >>> 3;
                        final int relX = chunkX * cellsPerChunk + k;
                        final int relZ = chunkZ * cellsPerChunk + j;
                        if (relX > 0 && (opaqueRows[j + 1] >>> k & 1) == 0)
                            westFace  |= 1L << bit;
                        if (relX < 0 && (opaqueRows[j + 1] >>> (k + 2) & 1) == 0)
                            eastFace  |= 1L << bit;
                        if (relZ > 0 && (opaqueRows[j]     >>> (k + 1) & 1) == 0)
                            northFace |= 1L << bit;
                        if (relZ < 0 && (opaqueRows[j + 2] >>> (k + 1) & 1) == 0)
                            southFace |= 1L << bit;
                    }
                }

                // Greedy mesh -X face
                while (westFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(westFace);
                    final int k = bit & 7;
                    final int j0 = bit >>> 3;
                    int h = 1;
                    while (j0 + h < cellsPerChunk
                        && ((westFace >>> ((j0 + h) * cellsPerChunk + k)) & 1L) != 0L) {
                        h++;
                    }
                    long mask = 0L;
                    for (int dh = 0; dh < h; dh++) mask |= 1L << ((j0 + dh) * cellsPerChunk + k);
                    westFace &= ~mask;

                    final double x0 = chunkOffsetX + k;
                    final double z0 = chunkOffsetZ + j0;
                    final double z1 = chunkOffsetZ + j0 + h;
                    final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                    final float vN = (chunkOffsetZ + j0)     * scrollSpeed + cloudScrollingZ;
                    final float vS = (chunkOffsetZ + j0 + h) * scrollSpeed + cloudScrollingZ;

                    tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                    tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                    tessellator.addVertexWithUV(x0, 0.0, z1, cellUF, vS);
                    tessellator.addVertexWithUV(x0, y1,  z1, cellUF, vS);
                    tessellator.addVertexWithUV(x0, y1,  z0, cellUF, vN);
                    tessellator.addVertexWithUV(x0, 0.0, z0, cellUF, vN);
                }

                // Greedy mesh +X face
                while (eastFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(eastFace);
                    final int k = bit & 7;
                    final int j0 = bit >>> 3;
                    int h = 1;
                    while (j0 + h < cellsPerChunk
                        && ((eastFace >>> ((j0 + h) * cellsPerChunk + k)) & 1L) != 0L) {
                        h++;
                    }
                    long mask = 0L;
                    for (int dh = 0; dh < h; dh++) mask |= 1L << ((j0 + dh) * cellsPerChunk + k);
                    eastFace &= ~mask;

                    final double xe = chunkOffsetX + k + 1 - edgeOverlap;
                    final double z0 = chunkOffsetZ + j0;
                    final double z1 = chunkOffsetZ + j0 + h;
                    final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                    final float vN = (chunkOffsetZ + j0)     * scrollSpeed + cloudScrollingZ;
                    final float vS = (chunkOffsetZ + j0 + h) * scrollSpeed + cloudScrollingZ;

                    tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                    tessellator.setNormal(1.0F, 0.0F, 0.0F);
                    tessellator.addVertexWithUV(xe, 0.0, z0, cellUF, vN);
                    tessellator.addVertexWithUV(xe, y1,  z0, cellUF, vN);
                    tessellator.addVertexWithUV(xe, y1,  z1, cellUF, vS);
                    tessellator.addVertexWithUV(xe, 0.0, z1, cellUF, vS);
                }

                // Greedy mesh -Z face
                while (northFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(northFace);
                    final int k0 = bit & 7;
                    final int j = bit >>> 3;
                    int w = 1;
                    while (k0 + w < cellsPerChunk
                        && ((northFace >>> (j * cellsPerChunk + k0 + w)) & 1L) != 0L) {
                        w++;
                    }
                    northFace &= ~(((1L << w) - 1L) << (j * cellsPerChunk + k0));

                    final double x0 = chunkOffsetX + k0;
                    final double x1 = chunkOffsetX + k0 + w;
                    final double z0 = chunkOffsetZ + j;
                    final float uL = (chunkOffsetX + k0)     * scrollSpeed + cloudScrollingX;
                    final float uR = (chunkOffsetX + k0 + w) * scrollSpeed + cloudScrollingX;
                    final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;

                    tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                    tessellator.setNormal(0.0F, 0.0F, -1.0F);
                    tessellator.addVertexWithUV(x0, y1,  z0, uL, cellVF);
                    tessellator.addVertexWithUV(x1, y1,  z0, uR, cellVF);
                    tessellator.addVertexWithUV(x1, 0.0, z0, uR, cellVF);
                    tessellator.addVertexWithUV(x0, 0.0, z0, uL, cellVF);
                }

                // Greedy mesh +Z face
                while (southFace != 0L) {
                    final int bit = Long.numberOfTrailingZeros(southFace);
                    final int k0 = bit & 7;
                    final int j = bit >>> 3;
                    int w = 1;
                    while (k0 + w < cellsPerChunk
                        && ((southFace >>> (j * cellsPerChunk + k0 + w)) & 1L) != 0L) {
                        w++;
                    }
                    southFace &= ~(((1L << w) - 1L) << (j * cellsPerChunk + k0));

                    final double x0 = chunkOffsetX + k0;
                    final double x1 = chunkOffsetX + k0 + w;
                    final double ze = chunkOffsetZ + j + 1 - edgeOverlap;
                    final float uL = (chunkOffsetX + k0)     * scrollSpeed + cloudScrollingX;
                    final float uR = (chunkOffsetX + k0 + w) * scrollSpeed + cloudScrollingX;
                    final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;

                    tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                    tessellator.setNormal(0.0F, 0.0F, 1.0F);
                    tessellator.addVertexWithUV(x0, 0.0, ze, uL, cellVF);
                    tessellator.addVertexWithUV(x1, 0.0, ze, uR, cellVF);
                    tessellator.addVertexWithUV(x1, y1,  ze, uR, cellVF);
                    tessellator.addVertexWithUV(x0, y1,  ze, uL, cellVF);
                }

                // Modern MC's FLAG_INSIDE_FACE....kinda. emit for 3x3 center cluster,
                // with reversed vertex order so the rasterizer's back-face culling makes
                // these visible only when the camera is inside the cell's cuboid.
                if (cameraInsideCloud) {
                    long bits = emittableBits;
                    while (bits != 0L) {
                        final int bit = Long.numberOfTrailingZeros(bits);
                        bits &= bits - 1;
                        final int k = bit & 7;
                        final int j = bit >>> 3;
                        final double x0 = chunkOffsetX + k;
                        final double x1 = chunkOffsetX + k + 1;
                        final double z0 = chunkOffsetZ + j;
                        final double z1 = chunkOffsetZ + j + 1;
                        final double xe = x1 - edgeOverlap;
                        final double ze = z1 - edgeOverlap;
                        final float cellUF = (chunkOffsetX + k + 0.5F) * scrollSpeed + cloudScrollingX;
                        final float cellVF = (chunkOffsetZ + j + 0.5F) * scrollSpeed + cloudScrollingZ;
                        final float uL = (chunkOffsetX + k)     * scrollSpeed + cloudScrollingX;
                        final float uR = (chunkOffsetX + k + 1) * scrollSpeed + cloudScrollingX;
                        final float vN = (chunkOffsetZ + j)     * scrollSpeed + cloudScrollingZ;
                        final float vS = (chunkOffsetZ + j + 1) * scrollSpeed + cloudScrollingZ;
                        // Interior top
                        tessellator.setColorRGBA_F(red * 0.7F, green * 0.7F, blue * 0.7F, a);
                        tessellator.setNormal(0.0F, 1.0F, 0.0F);
                        tessellator.addVertexWithUV(x0, 0.0, z1, uL, vS);
                        tessellator.addVertexWithUV(x1, 0.0, z1, uR, vS);
                        tessellator.addVertexWithUV(x1, 0.0, z0, uR, vN);
                        tessellator.addVertexWithUV(x0, 0.0, z0, uL, vN);
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
                        tessellator.addVertexWithUV(x0, 0.0, z0, cellUF, vN);
                        tessellator.addVertexWithUV(x0, y1,  z0, cellUF, vN);
                        tessellator.addVertexWithUV(x0, y1,  z1, cellUF, vS);
                        tessellator.addVertexWithUV(x0, 0.0, z1, cellUF, vS);
                        // Interior east
                        tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                        tessellator.addVertexWithUV(xe, 0.0, z1, cellUF, vS);
                        tessellator.addVertexWithUV(xe, y1,  z1, cellUF, vS);
                        tessellator.addVertexWithUV(xe, y1,  z0, cellUF, vN);
                        tessellator.addVertexWithUV(xe, 0.0, z0, cellUF, vN);
                        // Interior north
                        tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                        tessellator.setNormal(0.0F, 0.0F, 1.0F);
                        tessellator.addVertexWithUV(x0, 0.0, z0, uL, cellVF);
                        tessellator.addVertexWithUV(x1, 0.0, z0, uR, cellVF);
                        tessellator.addVertexWithUV(x1, y1,  z0, uR, cellVF);
                        tessellator.addVertexWithUV(x0, y1,  z0, uL, cellVF);
                        // Interior south
                        tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                        tessellator.setNormal(0.0F, 0.0F, -1.0F);
                        tessellator.addVertexWithUV(x0, y1,  ze, uL, cellVF);
                        tessellator.addVertexWithUV(x1, y1,  ze, uR, cellVF);
                        tessellator.addVertexWithUV(x1, 0.0, ze, uR, cellVF);
                        tessellator.addVertexWithUV(x0, 0.0, ze, uL, cellVF);
                    }
                }
            }
        }
    }

    public void renderCloudsFast(float partialTicks) {
        final float cameraOffsetY = (float)(mc.renderViewEntity.lastTickPosY + (mc.renderViewEntity.posY - mc.renderViewEntity.lastTickPosY) * (double)partialTicks);
        int fastCloudElevation = (int)theWorld.provider.getCloudHeight();
        if (fastCloudElevation >= 96) {
            fastCloudElevation = (int)Settings.CLOUD_HEIGHT.option.getStore();
        }
        final double cameraRelativeY = fastCloudElevation - cameraOffsetY + 0.33F;
        final int fastTargetDistance = Math.max(mc.gameSettings.renderDistanceChunks,
            (int)Settings.RENDER_DISTANCE_CLOUDS.option.getStore());
        final float renderRadius = fastTargetDistance * 64.0f;

        final float fastPitchRad = (mc.renderViewEntity.prevRotationPitch
            + (mc.renderViewEntity.rotationPitch - mc.renderViewEntity.prevRotationPitch) * partialTicks)
            * (float)(Math.PI / 180.0);
        final float fastHalfVFovRad = RenderingState.INSTANCE.getFov() * 0.5f * (float)(Math.PI / 180.0);
        final float fastPlaneElev = (float)Math.atan(cameraRelativeY / renderRadius);
        if ((cameraRelativeY > 0.0 && -fastPitchRad + fastHalfVFovRad < fastPlaneElev)
         || (cameraRelativeY < 0.0 && -fastPitchRad - fastHalfVFovRad > fastPlaneElev)) {
            return;
        }

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
        double cameraOffsetX = mc.renderViewEntity.prevPosX + (mc.renderViewEntity.posX - mc.renderViewEntity.prevPosX) * (double)partialTicks + cloudTick * 0.03D;
        double cameraOffsetZ = mc.renderViewEntity.prevPosZ + (mc.renderViewEntity.posZ - mc.renderViewEntity.prevPosZ) * (double)partialTicks;

        final int cloudSettingScale = (int)Settings.CLOUD_SCALE.option.getStore();
        final int fastScale = 8 * cloudSettingScale;
        double uvScale = (1.0 / 256.0) / fastScale;

        final double fastTextureCycleWorld = 256.0 * fastScale;
        cameraOffsetX -= MathHelper.floor_double(cameraOffsetX / fastTextureCycleWorld) * fastTextureCycleWorld;
        cameraOffsetZ -= MathHelper.floor_double(cameraOffsetZ / fastTextureCycleWorld) * fastTextureCycleWorld;

        float uvShiftX = (float)(cameraOffsetX * uvScale);
        float uvShiftZ = (float)(cameraOffsetZ * uvScale);

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
