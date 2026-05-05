package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import org.lwjgl.opengl.GL11;

public class BlockRenderListManager {
    private static final BlockMeta key = new BlockMeta();

    private static final Object2IntOpenHashMap<BlockMeta> displayListMap = new Object2IntOpenHashMap<>();


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

    public static int startCompiling() {
        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        return list;
    }

    public static void endCompiling(int list, Block block, int meta) {
        GLStateManager.glEndList();
        displayListMap.put(new BlockMeta(block, meta), list);
    }


    private static final class BlockMeta {
        private Block block;
        private int meta;

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
