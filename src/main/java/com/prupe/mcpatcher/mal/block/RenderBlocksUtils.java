package com.prupe.mcpatcher.mal.block;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.Config;
import com.prupe.mcpatcher.MCPatcherUtils;

// Shared by both CTM and Custom Colors.
public class RenderBlocksUtils {

    public static final boolean enableBetterGrass = Config
        .getBoolean(MCPatcherUtils.CONNECTED_TEXTURES, "grass", false);

    private static final Block grassBlock = BlockAPI.getFixedBlock("minecraft:grass");
    private static final Block snowBlock = BlockAPI.getFixedBlock("minecraft:snow_layer");
    private static final Block craftedSnowBlock = BlockAPI.getFixedBlock("minecraft:snow");

    private static final int COLOR = 0;
    private static final int NONCOLOR = 1;
    private static final int COLOR_AND_NONCOLOR = 2;

    private static final int[] colorMultiplierType = new int[6];
    private static final float[][] nonAOMultipliers = new float[6][3];

    public static final float[] AO_BASE = new float[] { 0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f };

    public static int layerIndex;
    public static IIcon blankIcon;

    public static void setupColorMultiplier(Block block, IBlockAccess blockAccess, int i, int j, int k,
        boolean haveOverrideTexture, float r, float g, float b) {
        if (haveOverrideTexture || !RenderPassAPI.instance.useColorMultiplierThisPass(block)) {
            colorMultiplierType[0] = COLOR;
            colorMultiplierType[2] = COLOR;
            colorMultiplierType[3] = COLOR;
            colorMultiplierType[4] = COLOR;
            colorMultiplierType[5] = COLOR;
        } else if (block == grassBlock) {
            colorMultiplierType[0] = NONCOLOR;
            if (enableBetterGrass) {
                if (isSnowCovered(blockAccess, i, j, k)) {
                    colorMultiplierType[2] = NONCOLOR;
                    colorMultiplierType[3] = NONCOLOR;
                    colorMultiplierType[4] = NONCOLOR;
                    colorMultiplierType[5] = NONCOLOR;
                } else {
                    j--;
                    colorMultiplierType[2] = block == BlockAPI.getBlockAt(blockAccess, i, j, k - 1)
                        && !isSnowCovered(blockAccess, i, j, k - 1) ? COLOR : COLOR_AND_NONCOLOR;
                    colorMultiplierType[3] = block == BlockAPI.getBlockAt(blockAccess, i, j, k + 1)
                        && !isSnowCovered(blockAccess, i, j, k + 1) ? COLOR : COLOR_AND_NONCOLOR;
                    colorMultiplierType[4] = block == BlockAPI.getBlockAt(blockAccess, i - 1, j, k)
                        && !isSnowCovered(blockAccess, i - 1, j, k) ? COLOR : COLOR_AND_NONCOLOR;
                    colorMultiplierType[5] = block == BlockAPI.getBlockAt(blockAccess, i + 1, j, k)
                        && !isSnowCovered(blockAccess, i + 1, j, k) ? COLOR : COLOR_AND_NONCOLOR;
                }
            } else {
                colorMultiplierType[2] = COLOR_AND_NONCOLOR;
                colorMultiplierType[3] = COLOR_AND_NONCOLOR;
                colorMultiplierType[4] = COLOR_AND_NONCOLOR;
                colorMultiplierType[5] = COLOR_AND_NONCOLOR;
            }
        } else {
            colorMultiplierType[0] = COLOR;
            colorMultiplierType[2] = COLOR;
            colorMultiplierType[3] = COLOR;
            colorMultiplierType[4] = COLOR;
            colorMultiplierType[5] = COLOR;
        }
        if (!isAmbientOcclusionEnabled() || BlockAPI.getBlockLightValue(block) != 0) {
            setupColorMultiplier(0, r, g, b);
            setupColorMultiplier(1, r, g, b);
            setupColorMultiplier(2, r, g, b);
            setupColorMultiplier(3, r, g, b);
            setupColorMultiplier(4, r, g, b);
            setupColorMultiplier(5, r, g, b);
        }
    }

    public static void setupColorMultiplier(Block block, boolean useColor) {
        if (block == grassBlock || !useColor) {
            colorMultiplierType[0] = NONCOLOR;
            colorMultiplierType[2] = NONCOLOR;
            colorMultiplierType[3] = NONCOLOR;
            colorMultiplierType[4] = NONCOLOR;
            colorMultiplierType[5] = NONCOLOR;
        } else {
            colorMultiplierType[0] = COLOR;
            colorMultiplierType[2] = COLOR;
            colorMultiplierType[3] = COLOR;
            colorMultiplierType[4] = COLOR;
            colorMultiplierType[5] = COLOR;
        }
    }

    private static void setupColorMultiplier(int face, float r, float g, float b) {
        float[] mult = nonAOMultipliers[face];
        float ao = AO_BASE[face];
        mult[0] = ao;
        mult[1] = ao;
        mult[2] = ao;
        if (colorMultiplierType[face] != NONCOLOR) {
            mult[0] *= r;
            mult[1] *= g;
            mult[2] *= b;
        }
    }

    public static boolean useColorMultiplier(int face) {
        layerIndex = 0;
        return useColorMultiplier1(face);
    }

    private static boolean useColorMultiplier1(int face) {
        if (layerIndex == 0) {
            return colorMultiplierType[getFaceIndex(face)] == COLOR;
        } else {
            return colorMultiplierType[getFaceIndex(face)] != NONCOLOR;
        }
    }

    public static float getColorMultiplierRed(int face) {
        return nonAOMultipliers[getFaceIndex(face)][0];
    }

    public static float getColorMultiplierGreen(int face) {
        return nonAOMultipliers[getFaceIndex(face)][1];
    }

    public static float getColorMultiplierBlue(int face) {
        return nonAOMultipliers[getFaceIndex(face)][2];
    }

    private static int getFaceIndex(int face) {
        return face < 0 ? 1 : face % 6;
    }

    public static IIcon getGrassTexture(Block block, IBlockAccess blockAccess, int i, int j, int k, int face,
        IIcon topIcon) {
        if (!enableBetterGrass || face < 2) {
            return null;
        }
        boolean isSnow = isSnowCovered(blockAccess, i, j, k);
        j--;
        switch (face) {
            case 2:
                k--;
                break;

            case 3:
                k++;
                break;

            case 4:
                i--;
                break;

            case 5:
                i++;
                break;

            default:
                return null;
        }
        if (block != BlockAPI.getBlockAt(blockAccess, i, j, k)) {
            return null;
        }
        boolean neighborIsSnow = isSnowCovered(blockAccess, i, j, k);
        if (isSnow != neighborIsSnow) {
            return null;
        }
        return isSnow ? BlockAPI.getBlockIcon(snowBlock, blockAccess, i, j, k, face) : topIcon;
    }

    private static boolean isSnowCovered(IBlockAccess blockAccess, int i, int j, int k) {
        Block topBlock = BlockAPI.getBlockAt(blockAccess, i, j + 1, k);
        return topBlock == snowBlock || topBlock == craftedSnowBlock;
    }

    public static boolean isAmbientOcclusionEnabled() {
        return Minecraft.isAmbientOcclusionEnabled();
    }
}
