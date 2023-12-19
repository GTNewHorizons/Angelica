package com.prupe.mcpatcher.mal.block;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.MCPatcherUtils;

import cpw.mods.fml.common.registry.GameData;

public class BlockStateMatcher {

    private final String fullString;
    private final ThreadLocal<Object> threadLocal = new ThreadLocal<>();

    protected final Block block;
    protected Object data;

    private static final int MAX_METADATA = 15;
    private static final int NO_METADATA = -1;

    private final int metadataBits;

    private static Block doublePlantBlock;
    private static Block logBlock;

    public BlockStateMatcher(String metaString, Block block, String metadataList) {
        this.fullString = BlockAPI.getBlockName(block) + metaString;
        this.block = block;
        if (MCPatcherUtils.isNullOrEmpty(metadataList)) {
            metadataBits = NO_METADATA;
        } else {
            int bits = 0;
            for (int i : MCPatcherUtils.parseIntegerList(metadataList, 0, MAX_METADATA)) {
                bits |= 1 << i;
            }
            metadataBits = bits;
        }
        doublePlantBlock = GameData.getBlockRegistry()
            .getObject("minecraft:double_plant");
        logBlock = GameData.getBlockRegistry()
            .getObject("minecraft:log");
    }

    final public Block getBlock() {
        return block;
    }

    final public Object getData() {
        return data;
    }

    final public void setData(Object data) {
        this.data = data;
    }

    final public Object getThreadData() {
        return threadLocal.get();
    }

    final public void setThreadData(Object data) {
        threadLocal.set(data);
    }

    final public String toString() {
        return fullString;
    }

    public boolean match(IBlockAccess blockAccess, int i, int j, int k) {
        Block block = BlockAPI.getBlockAt(blockAccess, i, j, k);
        if (block != this.block) {
            return false;
        }
        int metadata = BlockAPI.getMetadataAt(blockAccess, i, j, k);
        if (block == doublePlantBlock) {
            if ((metadata & 0x8) != 0 && BlockAPI.getBlockAt(blockAccess, i, j - 1, k) == block) {
                metadata = BlockAPI.getMetadataAt(blockAccess, i, j - 1, k);
            }
            metadata &= 0x7;
        } else if (block == logBlock) {
            metadata &= ~0xc;
        }
        return (metadataBits & (1 << metadata)) != 0;
    }

    public boolean match(Block block, int metadata) {
        return block == this.block && (metadataBits & (1 << metadata)) != 0;
    }

}
