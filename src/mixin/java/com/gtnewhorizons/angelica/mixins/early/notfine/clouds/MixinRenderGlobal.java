package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import jss.notfine.core.Settings;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static final Logger ANGELICA_CLOUDS_LOGGER = LogManager.getLogger("Angelica/Clouds");

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
    // rebuild the cache every frame. Tolerate a small delta below visual
    // perception.
    @Unique private static final float ANGELICA_CLOUD_COLOR_REBUILD_DELTA = 0.005f;

    @Unique private static float angelica$planeLA, angelica$planeLB, angelica$planeLC, angelica$planeLOffset;
    @Unique private static float angelica$planeRA, angelica$planeRB, angelica$planeRC, angelica$planeROffset;
    @Unique private static float angelica$planeTA, angelica$planeTB, angelica$planeTC, angelica$planeTOffset;
    @Unique private static float angelica$planeBA, angelica$planeBB, angelica$planeBC, angelica$planeBOffset;
    @Unique private static float angelica$cachedFX = Float.NaN;
    @Unique private static float angelica$cachedFY = Float.NaN;
    @Unique private static float angelica$cachedFZ = Float.NaN;
    @Unique private static float angelica$cachedUX = Float.NaN;
    @Unique private static float angelica$cachedUY = Float.NaN;
    @Unique private static float angelica$cachedUZ = Float.NaN;
    @Unique private static float angelica$cachedBakedHalfHFovRad = Float.NaN;
    @Unique private static float angelica$cachedBakedHalfVFovRad = Float.NaN;
    @Unique private static final float ANGELICA_FRUSTUM_DRIFT_RAD = (float)(5.0 * Math.PI / 180.0);
    @Unique private static final float ANGELICA_FRUSTUM_DRIFT_COS = (float)Math.cos(ANGELICA_FRUSTUM_DRIFT_RAD);
    @Unique private static final float ANGELICA_PLANE_MAX_HALF_FOV_RAD = (float)(89.0 * Math.PI / 180.0);
    @Unique private static final Matrix4f angelica$scratchPWide = new Matrix4f();
    @Unique private static final Matrix4f angelica$scratchMVNoTrans = new Matrix4f();
    @Unique private static final Matrix4f angelica$scratchMVP = new Matrix4f();
    @Unique private static final Vector4f angelica$scratchPlane = new Vector4f();

    // -Dangelica.clouds.stats=true to get culling stats
    @Unique private static final boolean ANGELICA_CLOUDS_STATS =
        Boolean.getBoolean("angelica.clouds.stats");
    @Unique private static int angelica$statsCellsInRadius;
    @Unique private static int angelica$statsCellsFrustumCulled;
    @Unique private static int angelica$statsCellsEmitted;

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

        // Camera forward + up in world space
        final float yawDeg = mc.renderViewEntity.prevRotationYaw
            + (mc.renderViewEntity.rotationYaw - mc.renderViewEntity.prevRotationYaw) * partialTicks;
        final float pitchDeg = mc.renderViewEntity.prevRotationPitch
            + (mc.renderViewEntity.rotationPitch - mc.renderViewEntity.prevRotationPitch) * partialTicks;
        final float yawRad = yawDeg * (float)(Math.PI / 180.0);
        final float pitchRad = pitchDeg * (float)(Math.PI / 180.0);
        final float cosY = MathHelper.cos(yawRad), sinY = MathHelper.sin(yawRad);
        final float cosP = MathHelper.cos(pitchRad), sinP = MathHelper.sin(pitchRad);
        final float fwdX = -sinY * cosP;
        final float fwdY = -sinP;
        final float fwdZ =  cosY * cosP;
        final float upX  = -sinY * sinP;
        final float upY  =  cosP;
        final float upZ  =  cosY * sinP;

        final Matrix4f projMat = RenderingState.INSTANCE.getProjectionMatrix();
        final Matrix4f mvMat = RenderingState.INSTANCE.getModelViewMatrix();
        final float halfHFovRad = (float)Math.atan(1.0 / projMat.m00());
        final float halfVFovRad = (float)Math.atan(1.0 / projMat.m11());
        final float halfHFovPlusDrift = halfHFovRad + ANGELICA_FRUSTUM_DRIFT_RAD;
        final float halfVFovPlusDrift = halfVFovRad + ANGELICA_FRUSTUM_DRIFT_RAD;
        final boolean horizontalCullActive = halfHFovPlusDrift < ANGELICA_PLANE_MAX_HALF_FOV_RAD;
        final boolean verticalCullActive   = halfVFovPlusDrift < ANGELICA_PLANE_MAX_HALF_FOV_RAD;

        // If the view cone doesn't reach the cloud Y band at any distance in the render radius, skip everything.
        if (!cameraInsideY) {
            final float maxDistBlocks = renderRadius * cellsPerChunk * cloudInteriorWidth;
            final float viewAxisElevRad = -pitchRad;
            final float viewUpperEdgeRad = viewAxisElevRad + halfVFovPlusDrift;
            final float viewLowerEdgeRad = viewAxisElevRad - halfVFovPlusDrift;
            final boolean slabOutOfView;
            if (cameraRelativeY > 0f) {
                slabOutOfView = viewUpperEdgeRad < (float)Math.atan(cameraRelativeY / maxDistBlocks);
            } else {
                slabOutOfView = viewLowerEdgeRad > (float)Math.atan((cameraRelativeY + cloudInteriorHeight) / maxDistBlocks);
            }
            if (slabOutOfView) {
                if (angelica$cloudVao != null) {
                    angelica$cloudVao.delete();
                    angelica$cloudVao = null;
                }
                return;
            }
        }

        // Widen the projection (m00/m11 = 1/tan(halfFov + drift)) so the bake
        // covers rotations up to drift, and strip the translation from MV so
        // extracted planes live in camera-relative world space (the same frame
        // as our cell positions). If widened halfFov would reach the 90°
        // ceiling, clamp here to keep tan finite; the plane-zero branch below
        // disables that axis' cull for real.
        angelica$scratchPWide.set(projMat);
        angelica$scratchPWide.m00((float)(1.0 / Math.tan(horizontalCullActive
            ? halfHFovPlusDrift : ANGELICA_PLANE_MAX_HALF_FOV_RAD)));
        angelica$scratchPWide.m11((float)(1.0 / Math.tan(verticalCullActive
            ? halfVFovPlusDrift : ANGELICA_PLANE_MAX_HALF_FOV_RAD)));

        angelica$scratchMVNoTrans.set(mvMat);
        angelica$scratchMVNoTrans.m30(0f).m31(0f).m32(0f);

        angelica$scratchPWide.mul(angelica$scratchMVNoTrans, angelica$scratchMVP);

        // Cell AABB half-extents in world blocks. X/Z use the full cell width
        // (not half) to also absorb the sub-cell camera drift the emission
        // loop ignores when computing cell centers.
        final float cellHx = cloudInteriorWidth;
        final float cellHy = cloudInteriorHeight * 0.5f;
        final float cellHz = cloudInteriorWidth;

        // Extract the 4 side planes via Gribb-Hartmann. frustumPlane returns a
        // normalized (a, b, c, d) with the inward normal; inside iff a·x + b·y
        // + c·z + d ≥ 0. We fold the AABB margin into `offset` here so each
        // per-cell test is one fused dot + compare.
        if (horizontalCullActive) {
            angelica$scratchMVP.frustumPlane(Matrix4fc.PLANE_NX, angelica$scratchPlane);
            angelica$planeLA = angelica$scratchPlane.x;
            angelica$planeLB = angelica$scratchPlane.y;
            angelica$planeLC = angelica$scratchPlane.z;
            angelica$planeLOffset = angelica$scratchPlane.w
                + Math.abs(angelica$scratchPlane.x) * cellHx
                + Math.abs(angelica$scratchPlane.y) * cellHy
                + Math.abs(angelica$scratchPlane.z) * cellHz;
            angelica$scratchMVP.frustumPlane(Matrix4fc.PLANE_PX, angelica$scratchPlane);
            angelica$planeRA = angelica$scratchPlane.x;
            angelica$planeRB = angelica$scratchPlane.y;
            angelica$planeRC = angelica$scratchPlane.z;
            angelica$planeROffset = angelica$scratchPlane.w
                + Math.abs(angelica$scratchPlane.x) * cellHx
                + Math.abs(angelica$scratchPlane.y) * cellHy
                + Math.abs(angelica$scratchPlane.z) * cellHz;
        } else {
            angelica$planeLA = angelica$planeLB = angelica$planeLC = angelica$planeLOffset = 0f;
            angelica$planeRA = angelica$planeRB = angelica$planeRC = angelica$planeROffset = 0f;
        }
        if (verticalCullActive) {
            angelica$scratchMVP.frustumPlane(Matrix4fc.PLANE_NY, angelica$scratchPlane);
            angelica$planeBA = angelica$scratchPlane.x;
            angelica$planeBB = angelica$scratchPlane.y;
            angelica$planeBC = angelica$scratchPlane.z;
            angelica$planeBOffset = angelica$scratchPlane.w
                + Math.abs(angelica$scratchPlane.x) * cellHx
                + Math.abs(angelica$scratchPlane.y) * cellHy
                + Math.abs(angelica$scratchPlane.z) * cellHz;
            angelica$scratchMVP.frustumPlane(Matrix4fc.PLANE_PY, angelica$scratchPlane);
            angelica$planeTA = angelica$scratchPlane.x;
            angelica$planeTB = angelica$scratchPlane.y;
            angelica$planeTC = angelica$scratchPlane.z;
            angelica$planeTOffset = angelica$scratchPlane.w
                + Math.abs(angelica$scratchPlane.x) * cellHx
                + Math.abs(angelica$scratchPlane.y) * cellHy
                + Math.abs(angelica$scratchPlane.z) * cellHz;
        } else {
            angelica$planeTA = angelica$planeTB = angelica$planeTC = angelica$planeTOffset = 0f;
            angelica$planeBA = angelica$planeBB = angelica$planeBC = angelica$planeBOffset = 0f;
        }

        // Cache valid iff forward + up are within drift of cached and neither
        // FOV has widened past cached. Forward alone misses roll at extreme
        // pitch (yaw ≈ roll there, forward barely moves) — up catches it.
        final boolean frustumCacheValid = cameraInsideCloud
            || ((angelica$cachedFX * fwdX + angelica$cachedFY * fwdY + angelica$cachedFZ * fwdZ) >= ANGELICA_FRUSTUM_DRIFT_COS
                && (angelica$cachedUX * upX + angelica$cachedUY * upY + angelica$cachedUZ * upZ) >= ANGELICA_FRUSTUM_DRIFT_COS
                && halfHFovPlusDrift <= angelica$cachedBakedHalfHFovRad
                && halfVFovPlusDrift <= angelica$cachedBakedHalfVFovRad);

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
            && Math.abs(angelica$cachedBlue - blue) < ANGELICA_CLOUD_COLOR_REBUILD_DELTA
            && frustumCacheValid;

        if (!cacheValid) {
            if (angelica$cloudVao != null) {
                angelica$cloudVao.delete();
                angelica$cloudVao = null;
            }
            final DirectTessellator capture = TessellatorManager.startCapturingDirect();
            angelica$emitCloudGeometry(capture, renderRadius, cloudWidth, cellsPerChunk,
                radiusCellsSq, floorOffsetX, floorOffsetZ,
                cloudInteriorWidth, cloudInteriorHeight, cameraRelativeY,
                scrollSpeed, cloudScrollingX, cloudScrollingZ, edgeOverlap,
                emitTopFace, emitBottomFace, cameraInsideCloud,
                red, green, blue);
            // stopCapturingToVBO(IMMUTABLE) with zero captured vertices throws
            // GL_INVALID_VALUE and returns a format-less VAO that NPEs on bind.
            if (capture.getVertexCount() == 0) {
                TessellatorManager.stopCapturingDirect();
                angelica$cloudVao = null;
            } else {
                angelica$cloudVao = DirectTessellator.stopCapturingToVBO(VertexBufferType.IMMUTABLE);
            }

            if (ANGELICA_CLOUDS_STATS) {
                final int radiusCount = angelica$statsCellsInRadius;
                final int culledCount = angelica$statsCellsFrustumCulled;
                final int emittedCount = angelica$statsCellsEmitted;
                final float cullPct = radiusCount > 0 ? 100f * culledCount / radiusCount : 0f;
                ANGELICA_CLOUDS_LOGGER.info(
                    "Culling rebuild: opaque-in-radius={}, frustum-culled={} ({}%), emitted={}",
                    radiusCount, culledCount, String.format("%.1f", cullPct), emittedCount);
            }

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
            angelica$cachedFX = fwdX;
            angelica$cachedFY = fwdY;
            angelica$cachedFZ = fwdZ;
            angelica$cachedUX = upX;
            angelica$cachedUY = upY;
            angelica$cachedUZ = upZ;
            angelica$cachedBakedHalfHFovRad = halfHFovPlusDrift;
            angelica$cachedBakedHalfVFovRad = halfVFovPlusDrift;
        }

        if (angelica$cloudVao == null) {
            return;
        }

        angelica$cloudVao.bind();

        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(true);
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(-cameraRelativeX, cameraRelativeY, -cameraRelativeZ);
        angelica$cloudVao.draw();
        GLStateManager.glPopMatrix();

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
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(-cameraRelativeX, cameraRelativeY, -cameraRelativeZ);
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
        float cloudInteriorWidth,
        float cloudInteriorHeight,
        float cameraRelativeY,
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
        angelica$statsCellsInRadius = 0;
        angelica$statsCellsFrustumCulled = 0;
        angelica$statsCellsEmitted = 0;
        // DirectTessellator captures everything between one startDrawingQuads() and stopCapturingToVBO() into one VBO.
        for (int chunkX = -renderRadius + 1; chunkX <= renderRadius; ++chunkX) {
            for (int chunkZ = -renderRadius + 1; chunkZ <= renderRadius; ++chunkZ) {
                final float chunkOffsetX = (chunkX * cloudWidth);
                final float chunkOffsetZ = (chunkZ * cloudWidth);
                final float startX = chunkOffsetX - (float) 0.0;
                final float startZ = chunkOffsetZ - (float) 0.0;
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
                        angelica$statsCellsInRadius++;

                        // Gribb-Hartmann frustum cull against the 4 baked side
                        // planes. Disabled axes have zero plane + offset, so
                        // the test is `0 < 0` = false and the cell keeps.
                        // cameraInsideCloud skips the cull so the 3×3 interior
                        // cluster below can emit.
                        if (!cameraInsideCloud) {
                            final float cx = (relX + 0.5f) * cloudInteriorWidth;
                            final float cy = cameraRelativeY + cloudInteriorHeight * 0.5f;
                            final float cz = (relZ + 0.5f) * cloudInteriorWidth;
                            if (angelica$planeLA * cx + angelica$planeLB * cy + angelica$planeLC * cz + angelica$planeLOffset < 0f
                             || angelica$planeRA * cx + angelica$planeRB * cy + angelica$planeRC * cz + angelica$planeROffset < 0f
                             || angelica$planeTA * cx + angelica$planeTB * cy + angelica$planeTC * cz + angelica$planeTOffset < 0f
                             || angelica$planeBA * cx + angelica$planeBB * cy + angelica$planeBC * cz + angelica$planeBOffset < 0f) {
                                angelica$statsCellsFrustumCulled++;
                                continue;
                            }
                        }

                        // When inside a cloud, only process a 3×3 cluster.
                        if (cameraInsideCloud && (Math.abs(relX) > 1 || Math.abs(relZ) > 1)) continue;
                        angelica$statsCellsEmitted++;

                        final boolean isCenter = cameraInsideCloud
                            && Math.abs(relX) <= 1 && Math.abs(relZ) <= 1;

                        final double x0 = startX + k;
                        final double x1 = startX + k + 1;
                        final double z0 = startZ + j;
                        final double z1 = startZ + j + 1;
                        final double y1 = (float) 0.0 + cloudInteriorHeight;

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
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, uL, vN);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z0, uR, vN);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z1, uR, vS);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z1, uL, vS);
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
                            tessellator.addVertexWithUV(x0, (float) 0.0, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, cellUF, vN);
                        }
                        // +X normal
                        if (relX < 0 && eastEmpty) {
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(xe, (float) 0.0, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, (float) 0.0, z1, cellUF, vS);
                        }
                        // -Z normal
                        if (relZ > 0 && northEmpty) {
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, -1.0F);
                            tessellator.addVertexWithUV(x0, y1, z0, uL, cellVF);
                            tessellator.addVertexWithUV(x1, y1, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, uL, cellVF);
                        }
                        // +Z normal
                        if (relZ < 0 && southEmpty) {
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, 1.0F);
                            tessellator.addVertexWithUV(x0, (float) 0.0, ze, uL, cellVF);
                            tessellator.addVertexWithUV(x1, (float) 0.0, ze, uR, cellVF);
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
                            tessellator.addVertexWithUV(x0, (float) 0.0, z1, uL, vS);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z1, uR, vS);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z0, uR, vN);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, uL, vN);
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
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(x0, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z1, cellUF, vS);
                            // Interior east
                            tessellator.setColorRGBA_F(red * 0.9F, green * 0.9F, blue * 0.9F, a);
                            tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                            tessellator.addVertexWithUV(xe, (float) 0.0, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, y1, z1, cellUF, vS);
                            tessellator.addVertexWithUV(xe, y1, z0, cellUF, vN);
                            tessellator.addVertexWithUV(xe, (float) 0.0, z0, cellUF, vN);
                            // Interior north
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, 1.0F);
                            tessellator.addVertexWithUV(x0, (float) 0.0, z0, uL, cellVF);
                            tessellator.addVertexWithUV(x1, (float) 0.0, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x1, y1, z0, uR, cellVF);
                            tessellator.addVertexWithUV(x0, y1, z0, uL, cellVF);
                            // Interior south
                            tessellator.setColorRGBA_F(red * 0.8F, green * 0.8F, blue * 0.8F, a);
                            tessellator.setNormal(0.0F, 0.0F, -1.0F);
                            tessellator.addVertexWithUV(x0, y1, ze, uL, cellVF);
                            tessellator.addVertexWithUV(x1, y1, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x1, (float) 0.0, ze, uR, cellVF);
                            tessellator.addVertexWithUV(x0, (float) 0.0, ze, uL, cellVF);
                        }
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
