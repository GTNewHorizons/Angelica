package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL13;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.blocks.BlockJar;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.tiles.TileJarFillable;

import java.util.HashMap;
import java.util.Map;

public final class Tc4JarMeshes {

    private static final double MIN_X = 0.25, MIN_Y = 0.0625, MIN_Z = 0.25, MAX_X = 0.75, MAX_Z = 0.75;
    private static final double OFF_X = -0.5, OFF_Y = 0.0, OFF_Z = -0.5;

    private record LiquidKey(int color, int level64) {}

    private static final class Holder {
        final Object cacheKey = new Object();
        final TesrMaterial material;

        Holder(int color) {
            this.material = TesrMaterial.builder()
                .color(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f)
                .noCull().unlit().build();
        }
    }

    private static final Map<LiquidKey, Holder> HOLDERS = new HashMap<>();
    private static final RenderBlocks FALLBACK_RENDER_BLOCKS = new RenderBlocks();

    private static final ResourceLocation LABEL_TEXTURE = new ResourceLocation("thaumcraft", "textures/models/label.png");
    private static final TesrMaterial LABEL_MATERIAL = TesrMaterial.builder().color(1f, 1f, 1f, 1f).translucent().noCull().build();
    private static final TesrMaterial TAG_MATERIAL = TesrMaterial.builder().color(0.1f, 0.1f, 0.1f, 0.8f).translucent().noCull().unlit().build();
    private static final Object LABEL_KEY = new Object();
    private static final Map<Aspect, Object> TAG_KEYS = new HashMap<>();

    private static final TesrMeshBuilder LABEL_BUILDER = sink -> {
        final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, LABEL_TEXTURE, LABEL_MATERIAL);
        t.addVertexWithUV(-0.25, 0.25, 0.0, 0.0, 1.0);
        t.addVertexWithUV(0.25, 0.25, 0.0, 1.0, 1.0);
        t.addVertexWithUV(0.25, -0.25, 0.0, 1.0, 0.0);
        t.addVertexWithUV(-0.25, -0.25, 0.0, 0.0, 0.0);
    };

    private static final TagBuilder TAG_BUILDER = new TagBuilder();

    private static final class TagBuilder implements TesrMeshBuilder {
        private Aspect aspect;

        @Override
        public void angelica$build(TesrMeshSink sink) {
            final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, aspect.getImage(), TAG_MATERIAL);
            t.addVertexWithUV(-8.0, 8.0, 0.0, 0.0, 1.0);
            t.addVertexWithUV(8.0, 8.0, 0.0, 1.0, 1.0);
            t.addVertexWithUV(8.0, -8.0, 0.0, 1.0, 0.0);
            t.addVertexWithUV(-8.0, -8.0, 0.0, 0.0, 0.0);
        }
    }

    public static void renderLabelBacking() {
        AngelicaTesrMeshCache.INSTANCE.renderCached(LABEL_KEY, LABEL_BUILDER);
    }

    public static void renderLabelTag(Aspect aspect) {
        final Object key = TAG_KEYS.computeIfAbsent(aspect, k -> new Object());
        TAG_BUILDER.aspect = aspect;
        AngelicaTesrMeshCache.INSTANCE.renderCached(key, TAG_BUILDER);
    }

    private Tc4JarMeshes() {}

    public static RenderBlocks fallbackRenderBlocks() {
        return FALLBACK_RENDER_BLOCKS;
    }

    public static boolean renderLiquid(TileJarFillable te) {
        if (TileEntityRendererDispatcher.instance.field_147553_e == null) {
            return true;
        }
        final BlockJar jar = (BlockJar) ConfigBlocks.blockJar;
        final IIcon icon = jar.iconLiquid;
        if (icon == null || te.maxAmount <= 0) {
            return false;
        }
        final int level64 = Math.round(te.amount * 64f / te.maxAmount);
        if (level64 <= 0) {
            return true;
        }
        final int color = te.aspect != null ? te.aspect.getColor() : 0xFFFFFF;

        int bright = 200;
        if (te.getWorldObj() != null) {
            bright = Math.max(200, ConfigBlocks.blockJar.getMixedBrightnessForBlock(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord));
        }
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, bright % 65536, (float) (bright / 65536));

        final Holder holder = HOLDERS.computeIfAbsent(new LiquidKey(color, level64), k -> new Holder(k.color()));
        GLStateManager.glPushMatrix();
        GLStateManager.glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
        BUILDER.material = holder.material;
        BUILDER.level64 = level64;
        BUILDER.icon = icon;
        AngelicaTesrMeshCache.INSTANCE.renderCached(holder.cacheKey, BUILDER);
        GLStateManager.glPopMatrix();
        return true;
    }

    private static final Builder BUILDER = new Builder();

    private static final class Builder implements TesrMeshBuilder {
        private TesrMaterial material;
        private int level64;
        private IIcon icon;

        @Override
        public void angelica$build(TesrMeshSink sink) {
            build(sink, material, level64, icon);
        }
    }

    private static void build(TesrMeshSink sink, TesrMaterial material, int level64, IIcon icon) {
        final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TextureMap.locationBlocksTexture, material);
        final double maxY = MIN_Y + level64 / 64f * 0.625f;

        final double x1 = OFF_X + MIN_X, x2 = OFF_X + MAX_X;
        final double y1 = OFF_Y + MIN_Y, y2 = OFF_Y + maxY;
        final double z1 = OFF_Z + MIN_Z, z2 = OFF_Z + MAX_Z;

        // Horizontal faces
        double u1 = icon.getInterpolatedU(MIN_X * 16.0);
        double u2 = icon.getInterpolatedU(MAX_X * 16.0);
        double v1 = icon.getInterpolatedV(MIN_Z * 16.0);
        double v2 = icon.getInterpolatedV(MAX_Z * 16.0);
        // YNeg
        t.addVertexWithUV(x1, y1, z2, u1, v2);
        t.addVertexWithUV(x1, y1, z1, u1, v1);
        t.addVertexWithUV(x2, y1, z1, u2, v1);
        t.addVertexWithUV(x2, y1, z2, u2, v2);
        // YPos
        t.addVertexWithUV(x2, y2, z2, u2, v2);
        t.addVertexWithUV(x2, y2, z1, u2, v1);
        t.addVertexWithUV(x1, y2, z1, u1, v1);
        t.addVertexWithUV(x1, y2, z2, u1, v2);

        // Z faces
        v1 = icon.getInterpolatedV(16.0 - maxY * 16.0);
        v2 = icon.getInterpolatedV(16.0 - MIN_Y * 16.0);
        // ZNeg
        t.addVertexWithUV(x1, y2, z1, u2, v1);
        t.addVertexWithUV(x2, y2, z1, u1, v1);
        t.addVertexWithUV(x2, y1, z1, u1, v2);
        t.addVertexWithUV(x1, y1, z1, u2, v2);
        // ZPos
        t.addVertexWithUV(x1, y2, z2, u1, v1);
        t.addVertexWithUV(x1, y1, z2, u1, v2);
        t.addVertexWithUV(x2, y1, z2, u2, v2);
        t.addVertexWithUV(x2, y2, z2, u2, v1);

        // X faces
        u1 = icon.getInterpolatedU(MIN_Z * 16.0);
        u2 = icon.getInterpolatedU(MAX_Z * 16.0);
        // XNeg
        t.addVertexWithUV(x1, y2, z2, u2, v1);
        t.addVertexWithUV(x1, y2, z1, u1, v1);
        t.addVertexWithUV(x1, y1, z1, u1, v2);
        t.addVertexWithUV(x1, y1, z2, u2, v2);
        // XPos
        t.addVertexWithUV(x2, y1, z2, u1, v2);
        t.addVertexWithUV(x2, y1, z1, u2, v2);
        t.addVertexWithUV(x2, y2, z1, u2, v1);
        t.addVertexWithUV(x2, y2, z2, u1, v1);
    }
}
