package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.rendering.OperationArgs;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager.BlockMeta;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.BakedTransformCapture;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;

public final class DroppedItemInstancer {

    private static final int CAPTURE_BYTES = 64 * 1024;

    private static final ItemPropCache<IconMesh> icons = new ItemPropCache<>(() -> AngelicaConfig.itemRendererCacheSize, IconMesh::new, null);
    private static final Object2ObjectOpenHashMap<BlockMeta, BlockMesh> blocks = new Object2ObjectOpenHashMap<>();
    private static final BlockMeta blockKey = new BlockMeta();
    private static final AngelicaTesrMeshCache.GtnhMeshBackend iconBackend = new AngelicaTesrMeshCache.GtnhMeshBackend();
    private static final Object[] ICON_ARGS = new Object[8];
    private static final Object[] GLINT_ARGS = new Object[8];
    private static final Object[] BLOCK_ARGS = new Object[4];
    private static BakedTransformCapture blockBackend;
    private static boolean basePart;

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
        return stack != null && stack.getItem() != null && ModelPartBatcher.INSTANCE.isActive() && !GLStateManager.isRecordingDisplayList() && !TessellatorManager.isCurrentlyCapturing() && !TessellatorManager.shouldInterceptDraw(Tessellator.instance);
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

    public static void icon(ItemStack stack, Tessellator t, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        final TesrMaterial material = material(stack, true);
        if (material == null) {
            basePart = false;
            callIcon(ICON_ARGS, original, t, maxU, minV, minU, maxV, width, height, thickness);
            return;
        }
        basePart = batchIcon(material, t, maxU, minV, minU, maxV, width, height, thickness, original);
    }

    static boolean batchIcon(TesrMaterial material, Tessellator t, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        final long drawsBefore = GLStateManager.drawCalls;
        if (BatchEligibility.batchingAllowed()) {
            final TemplateBuffer template = iconTemplate(ICON_ARGS, maxU, minV, minU, maxV, width, height, thickness, original);
            if (template == null) {
                callIcon(ICON_ARGS, original, t, maxU, minV, minU, maxV, width, height, thickness);
                fallback++;
                bail(BailReason.TEMPLATE);
                return false;
            }
            if (ModelPartBatcher.INSTANCE.queueTemplate(template, material)) {
                instanced++;
                BatchEligibility.onPartQueued();
                return true;
            }
            fallback++;
            bail(BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        callIcon(ICON_ARGS, original, t, maxU, minV, minU, maxV, width, height, thickness);
        BatchEligibility.onPartFallback(drawsBefore, GLStateManager.drawCalls);
        return true;
    }

    public static void glint(Tessellator t, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        if (!basePart) {
            callIcon(GLINT_ARGS, original, t, maxU, minV, minU, maxV, width, height, thickness);
            return;
        }
        final boolean allowed = BatchEligibility.batchingAllowed();
        if (allowed && ModelPartBatcher.INSTANCE.isShadowPass()) {
            BatchEligibility.onPartQueued();
            return;
        }
        final long drawsBefore = GLStateManager.drawCalls;
        if (allowed) {
            final TemplateBuffer template = iconTemplate(GLINT_ARGS, maxU, minV, minU, maxV, width, height, thickness, original);
            if (template != null && ModelPartBatcher.INSTANCE.queueTemplate(template, EntityMaterials.GLINT)) {
                glintInstanced++;
                BatchEligibility.onPartQueued();
                return;
            }
            fallback++;
            bail(template == null ? BailReason.TEMPLATE : BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        callIcon(GLINT_ARGS, original, t, maxU, minV, minU, maxV, width, height, thickness);
        BatchEligibility.onPartFallback(drawsBefore, GLStateManager.drawCalls);
    }

    public static void block(ItemStack stack, RenderBlocks rb, Block block, int meta, float brightness, boolean unfilteredAtlas, Operation<Void> original) {
        final TesrMaterial material = material(stack, unfilteredAtlas);
        if (material == null) {
            callBlock(original, rb, block, meta, brightness);
            return;
        }
        final BailReason stateBail = blockStateBail(rb, block, brightness);
        if (stateBail != null) {
            fallback++;
            bail(stateBail);
            callBlock(original, rb, block, meta, brightness);
            return;
        }
        batchBlock(material, rb, block, meta, brightness, original);
    }

    static void batchBlock(TesrMaterial material, RenderBlocks rb, Block block, int meta, float brightness, Operation<Void> original) {
        final long drawsBefore = GLStateManager.drawCalls;
        if (BatchEligibility.batchingAllowed()) {
            BlockMesh mesh = lookupBlock(block, meta);
            if (mesh == null) {
                mesh = captureBlock(rb, block, meta, brightness, original);
            } else if (mesh.template() != null) {
                GLStateManager.glColor4f(mesh.red(), mesh.green(), mesh.blue(), mesh.alpha());
            }
            if (mesh.template() == null) {
                callBlock(original, rb, block, meta, brightness);
                fallback++;
                bail(BailReason.TEMPLATE);
                return;
            }
            if (ModelPartBatcher.INSTANCE.queueTemplate(mesh.template(), material)) {
                instanced++;
                BatchEligibility.onPartQueued();
                return;
            }
            fallback++;
            bail(BailReason.QUEUE);
        } else {
            bail(BailReason.NOT_ALLOWED);
        }
        callBlock(original, rb, block, meta, brightness);
        BatchEligibility.onPartFallback(drawsBefore, GLStateManager.drawCalls);
    }

    public static void clear() {
        icons.clear();
        blocks.clear();
        blockKey.block = null;
        basePart = false;
        if (blockBackend != null) {
            blockBackend.delete();
            blockBackend = null;
        }
    }

    private static void callIcon(Object[] args, Operation<Void> original, Tessellator t, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        args[0] = t;
        args[1] = OperationArgs.boxed(args[1], maxU);
        args[2] = OperationArgs.boxed(args[2], minV);
        args[3] = OperationArgs.boxed(args[3], minU);
        args[4] = OperationArgs.boxed(args[4], maxV);
        args[5] = OperationArgs.boxed(args[5], width);
        args[6] = OperationArgs.boxed(args[6], height);
        args[7] = OperationArgs.boxed(args[7], thickness);
        try {
            original.call(args);
        } finally {
            args[0] = null;
        }
    }

    private static void callBlock(Operation<Void> original, RenderBlocks rb, Block block, int meta, float brightness) {
        final Object[] args = BLOCK_ARGS;
        args[0] = rb;
        args[1] = block;
        args[2] = OperationArgs.boxed(args[2], meta);
        args[3] = OperationArgs.boxed(args[3], brightness);
        try {
            original.call(args);
        } finally {
            args[0] = null;
            args[1] = null;
        }
    }

    private static TemplateBuffer iconTemplate(Object[] args, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        final IconMesh hit = icons.get(maxU, minV, minU, maxV, width, height, thickness);
        if (hit != null) return hit.template;
        final Tessellator capture = iconBackend.beginCapture(DefaultVertexFormat.POSITION_TEXTURE_NORMAL);
        final TemplateBuffer template;
        try {
            callIcon(args, original, capture, maxU, minV, minU, maxV, width, height, thickness);
        } finally {
            template = iconBackend.endCaptureToTemplate();
        }
        if (template == null) return null;
        icons.insert().template = template;
        return template;
    }

    private static BlockMesh captureBlock(RenderBlocks rb, Block block, int meta, float brightness, Operation<Void> original) {
        if (blockBackend == null) {
            blockBackend = new BakedTransformCapture(CAPTURE_BYTES);
        }
        final TemplateBuffer template;
        GLStateManager.glPushMatrix();
        try {
            blockBackend.begin();
            try {
                callBlock(original, rb, block, meta, brightness);
            } finally {
                template = blockBackend.end();
            }
        } finally {
            GLStateManager.glPopMatrix();
        }
        final Color4 color = GLStateManager.getColor();
        final BlockMesh mesh = new BlockMesh(template, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
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
        if (renderBlocks.enableAO || renderBlocks.overrideBlockTexture != null || brightness != 1.0F || !renderBlocks.useInventoryTint || renderBlocks.blockAccess != null || (renderBlocks.uvRotateEast | renderBlocks.uvRotateWest | renderBlocks.uvRotateSouth | renderBlocks.uvRotateNorth | renderBlocks.uvRotateTop | renderBlocks.uvRotateBottom) != 0) return BailReason.BLOCK_STATE;
        return null;
    }

    private static final class IconMesh {
        TemplateBuffer template;
    }

    private record BlockMesh(TemplateBuffer template, float red, float green, float blue, float alpha) {}
}
