package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.PassOverride;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.vertices.NormI8;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

final class EntityShadowBatcher {

    private static final ResourceLocation SHADOW_TEXTURE = new ResourceLocation("textures/misc/shadow.png");
    private static final int PACKED_UP = NormI8.pack(0f, 1f, 0f, 0f);

    private static final int INITIAL_QUADS = 256;
    private static final int INITIAL_MVS = 8;

    private int[] records = new int[INITIAL_QUADS * ShadowQuadMath.RECORD_SIZE];
    private int[] recordMv = new int[INITIAL_QUADS];
    private int count;
    private Matrix4f[] mvs = new Matrix4f[INITIAL_MVS];
    private int mvCount;
    private final int[] packedScratch = new int[4 * ModelQuadUtil.VERTEX_SIZE];
    private final int[] ijk = new int[6];
    private final Matrix4f mvScratch = new Matrix4f();
    private final Vector3f scratch = new Vector3f();
    private int lastMvGeneration;
    private RenderLayer layer;

    private long quads;
    private long draws;

    long statQuads() { return quads; }

    long statDraws() { return draws; }

    boolean record(World world, Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks, float shadowSize) {
        if (world == null || !ModelPartBatcher.INSTANCE.entityPassActive()) return false;
        if (Iris.enabled) {
            final PipelineManager manager = Iris.getPipelineManagerNullable();
            if (manager != null) {
                final WorldRenderingPipeline pipeline = manager.getPipelineNullable();
                if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows()) return false;
            }
        }

        float size = shadowSize;
        if (entity instanceof EntityLiving living) {
            size *= living.getRenderSizeModifier();
            if (living.isChild()) {
                size *= 0.5F;
            }
        }

        final double ss = entity.getShadowSize();
        final double ex = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) partialTicks;
        final double ey = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) partialTicks + ss;
        final double ez = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) partialTicks;
        final double ox = x - ex;
        final double oy = y - ey;
        final double oz = z - ez;

        final int cells = ShadowQuadMath.box(ex, ey, ez, size, ijk);
        ensureCapacity(cells);
        final int mvIndex = currentMvIndex();

        final int packedLight = GLSMConfig.packedLastBrightness();
        final float[] brightnessTable = world.provider.lightBrightnessTable;
        final double yParam = y + ss;
        final double oyParam = oy + ss;

        for (int bx = ijk[0]; bx <= ijk[1]; ++bx) {
            for (int by = ijk[2]; by <= ijk[3]; ++by) {
                for (int bz = ijk[4]; bz <= ijk[5]; ++bz) {
                    final Block block = world.getBlock(bx, by - 1, bz);
                    if (block.getMaterial() == Material.air) continue;
                    final int light = world.getBlockLightValue(bx, by, bz);
                    if (light <= 3 || !block.renderAsNormalBlock()) continue;
                    if (ShadowQuadMath.project(records, count * ShadowQuadMath.RECORD_SIZE, x, yParam, z, bx, by, bz, shadowAlpha, size, ox, oyParam, oz, block.getBlockBoundsMinX(), block.getBlockBoundsMaxX(), block.getBlockBoundsMinY(), block.getBlockBoundsMinZ(), block.getBlockBoundsMaxZ(), brightnessTable[light], packedLight)) {
                        recordMv[count] = mvIndex;
                        count++;
                    }
                }
            }
        }

        GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDepthMask(true);
        return true;
    }

    void flush(AngelicaBufferSource source) {
        if (count == 0) {
            mvCount = 0;
            return;
        }
        final SegmentedBufferBuilder builder = source.getBuffer(layer(), 0);
        for (int i = 0; i < count; i++) {
            ShadowQuadMath.expand(packedScratch, records, i * ShadowQuadMath.RECORD_SIZE, PACKED_UP);
            builder.addPackedVertices(packedScratch, 4, mvs[recordMv[i]], scratch);
        }
        draws++;
        quads += count;
        count = 0;
        mvCount = 0;
    }

    void reset() {
        count = 0;
        mvCount = 0;
        records = new int[INITIAL_QUADS * ShadowQuadMath.RECORD_SIZE];
        recordMv = new int[INITIAL_QUADS];
        mvs = new Matrix4f[INITIAL_MVS];
        layer = null;
    }

    private int currentMvIndex() {
        final int mvGen = GLStateManager.getMvGeneration();
        if (mvCount > 0 && mvGen == lastMvGeneration) return mvCount - 1;
        lastMvGeneration = mvGen;
        mvScratch.set(GLStateManager.getModelViewMatrix());
        if (mvCount > 0 && mvs[mvCount - 1].equals(mvScratch)) return mvCount - 1;
        if (mvCount == mvs.length) mvs = Arrays.copyOf(mvs, mvs.length * 2);
        Matrix4f slot = mvs[mvCount];
        if (slot == null) {
            slot = new Matrix4f();
            mvs[mvCount] = slot;
        }
        slot.set(mvScratch);
        return mvCount++;
    }

    private void ensureCapacity(int additionalQuads) {
        final int neededQuads = count + additionalQuads;
        if (neededQuads * ShadowQuadMath.RECORD_SIZE > records.length) {
            records = Arrays.copyOf(records, Math.max(neededQuads * ShadowQuadMath.RECORD_SIZE, records.length * 2));
        }
        if (neededQuads > recordMv.length) {
            recordMv = Arrays.copyOf(recordMv, Math.max(neededQuads, recordMv.length * 2));
        }
    }

    private RenderLayer layer() {
        RenderLayer l = layer;
        if (l == null) {
            l = RenderLayer.tesr(SHADOW_TEXTURE, EntityMaterials.SHADOW, PassOverride.NONE, 0.0F, 0.0F);
            layer = l;
        }
        return l;
    }
}
