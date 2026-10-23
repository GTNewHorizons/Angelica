package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager.BlockMeta;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.BakedTransformCapture;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.gtnewhorizons.angelica.rendering.tesr.GlintCapture;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;

public final class DroppedItemInstancer {

    public static final long SKIP = -2L;
    public static final long RUN = -1L;

    private static final int CAPTURE_BYTES = 64 * 1024;

    private static final ItemPropCache<IconMesh> icons = new ItemPropCache<>(() -> AngelicaConfig.itemRendererCacheSize, IconMesh::new, null);
    private static final Object2ObjectOpenHashMap<BlockMeta, BlockMesh> blocks = new Object2ObjectOpenHashMap<>();
    private static final BlockMeta blockKey = new BlockMeta();
    private static final AngelicaTesrMeshCache.GtnhMeshBackend iconBackend = new AngelicaTesrMeshCache.GtnhMeshBackend();
    private static BakedTransformCapture blockBackend;
    private static boolean basePart;
    private static int itemsDrawingBlocks;
    private static final GlintCapture glintCapture = new GlintCapture();
    private static boolean glintSeen;
    private static final ItemPropCache.ItemProp glintProperties = new ItemPropCache.ItemProp();
    private static final ItemPropCache.ItemProp currentGlintProperties = new ItemPropCache.ItemProp();
    private static final GlintMesh glintMesh = new GlintMesh();

    private static long instanced;
    private static long glintInstanced;
    private static long fallback;

    public enum BailReason {
        INELIGIBLE("items.bail.ineligible"),
        NO_MATERIAL("items.bail.material"),
        ISBRH("items.bail.isbrh"),
        BLOCK_STATE("items.bail.blockState"),
        NOT_ALLOWED("items.bail.notAllowed"),
        TEMPLATE("items.bail.template"),
        QUEUE("items.bail.queue");

        public static final BailReason[] VALUES = values();
        public final String plotName;

        BailReason(String plotName) {
            this.plotName = plotName;
        }
    }

    private static final long[] bails = new long[BailReason.VALUES.length];

    private DroppedItemInstancer() {}

    public static long statInstanced() {
        return instanced;
    }

    public static long statGlintInstanced() {
        return glintInstanced;
    }

    public static long statFallback() {
        return fallback;
    }

    public static long statBail(BailReason reason) {
        return bails[reason.ordinal()];
    }

    private static void bail(BailReason reason) {
        bails[reason.ordinal()]++;
    }

    private static boolean glintEligible(ItemStack stack) {
        return stack != null && stack.getItem() != null && ModelPartBatcher.INSTANCE.isActive() && !GLStateManager.isRecordingDisplayList()
            && !TessellatorManager.isCurrentlyCapturing() && !TessellatorManager.shouldInterceptDraw(Tessellator.instance);
    }

    static TesrMaterial material(ItemStack stack, boolean unfilteredAtlas) {
        if (stack == null) return null;
        if (!glintEligible(stack)) {
            fallback++;
            bail(BailReason.INELIGIBLE);
            return null;
        }
        final TesrMaterial material = EntityMaterials.itemFromCurrentState(unfilteredAtlas);
        if (material == null) {
            fallback++;
            bail(BailReason.NO_MATERIAL);
        }
        return material;
    }

    public static long icon(ItemStack stack, boolean afterGlint, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        glintCapture.reset();
        glintSeen = false;
        final TesrMaterial material = material(stack, true);
        if (material == null) {
            basePart = false;
            return RUN;
        }
        final long part = batchIcon(material, afterGlint, maxU, minV, minU, maxV, width, height, thickness);
        basePart = part == SKIP;
        return part;
    }

    static long batchIcon(TesrMaterial material, boolean afterGlint, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        final long drawsBefore = GLStateManager.drawCalls;
        if (BatchEligibility.batchingAllowed()) {
            final TemplateBuffer template = iconTemplate(maxU, minV, minU, maxV, width, height, thickness);
            if (template == null) {
                fallback++;
                bail(BailReason.TEMPLATE);
                return RUN;
            }
            if (ModelPartBatcher.INSTANCE.queueTemplate(template, material, afterGlint)) {
                instanced++;
                BatchEligibility.onPartQueued();
                return SKIP;
            }
            fallback++;
            bail(BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        return drawsBefore;
    }

    /** Held glints use ITEM_GLINT for its stencil guard; dropped items draw with culling on and need none. */
    public static long glint(float maxU, float minV, float minU, float maxV, int width, int height, float thickness, TesrMaterial material) {
        if (!basePart) {
            return RUN;
        }
        final boolean allowed = BatchEligibility.batchingAllowed();
        if (allowed && ModelPartBatcher.INSTANCE.isShadowPass()) {
            BatchEligibility.onPartQueued();
            return SKIP;
        }
        final long drawsBefore = GLStateManager.drawCalls;
        if (allowed) {
            currentGlintProperties.set(maxU, minV, minU, maxV, width, height, thickness);
            if (glintSeen && glintProperties.equals(currentGlintProperties) && ModelPartBatcher.INSTANCE.replayGlint(glintCapture)) {
                glintInstanced++;
                return SKIP;
            }
            final TemplateBuffer template = iconTemplate(maxU, minV, minU, maxV, width, height, thickness);
            final boolean capture = !glintSeen && ModelPartBatcher.INSTANCE.beginGlintCapture(glintCapture);
            if (capture) glintProperties.set(currentGlintProperties);
            glintSeen = true;
            final boolean queued;
            try {
                queued = template != null && ModelPartBatcher.INSTANCE.queueTemplate(template, material);
            } finally {
                if (capture) glintCapture.end();
            }
            if (queued) {
                glintInstanced++;
                BatchEligibility.onPartQueued();
                return SKIP;
            }
            fallback++;
            bail(template == null ? BailReason.TEMPLATE : BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        return drawsBefore;
    }

    public static boolean canQueueSkippedGlint() {
        return basePart && BatchEligibility.batchingAllowed();
    }

    public static boolean queueSkippedGlint(int stripsWide, int stripsHigh, TesrMaterial material) {
        if (!canQueueSkippedGlint()) return false;
        final TemplateBuffer template = glintMesh.get(stripsWide, stripsHigh);
        if (template == null || !ModelPartBatcher.INSTANCE.queueSkippedItemGlint(template, material)) return false;
        glintInstanced += 2;
        BatchEligibility.onPartQueued();
        BatchEligibility.onPartQueued();
        return true;
    }

    public static long block(ItemStack stack, RenderBlocks rb, Block block, int meta, float brightness, boolean unfilteredAtlas) {
        final long part = itemBlock(stack, rb, block, meta, brightness, unfilteredAtlas);
        if (part != SKIP) itemsDrawingBlocks++;
        return part;
    }

    public static void endBlock(long part) {
        if (part != SKIP) itemsDrawingBlocks--;
        endPart(part);
    }

    public static long directBlock(RenderBlocks rb, Block block, int meta, float brightness) {
        if (itemsDrawingBlocks != 0 || !ModelPartBatcher.INSTANCE.entitiesActive()) return RUN;
        if (!BatchEligibility.batchingAllowed()) return GLStateManager.drawCalls;
        final TesrMaterial material = EntityMaterials.itemFromCurrentState(false);
        return material != null ? batchBlock(material, rb, block, meta, brightness) : GLStateManager.drawCalls;
    }

    private static long itemBlock(ItemStack stack, RenderBlocks rb, Block block, int meta, float brightness, boolean unfilteredAtlas) {
        final TesrMaterial material = material(stack, unfilteredAtlas);
        if (material == null) {
            return RUN;
        }
        final BailReason stateBail = blockStateBail(rb, block, brightness);
        if (stateBail != null) {
            fallback++;
            bail(stateBail);
            return RUN;
        }
        return batchBlock(material, rb, block, meta, brightness);
    }

    static long batchBlock(TesrMaterial material, RenderBlocks rb, Block block, int meta, float brightness) {
        final long drawsBefore = GLStateManager.drawCalls;
        if (BatchEligibility.batchingAllowed()) {
            BlockMesh mesh = lookupBlock(block, meta);
            if (mesh == null) {
                mesh = captureBlock(rb, block, meta, brightness);
            } else if (mesh.template() != null) {
                GLStateManager.glColor4f(mesh.red(), mesh.green(), mesh.blue(), mesh.alpha());
            }
            if (mesh.template() == null) {
                fallback++;
                bail(BailReason.TEMPLATE);
                return RUN;
            }
            if (ModelPartBatcher.INSTANCE.queueTemplate(mesh.template(), material)) {
                mesh.sprites().markUsed();
                instanced++;
                BatchEligibility.onPartQueued();
                return SKIP;
            }
            fallback++;
            bail(BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        return drawsBefore;
    }

    public static void endPart(long part) {
        if (part >= 0L) {
            BatchEligibility.onPartFallback(part, GLStateManager.drawCalls);
        }
    }

    public static void forgetItemDraws() {
        itemsDrawingBlocks = 0;
    }

    public static void clear() {
        icons.clear();
        blocks.clear();
        blockKey.block = null;
        basePart = false;
        glintCapture.reset();
        glintSeen = false;
        glintMesh.template = null;
        if (blockBackend != null) {
            blockBackend.delete();
            blockBackend = null;
        }
    }

    private static final class GlintMesh {
        TemplateBuffer template;
        int stripsWide, stripsHigh;

        TemplateBuffer get(int stripsWide, int stripsHigh) {
            if (template == null || this.stripsWide != stripsWide || this.stripsHigh != stripsHigh) {
                template = iconTemplate(0.0F, 0.0F, 1.0F, 1.0F, stripsWide, stripsHigh, 0.0625F);
                this.stripsWide = stripsWide;
                this.stripsHigh = stripsHigh;
            }
            return template;
        }
    }

    private static TemplateBuffer iconTemplate(float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        final IconMesh hit = icons.get(maxU, minV, minU, maxV, width, height, thickness);
        if (hit != null) return hit.template;
        final Tessellator capture = iconBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
        final TemplateBuffer template;
        try {
            ItemRenderer.renderItemIn2D(capture, maxU, minV, minU, maxV, width, height, thickness);
        } finally {
            template = iconBackend.endCaptureToTemplate();
        }
        if (template == null) return null;
        icons.insert().template = template;
        return template;
    }

    private static BlockMesh captureBlock(RenderBlocks rb, Block block, int meta, float brightness) {
        if (blockBackend == null) {
            blockBackend = new BakedTransformCapture(CAPTURE_BYTES);
        }
        final TemplateBuffer template;
        final AnimationsRenderUtils.SpriteCapture sprites = AnimationsRenderUtils.captureSprites();
        GLStateManager.glPushMatrix();
        try {
            blockBackend.begin();
            try {
                rb.renderBlockAsItem(block, meta, brightness);
            } finally {
                template = blockBackend.end();
            }
        } finally {
            GLStateManager.glPopMatrix();
            sprites.close();
        }
        final Color4 color = GLStateManager.getColor();
        final BlockMesh mesh = new BlockMesh(template, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), sprites);
        blocks.put(new BlockMeta(block, meta), mesh);
        return mesh;
    }

    private static BlockMesh lookupBlock(Block block, int meta) {
        blockKey.block = block;
        blockKey.meta = meta;
        return blocks.get(blockKey);
    }

    private static BailReason blockStateBail(RenderBlocks renderBlocks, Block block, float brightness) {
        if (BlockRenderListManager.isISBRH(block.getRenderType())) return BailReason.ISBRH;
        return BlockRenderListManager.rendersPlainItem(renderBlocks, block, brightness) ? null : BailReason.BLOCK_STATE;
    }

    private static final class IconMesh {
        TemplateBuffer template;
    }

    private record BlockMesh(TemplateBuffer template, float red, float green, float blue, float alpha,
                             AnimationsRenderUtils.SpriteCapture sprites) {}
}
