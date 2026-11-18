package com.gtnewhorizons.angelica.helpers;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Light value of the block an ItemBlock stack would place.
 */
public final class ItemBlockLight {

    private static final SingleBlockAccess ACCESS = new SingleBlockAccess();

    private ItemBlockLight() {}

    public static int getLightValue(ItemBlock item, ItemStack stack) {
        final Block block = item.field_150939_a;
        if (block == null) return 0;
        ACCESS.set(block, item.getMetadata(stack.getItemDamage()));
        try {
            return block.getLightValue(ACCESS, 0, 0, 0);
        } catch (RuntimeException e) {
            return block.getLightValue();
        } finally {
            ACCESS.set(Blocks.air, 0);
        }
    }

    private static final class SingleBlockAccess implements IBlockAccess {
        private Block block = Blocks.air;
        private int metadata;

        void set(Block block, int metadata) {
            this.block = block;
            this.metadata = metadata;
        }

        private static boolean isOrigin(int x, int y, int z) {
            return x == 0 && y == 0 && z == 0;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return isOrigin(x, y, z) ? block : Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return isOrigin(x, y, z) ? metadata : 0;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int min) {
            return min << 4;
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int direction) {
            return 0;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return !isOrigin(x, y, z) || block == Blocks.air;
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return BiomeGenBase.plains;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            return false;
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean _default) {
            return false;
        }
    }
}
