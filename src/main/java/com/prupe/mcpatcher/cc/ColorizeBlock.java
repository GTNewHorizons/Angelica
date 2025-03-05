package com.prupe.mcpatcher.cc;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.biome.BiomeAPI;
import com.prupe.mcpatcher.mal.biome.ColorMap;
import com.prupe.mcpatcher.mal.biome.ColorMapBase;
import com.prupe.mcpatcher.mal.biome.ColorUtils;
import com.prupe.mcpatcher.mal.biome.IColorMap;
import com.prupe.mcpatcher.mal.block.BlockAPI;
import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.ResourceList;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

import jss.notfine.config.MCPatcherForgeConfig;

@Lwjgl3Aware
public class ColorizeBlock {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_COLORS);

    private static final boolean useWaterColors = MCPatcherForgeConfig.CustomColors.water;
    private static final boolean useTreeColors = MCPatcherForgeConfig.CustomColors.tree;
    private static final boolean useRedstoneColors = MCPatcherForgeConfig.CustomColors.redstone;
    private static final boolean useStemColors = MCPatcherForgeConfig.CustomColors.stem;
    private static final boolean useBlockColors = MCPatcherForgeConfig.CustomColors.otherBlocks;

    private static final boolean enableSmoothBiomes = MCPatcherForgeConfig.CustomColors.smoothBiomes;
    private static final boolean enableTestColorSmoothing = MCPatcherForgeConfig.CustomColors.testColorSmoothing;

    private static final ResourceLocation REDSTONE_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/redstone.png");
    private static final ResourceLocation STEM_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/stem.png");
    private static final ResourceLocation PUMPKIN_STEM_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/pumpkinstem.png");
    private static final ResourceLocation MELON_STEM_COLORS = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/melonstem.png");
    private static final ResourceLocation SWAMPGRASSCOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/swampgrass.png");
    private static final ResourceLocation SWAMPFOLIAGECOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/swampfoliage.png");
    private static final ResourceLocation DEFAULT_GRASSCOLOR = new ResourceLocation(
        "minecraft:textures/colormap/grass.png");
    private static final ResourceLocation DEFAULT_FOLIAGECOLOR = new ResourceLocation(
        "minecraft:textures/colormap/foliage.png");
    private static final ResourceLocation PINECOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/pine.png");
    private static final ResourceLocation BIRCHCOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/birch.png");
    private static final ResourceLocation WATERCOLOR = TexturePackAPI
        .newMCPatcherResourceLocation("colormap/water.png");

    private static final String PALETTE_BLOCK_KEY = "palette.block.";

    // bitmaps from palette.block.*
    private static final Map<Block, List<BlockStateMatcher>> blockColorMaps = new IdentityHashMap<>();
    private static IColorMap waterColorMap;
    private static float[][] redstoneColor; // colormap/redstone.png

    private static final int blockBlendRadius = MCPatcherForgeConfig.CustomColors.blockBlendRadius;

    public static int blockColor;
    public static boolean isSmooth;

    public static float colorRedTopLeft;
    public static float colorRedBottomLeft;
    public static float colorRedBottomRight;
    public static float colorRedTopRight;
    public static float colorGreenTopLeft;
    public static float colorGreenBottomLeft;
    public static float colorGreenBottomRight;
    public static float colorGreenTopRight;
    public static float colorBlueTopLeft;
    public static float colorBlueBottomLeft;
    public static float colorBlueBottomRight;
    public static float colorBlueTopRight;

    private static final int[][][] FACE_VERTICES = new int[][][] {
        // bottom face (y=0)
        { { 0, 0, 1 }, // top left
            { 0, 0, 0 }, // bottom left
            { 1, 0, 0 }, // bottom right
            { 1, 0, 1 }, // top right
        },
        // top face (y=1)
        { { 1, 1, 1 }, { 1, 1, 0 }, { 0, 1, 0 }, { 0, 1, 1 }, },
        // north face (z=0)
        { { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, },
        // south face (z=1)
        { { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, },
        // west face (x=0)
        { { 0, 1, 1 }, { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, },
        // east face (x=1)
        { { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 }, },

        // bottom face, water (y=0)
        { { 0, 0, 1 }, // top left
            { 0, 0, 0 }, // bottom left
            { 1, 0, 0 }, // bottom right
            { 1, 0, 1 }, // top right
        },
        // top face, water (y=1) cycle by 2
        { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 }, },
        // north face, water (z=0)
        { { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 }, },
        // south face, water (z=1) cycle by 1
        { { 1, 1, 1 }, { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 }, },
        // west face, water (x=0)
        { { 0, 1, 1 }, { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 }, },
        // east face, water (x=1) cycle by 2
        { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, }, };

    static {
        try {
            reset();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void reset() {
        blockColorMaps.clear();
        waterColorMap = null;
        resetVertexColors();
        redstoneColor = null;
    }

    static void reloadAll(PropertiesFile properties) {
        if (useBlockColors) {
            reloadBlockColors(properties);
        }
        if (useTreeColors) {
            reloadFoliageColors(properties);
        }
        if (useWaterColors) {
            reloadWaterColors(properties);
        }
        if (ColorMap.useSwampColors) {
            reloadSwampColors(properties);
        }
        if (useRedstoneColors) {
            reloadRedstoneColors(properties);
        }
        if (useStemColors) {
            reloadStemColors(properties);
        }
    }

    private static void reloadFoliageColors(PropertiesFile properties) {
        IColorMap colorMap = ColorMap.loadVanillaColorMap(DEFAULT_GRASSCOLOR, SWAMPGRASSCOLOR);
        registerColorMap(
            colorMap,
            DEFAULT_GRASSCOLOR,
            "minecraft:grass:snowy=false minecraft:tallgrass:1,2:type=tall_grass,fern minecraft:double_plant:2,3:variant=double_grass,double_fern");
        colorMap = ColorMap.loadVanillaColorMap(DEFAULT_FOLIAGECOLOR, SWAMPFOLIAGECOLOR);
        registerColorMap(colorMap, DEFAULT_FOLIAGECOLOR, "minecraft:leaves:0,4,8,12:variant=oak minecraft:vine");
        registerColorMap(PINECOLOR, "minecraft:leaves:1,5,9,13:variant=spruce");
        registerColorMap(BIRCHCOLOR, "minecraft:leaves:2,6,10,14:variant=birch");
    }

    private static IColorMap wrapBlockMap(IColorMap map) {
        if (map == null) {
            return null;
        } else {
            if (blockBlendRadius > 0) {
                map = new ColorMapBase.Blended(map, blockBlendRadius);
            }
            map = new ColorMapBase.Chunked(map);
            map = new ColorMapBase.Outer(map);
            return map;
        }
    }

    private static void reloadWaterColors(PropertiesFile properties) {
        waterColorMap = registerColorMap(WATERCOLOR, "minecraft:flowing_water minecraft:water");
    }

    private static void reloadSwampColors(PropertiesFile properties) {
        int[] lilypadColor = new int[] { 0x020830 };
        if (Colorizer.loadIntColor("lilypad", lilypadColor, 0)) {
            IColorMap colorMap = new ColorMap.Fixed(lilypadColor[0]);
            registerColorMap(colorMap, Colorizer.COLOR_PROPERTIES, "minecraft:waterlily");
        }
    }

    private static void reloadBlockColors(PropertiesFile properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!key.startsWith(PALETTE_BLOCK_KEY)) {
                continue;
            }
            key = key.substring(PALETTE_BLOCK_KEY.length())
                .trim();
            ResourceLocation resource = TexturePackAPI.parseResourceLocation(Colorizer.COLOR_PROPERTIES, key);
            if (resource == null) {
                continue;
            }
            registerColorMap(resource, value);
        }

        for (ResourceLocation resource : ResourceList.getInstance()
            .listResources(ColorMap.BLOCK_COLORMAP_DIR, ".properties", false)) {
            Properties properties1 = TexturePackAPI.getProperties(resource);
            IColorMap colorMap = ColorMap.loadColorMap(true, resource, properties1);
            registerColorMap(
                colorMap,
                resource,
                MCPatcherUtils.getStringProperty(properties1, "blocks", getDefaultBlockName(resource)));
        }
        List<ResourceLocation> unusedPNGs = new ArrayList<>(ColorMap.unusedPNGs);
        for (ResourceLocation resource : unusedPNGs) {
            Properties properties1 = new Properties();
            IColorMap colorMap = ColorMap.loadColorMap(true, resource, properties1);
            registerColorMap(colorMap, resource, getDefaultBlockName(resource));
        }
    }

    private static String getDefaultBlockName(ResourceLocation resource) {
        return resource.getResourceDomain() + ":"
            + resource.getResourcePath()
                .replaceFirst(".*/", "")
                .replaceFirst("\\.[^.]*$", "");
    }

    private static IColorMap registerColorMap(ResourceLocation resource, String idList) {
        IColorMap colorMap = ColorMap.loadColorMap(true, resource, null);
        return registerColorMap(colorMap, resource, idList);
    }

    private static IColorMap registerColorMap(IColorMap colorMap, ResourceLocation resource, String idList) {
        if (colorMap == null) {
            return null;
        }
        colorMap = wrapBlockMap(colorMap);
        for (String idString : idList.split("\\s+")) {
            BlockStateMatcher blockMatcher = BlockAPI.createMatcher(new PropertiesFile(logger, resource), idString);
            if (blockMatcher != null) {
                List<BlockStateMatcher> maps = blockColorMaps
                    .computeIfAbsent(blockMatcher.getBlock(), k -> new ArrayList<>());
                blockMatcher.setData(colorMap);
                maps.add(blockMatcher);
                if (resource != null) {
                    logger.fine(
                        "using %s for block %s, default color %06x",
                        colorMap,
                        blockMatcher,
                        colorMap.getColorMultiplier());
                }
            }
        }
        return colorMap;
    }

    private static void reloadRedstoneColors(PropertiesFile properties) {
        int[] rgb = MCPatcherUtils.getImageRGB(TexturePackAPI.getImage(REDSTONE_COLORS));
        if (rgb != null && rgb.length >= 16) {
            redstoneColor = new float[16][];
            for (int i = 0; i < 16; i++) {
                float[] f = new float[3];
                ColorUtils.intToFloat3(rgb[i], f);
                redstoneColor[i] = f;
            }
        }
    }

    private static void reloadStemColors(PropertiesFile properties) {
        ResourceLocation resource = TexturePackAPI.hasResource(PUMPKIN_STEM_COLORS) ? PUMPKIN_STEM_COLORS : STEM_COLORS;
        registerMetadataRGB("minecraft:pumpkin_stem", resource, "age", 8);
        resource = TexturePackAPI.hasResource(MELON_STEM_COLORS) ? MELON_STEM_COLORS : STEM_COLORS;
        registerMetadataRGB("minecraft:melon_stem", resource, "age", 8);
    }

    private static void registerMetadataRGB(String blockName, ResourceLocation resource, String property, int length) {
        int[] rgb = MCPatcherUtils.getImageRGB(TexturePackAPI.getImage(resource));
        if (rgb == null || rgb.length < length) {
            return;
        }
        for (int i = 0; i < length; i++) {
            IColorMap colorMap = new ColorMap.Fixed(rgb[i] & 0xffffff);
            String idList = String.format("%s:%d,%d:%s=%d", blockName, i, (i + length) & 0xf, property, i);
            registerColorMap(colorMap, resource, idList);
        }
    }

    static List<BlockStateMatcher> findColorMaps(Block block) {
        return blockColorMaps.get(block);
    }

    static IColorMap getThreadLocal(BlockStateMatcher matcher) {
        IColorMap newMap = (IColorMap) matcher.getThreadData();
        if (newMap == null) {
            IColorMap oldMap = (IColorMap) matcher.getData();
            newMap = oldMap.copy();
            matcher.setThreadData(newMap);
        }
        return newMap;
    }

    private static IColorMap findColorMap(Block block, int metadata) {
        List<BlockStateMatcher> maps = findColorMaps(block);
        if (maps != null) {
            for (BlockStateMatcher matcher : maps) {
                if (matcher.match(block, metadata)) {
                    return getThreadLocal(matcher);
                }
            }
        }
        return null;
    }

    private static IColorMap findColorMap(Block block, IBlockAccess blockAccess, int x, int y, int z) {
        List<BlockStateMatcher> maps = findColorMaps(block);
        if (maps != null) {
            for (BlockStateMatcher matcher : maps) {
                if (matcher.match(blockAccess, x, y, z)) {
                    return getThreadLocal(matcher);
                }
            }
        }
        return null;
    }

    public static boolean colorizeBlock(Block block) {
        return colorizeBlock(block, 16);
    }

    public static boolean colorizeBlock(Block block, int metadata) {
        IColorMap colorMap = findColorMap(block, metadata);
        if (colorMap == null) {
            RenderBlocksUtils.setupColorMultiplier(block, false);
            return false;
        } else {
            RenderBlocksUtils.setupColorMultiplier(block, true);
            blockColor = colorMap.getColorMultiplier();
            return true;
        }
    }

    public static boolean colorizeBlock(Block block, IBlockAccess blockAccess, int x, int y, int z) {
        IColorMap colorMap = findColorMap(block, blockAccess, x, y, z);
        return colorizeBlock(blockAccess, colorMap, x, y, z);
    }

    private static boolean colorizeBlock(IBlockAccess blockAccess, IColorMap colorMap, int x, int y, int z) {
        if (colorMap == null) {
            return false;
        } else {
            blockColor = colorMap.getColorMultiplier(blockAccess, x, y, z);
            return true;
        }
    }

    public static void computeWaterColor() {
        if (waterColorMap != null) {
            Colorizer.setColorF(waterColorMap.getColorMultiplier());
        }
    }

    public static boolean computeWaterColor(boolean includeBaseColor, int x, int y, int z) {
        if (waterColorMap == null) {
            return false;
        } else {
            Colorizer.setColorF(waterColorMap.getColorMultiplierF(BiomeAPI.getWorld(), x, y, z));
            if (includeBaseColor) {
                Colorizer.setColor[0] *= ColorizeEntity.waterBaseColor[0];
                Colorizer.setColor[1] *= ColorizeEntity.waterBaseColor[1];
                Colorizer.setColor[2] *= ColorizeEntity.waterBaseColor[2];
            }
            return true;
        }
    }

    public static void colorizeWaterBlockGL(Block block) {
        if (block == Blocks.flowing_water || block == Blocks.water) {
            float[] waterColor;
            if (waterColorMap == null) {
                waterColor = ColorizeEntity.waterBaseColor;
            } else {
                waterColor = new float[3];
                ColorUtils.intToFloat3(waterColorMap.getColorMultiplier(), waterColor);
            }
            glColor4f(waterColor[0], waterColor[1], waterColor[2], 1.0f);
        }
    }

    public static boolean computeRedstoneWireColor(int current) {
        if (redstoneColor == null) {
            return false;
        } else {
            System.arraycopy(redstoneColor[current & 0xf], 0, Colorizer.setColor, 0, 3);
            return true;
        }
    }

    public static int colorizeRedstoneWire(IBlockAccess blockAccess, int x, int y, int z, int defaultColor) {
        if (redstoneColor == null) {
            return defaultColor;
        } else {
            int metadata = Math.max(Math.min(blockAccess.getBlockMetadata(x, y, z), 15), 0);
            return ColorUtils.float3ToInt(redstoneColor[metadata]);
        }
    }

    private static float[] getVertexColor(IBlockAccess blockAccess, IColorMap colorMap, int x, int y, int z,
        int[] offsets) {
        if (enableTestColorSmoothing) {
            int rgb = 0;
            if ((x + offsets[0]) % 2 == 0) {
                rgb |= 0xff0000;
            }
            if ((y + offsets[1]) % 2 == 0) {
                rgb |= 0x00ff00;
            }
            if ((z + offsets[2]) % 2 == 0) {
                rgb |= 0x0000ff;
            }
            ColorUtils.intToFloat3(rgb, Colorizer.setColor);
            return Colorizer.setColor;
        } else {
            return colorMap.getColorMultiplierF(blockAccess, x + offsets[0], y + offsets[1], z + offsets[2]);
        }
    }

    // Called by asm
    @SuppressWarnings("unused")
    public static boolean setupBlockSmoothing(RenderBlocks renderBlocks, Block block, IBlockAccess blockAccess, int x,
        int y, int z, int face, float topLeft, float bottomLeft, float bottomRight, float topRight) {
        return RenderBlocksUtils.useColorMultiplier(face) && setupBiomeSmoothing(
            renderBlocks,
            block,
            blockAccess,
            x,
            y,
            z,
            face,
            true,
            topLeft,
            bottomLeft,
            bottomRight,
            topRight);
    }

    // TODO: remove
    @Deprecated
    public static boolean setupBlockSmoothingGrassSide(RenderBlocks renderBlocks, Block block, IBlockAccess blockAccess,
        int x, int y, int z, int face, float topLeft, float bottomLeft, float bottomRight, float topRight) {
        return checkBiomeSmoothing(block, face) && setupBiomeSmoothing(
            renderBlocks,
            block,
            blockAccess,
            x,
            y,
            z,
            face,
            true,
            topLeft,
            bottomLeft,
            bottomRight,
            topRight);
    }

    public static boolean setupBlockSmoothing(RenderBlocks renderBlocks, Block block, IBlockAccess blockAccess, int x,
        int y, int z, int face) {
        return checkBiomeSmoothing(block, face)
            && setupBiomeSmoothing(renderBlocks, block, blockAccess, x, y, z, face, true, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static boolean checkBiomeSmoothing(Block block, int face) {
        return enableSmoothBiomes && face >= 0
            && RenderBlocksUtils.isAmbientOcclusionEnabled()
            && block.getLightValue() == 0;
    }

    private static boolean setupBiomeSmoothing(RenderBlocks renderBlocks, Block block, IBlockAccess blockAccess, int x,
        int y, int z, int face, boolean useAO, float topLeft, float bottomLeft, float bottomRight, float topRight) {
        if (!setupBlockSmoothing(block, blockAccess, x, y, z, face)) {
            return false;
        }

        if (useAO) {
            float aoBase = RenderBlocksUtils.AO_BASE[face % 6];
            topLeft *= aoBase;
            bottomLeft *= aoBase;
            bottomRight *= aoBase;
            topRight *= aoBase;
        }

        renderBlocks.colorRedTopLeft = topLeft * colorRedTopLeft;
        renderBlocks.colorGreenTopLeft = topLeft * colorGreenTopLeft;
        renderBlocks.colorBlueTopLeft = topLeft * colorBlueTopLeft;

        renderBlocks.colorRedBottomLeft = bottomLeft * colorRedBottomLeft;
        renderBlocks.colorGreenBottomLeft = bottomLeft * colorGreenBottomLeft;
        renderBlocks.colorBlueBottomLeft = bottomLeft * colorBlueBottomLeft;

        renderBlocks.colorRedBottomRight = bottomRight * colorRedBottomRight;
        renderBlocks.colorGreenBottomRight = bottomRight * colorGreenBottomRight;
        renderBlocks.colorBlueBottomRight = bottomRight * colorBlueBottomRight;

        renderBlocks.colorRedTopRight = topRight * colorRedTopRight;
        renderBlocks.colorGreenTopRight = topRight * colorGreenTopRight;
        renderBlocks.colorBlueTopRight = topRight * colorBlueTopRight;

        return true;
    }

    public static void setupBlockSmoothing(Block block, IBlockAccess blockAccess, int x, int y, int z, int face,
        float r, float g, float b) {
        if (!setupBlockSmoothing(block, blockAccess, x, y, z, face)) {
            setVertexColors(r, g, b);
        }
    }

    private static boolean setupBlockSmoothing(Block block, IBlockAccess blockAccess, int x, int y, int z, int face) {
        if (!checkBiomeSmoothing(block, face)) {
            return false;
        }
        IColorMap colorMap = findColorMap(block, blockAccess, x, y, z);
        if (colorMap == null) {
            return false;
        }

        int[][] offsets = FACE_VERTICES[face];
        float[] color;

        color = getVertexColor(blockAccess, colorMap, x, y, z, offsets[0]);
        colorRedTopLeft = color[0];
        colorGreenTopLeft = color[1];
        colorBlueTopLeft = color[2];

        color = getVertexColor(blockAccess, colorMap, x, y, z, offsets[1]);
        colorRedBottomLeft = color[0];
        colorGreenBottomLeft = color[1];
        colorBlueBottomLeft = color[2];

        color = getVertexColor(blockAccess, colorMap, x, y, z, offsets[2]);
        colorRedBottomRight = color[0];
        colorGreenBottomRight = color[1];
        colorBlueBottomRight = color[2];

        color = getVertexColor(blockAccess, colorMap, x, y, z, offsets[3]);
        colorRedTopRight = color[0];
        colorGreenTopRight = color[1];
        colorBlueTopRight = color[2];

        return true;
    }

    private static void resetVertexColors() {
        setVertexColors(1.0f, 1.0f, 1.0f);
    }

    private static void setVertexColors(float r, float g, float b) {
        colorRedTopLeft = colorRedBottomLeft = colorRedBottomRight = colorRedTopRight = r;
        colorGreenTopLeft = colorGreenBottomLeft = colorGreenBottomRight = colorGreenTopRight = g;
        colorBlueTopLeft = colorBlueBottomLeft = colorBlueBottomRight = colorBlueTopRight = b;
    }

}
