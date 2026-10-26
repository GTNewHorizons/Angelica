package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.resources.IReloadableResourceManager;
import org.lwjgl.opengl.GL11;

public class BlockRenderListManager {
    private static final BlockMeta key = new BlockMeta();

    private static final Object2IntOpenHashMap<BlockMeta> displayListMap = new Object2IntOpenHashMap<>();
    private static final int[] itemFrameDisplayLists = new int[8];


    private static final int ISBRH_CUTOFF = 40;

    private static final boolean[] notISBRH = new boolean[ISBRH_CUTOFF];

    static {
        for (int i : new int[]
            { 0, 31, 39, 16, 26, 1, 19, 23, 13, 6, 2, 10, 27, 11, 21, 32, 35, 34, 38 }
        ) {
            notISBRH[i] = true;
        }
    }

    public static boolean isISBRH(int renderType) {
        return renderType >= ISBRH_CUTOFF || renderType < 0 || !notISBRH[renderType];
    }

    public static int getDisplayList(Block block, int meta) {
        key.block = block;
        key.meta = meta;
        return displayListMap.getInt(key);
    }

    public static int getItemFrameDisplayList(int variant) {
        return itemFrameDisplayLists[variant];
    }

    public static boolean rendersPlainItem(RenderBlocks rb, Block block, float brightness) {
        return !isISBRH(block.getRenderType()) && !rb.enableAO && rb.overrideBlockTexture == null && brightness == 1.0F && rb.useInventoryTint
            && rb.blockAccess == null && (rb.uvRotateEast | rb.uvRotateWest | rb.uvRotateSouth | rb.uvRotateNorth | rb.uvRotateTop | rb.uvRotateBottom) == 0;
    }

    public static int callOrStartCompiling(int cached) {
        if (cached != 0) {
            GLSMDraws.glCallList(cached);
            return -1;
        }
        return startCompiling();
    }

    private static int startCompiling() {
        final int list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, GL11.GL_COMPILE);
        return list;
    }

    public static void endCompiling(int list, Block block, int meta) {
        if (list <= 0) return;
        finishCompiling();
        displayListMap.put(new BlockMeta(block, meta), list);
        GLSMDraws.glCallList(list);
    }

    public static void endItemFrameCompiling(int list, int variant) {
        if (list <= 0) return;
        finishCompiling();
        itemFrameDisplayLists[variant] = list;
        GLSMDraws.glCallList(list);
    }

    public static void abortCompiling(int list) {
        if (list <= 0) return;
        finishCompiling();
        GLSMDraws.glDeleteLists(list, 1);
    }

    private static void finishCompiling() {
        DisplayListManager.discardMatrixTransforms();
        GLSMDraws.glEndList();
    }

    public static void registerReloadListener(){
        IReloadableResourceManager resourceManager = (IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager();
        resourceManager.registerReloadListener(_ -> {
            for (int list : displayListMap.values()) {
                GLSMDraws.glDeleteLists(list, 1);
            }
            displayListMap.clear();
            for (int i = 0; i < itemFrameDisplayLists.length; i++) {
                if (itemFrameDisplayLists[i] != 0) {
                    GLSMDraws.glDeleteLists(itemFrameDisplayLists[i], 1);
                    itemFrameDisplayLists[i] = 0;
                }
            }
        });
    }


    static final class BlockMeta {
        Block block;
        int meta;

        public BlockMeta() {

        }

        public BlockMeta(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }

        @Override
        public int hashCode() {
            return (block.hashCode() * 31 + meta) * 31;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BlockMeta other)) return false;
            return block == other.block && meta == other.meta;
        }
    }
}
