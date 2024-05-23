package com.prupe.mcpatcher.ctm;

import static com.prupe.mcpatcher.ctm.RenderBlockState.CONNECT_BY_BLOCK;
import static com.prupe.mcpatcher.ctm.RenderBlockState.CONNECT_BY_MATERIAL;
import static com.prupe.mcpatcher.ctm.RenderBlockState.CONNECT_BY_TILE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.NORMALS;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.biome.BiomeAPI;
import com.prupe.mcpatcher.mal.block.BlockAPI;
import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import com.prupe.mcpatcher.mal.block.RenderPassAPI;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.tile.TileLoader;

abstract class TileOverride implements Comparable<TileOverride> {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CONNECTED_TEXTURES, "CTM");

    private static final int META_MASK = 0xffff;

    private final PropertiesFile properties;
    private final String baseFilename;
    private final TileLoader tileLoader;
    private final int renderPass;
    private final int weight;
    private final List<BlockStateMatcher> matchBlocks;
    private final Set<String> matchTiles;
    private final int defaultMetaMask;
    private final BlockFaceMatcher faceMatcher;
    private final int connectType;
    private final boolean innerSeams;
    private final BitSet biomes;
    private final BitSet height;

    private final List<ResourceLocation> tileNames = new ArrayList<>();
    protected IIcon[] icons;
    private int matchMetadata = META_MASK;

    static TileOverride create(ResourceLocation propertiesFile, TileLoader tileLoader) {
        if (propertiesFile == null) {
            return null;
        }
        PropertiesFile properties = PropertiesFile.get(logger, propertiesFile);
        if (properties == null) {
            return null;
        }

        String method = properties.getString("method", "default")
            .toLowerCase();
        TileOverride override = null;

        switch (method) {
            case "default", "glass", "ctm" -> override = new TileOverrideImpl.CTM(properties, tileLoader);
            case "random" -> {
                override = new TileOverrideImpl.Random1(properties, tileLoader);
                if (override.getNumberOfTiles() == 1) {
                    override = new TileOverrideImpl.Fixed(properties, tileLoader);
                }
            }
            case "fixed", "static" -> override = new TileOverrideImpl.Fixed(properties, tileLoader);
            case "bookshelf", "horizontal" -> override = new TileOverrideImpl.Horizontal(properties, tileLoader);
            case "horizontal+vertical", "h+v" -> override = new TileOverrideImpl.HorizontalVertical(
                properties,
                tileLoader);
            case "vertical" -> override = new TileOverrideImpl.Vertical(properties, tileLoader);
            case "vertical+horizontal", "v+h" -> override = new TileOverrideImpl.VerticalHorizontal(
                properties,
                tileLoader);
            case "sandstone", "top" -> override = new TileOverrideImpl.Top(properties, tileLoader);
            case "repeat", "pattern" -> override = new TileOverrideImpl.Repeat(properties, tileLoader);
            default -> properties.error("unknown method \"%s\"", method);
        }

        if (override != null && !properties.valid()) {
            String status = override.checkTileMap();
            if (status != null) {
                override.properties.error("invalid %s tile map: %s", override.getMethod(), status);
            }
        }

        return override == null || override.isDisabled() ? null : override;
    }

    protected TileOverride(PropertiesFile properties, TileLoader tileLoader) {
        this.properties = properties;
        String texturesDirectory = properties.getResource()
            .getResourcePath()
            .replaceFirst("/[^/]*$", "");
        baseFilename = properties.getResource()
            .getResourcePath()
            .replaceFirst(".*/", "")
            .replaceFirst("\\.properties$", "");
        this.tileLoader = tileLoader;

        String renderPassStr = properties.getString("renderPass", "");
        renderPass = RenderPassAPI.instance.parseRenderPass(renderPassStr);
        if (renderPassStr.matches("\\d+") && renderPass >= 0 && renderPass <= RenderPassAPI.MAX_EXTRA_RENDER_PASS) {
            properties.warning(
                "renderPass=%s is deprecated, use renderPass=%s instead",
                renderPassStr,
                RenderPassAPI.instance.getRenderPassName(renderPass));
        }

        loadIcons();
        if (tileNames.isEmpty()) {
            properties.error("no images found in %s/", texturesDirectory);
        }

        String value;
        if (baseFilename.matches("block\\d+.*")) {
            value = baseFilename.replaceFirst("block(\\d+).*", "$1");
        } else {
            value = "";
        }
        matchBlocks = getBlockList(properties.getString("matchBlocks", value), properties.getString("metadata", ""));
        matchTiles = getTileList("matchTiles");
        if (matchBlocks.isEmpty() && matchTiles.isEmpty()) {
            matchTiles.add(baseFilename);
        }
        int bits = 0;
        for (int i : properties.getIntList("metadata", 0, 15, "0-15")) {
            bits |= 1 << i;
        }
        defaultMetaMask = bits;

        faceMatcher = BlockFaceMatcher.create(properties.getString("faces", ""));

        String connectType1 = properties.getString("connect", "")
            .toLowerCase();
        switch (connectType1) {
            case "" -> connectType = matchTiles.isEmpty() ? CONNECT_BY_BLOCK : CONNECT_BY_TILE;
            case "block" -> connectType = CONNECT_BY_BLOCK;
            case "tile" -> connectType = CONNECT_BY_TILE;
            case "material" -> connectType = CONNECT_BY_MATERIAL;
            default -> {
                properties.error("invalid connect type %s", connectType1);
                connectType = CONNECT_BY_BLOCK;
            }
        }

        innerSeams = properties.getBoolean("innerSeams", false);

        String biomeList = properties.getString("biomes", "");
        if (biomeList.isEmpty()) {
            biomes = null;
        } else {
            biomes = new BitSet();
            BiomeAPI.parseBiomeList(biomeList, biomes);
        }

        height = BiomeAPI.getHeightListProperty(properties, "");

        if (renderPass > RenderPassAPI.MAX_EXTRA_RENDER_PASS) {
            properties.error("invalid renderPass %s", renderPassStr);
        } else if (renderPass >= 0 && !matchTiles.isEmpty()) {
            properties.error(
                "renderPass=%s must be block-based not tile-based",
                RenderPassAPI.instance.getRenderPassName(renderPass));
        }

        weight = properties.getInt("weight", 0);
    }

    private boolean addIcon(ResourceLocation resource) {
        tileNames.add(resource);
        return tileLoader.preloadTile(resource, renderPass > RenderPassAPI.MAX_BASE_RENDER_PASS);
    }

    private void loadIcons() {
        tileNames.clear();
        String tileList = properties.getString("tiles", "");
        ResourceLocation blankResource = RenderPassAPI.instance.getBlankResource(renderPass);
        if (tileList.isEmpty()) {
            for (int i = 0;; i++) {
                ResourceLocation resource = TileLoader
                    .parseTileAddress(properties.getResource(), String.valueOf(i), blankResource);
                if (!TexturePackAPI.hasResource(resource)) {
                    break;
                }
                if (!addIcon(resource)) {
                    break;
                }
            }
        } else {
            Pattern range = Pattern.compile("(\\d+)-(\\d+)");
            for (String token : tileList.split("\\s+")) {
                Matcher matcher = range.matcher(token);
                if (token.isEmpty()) {
                    // nothing
                } else if (matcher.matches()) {
                    try {
                        int from = Integer.parseInt(matcher.group(1));
                        int to = Integer.parseInt(matcher.group(2));
                        for (int i = from; i <= to; i++) {
                            ResourceLocation resource = TileLoader
                                .parseTileAddress(properties.getResource(), String.valueOf(i), blankResource);
                            if (TexturePackAPI.hasResource(resource)) {
                                addIcon(resource);
                            } else {
                                properties.warning("could not find image %s", resource);
                                tileNames.add(null);
                            }
                        }
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                } else {
                    ResourceLocation resource = TileLoader
                        .parseTileAddress(properties.getResource(), token, blankResource);
                    if (resource == null) {
                        tileNames.add(null);
                    } else if (TexturePackAPI.hasResource(resource)) {
                        addIcon(resource);
                    } else {
                        properties.warning("could not find image %s", resource);
                        tileNames.add(null);
                    }
                }
            }
        }
    }

    private List<BlockStateMatcher> getBlockList(String property, String defaultMetadata) {
        List<BlockStateMatcher> blocks = new ArrayList<>();
        if (!MCPatcherUtils.isNullOrEmpty(defaultMetadata)) {
            defaultMetadata = ':' + defaultMetadata;
        }
        for (String token : property.split("\\s+")) {
            if (token.isEmpty()) {
                // nothing
            } else if (token.matches("\\d+-\\d+")) {
                for (int id : MCPatcherUtils.parseIntegerList(token, 0, 65535)) {
                    BlockStateMatcher matcher = BlockAPI.createMatcher(properties, id + defaultMetadata);
                    if (matcher == null) {
                        properties.warning("unknown block id %d", id);
                    } else {
                        blocks.add(matcher);
                    }
                }
            } else {
                BlockStateMatcher matcher = BlockAPI.createMatcher(properties, token + defaultMetadata);
                if (matcher == null) {
                    properties.warning("unknown block %s", token);
                } else {
                    blocks.add(matcher);
                }
            }
        }
        for (BlockStateMatcher matcher : blocks) {
            matcher.setData(this);
        }
        return blocks;
    }

    private Set<String> getTileList(String key) {
        Set<String> list = new HashSet<>();
        String property = properties.getString(key, "");
        for (String token : property.split("\\s+")) {
            if (token.isEmpty()) {
                // nothing
            } else if (token.contains("/")) {
                if (!token.endsWith(".png")) {
                    token += ".png";
                }
                ResourceLocation resource = TexturePackAPI.parseResourceLocation(properties.getResource(), token);
                if (resource != null) {
                    list.add(resource.toString());
                }
            } else {
                list.add(token);
            }
        }
        return list;
    }

    protected int getNumberOfTiles() {
        return tileNames.size();
    }

    String checkTileMap() {
        return null;
    }

    boolean requiresFace() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s[%s] (%d tiles)", getMethod(), properties, getNumberOfTiles());
    }

    public final void registerIcons() {
        icons = new IIcon[tileNames.size()];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = tileLoader.getIcon(tileNames.get(i));
        }
    }

    final public boolean isDisabled() {
        return !properties.valid();
    }

    final public List<BlockStateMatcher> getMatchingBlocks() {
        return matchBlocks;
    }

    final public Set<String> getMatchingTiles() {
        if (MCPatcherUtils.isNullOrEmpty(matchTiles)) {
            return null;
        } else {
            return new HashSet<>(matchTiles);
        }
    }

    final public int getRenderPass() {
        return renderPass;
    }

    final public int getWeight() {
        return weight;
    }

    @Override
    public int compareTo(TileOverride o) {
        int result = o.getWeight() - getWeight();
        if (result != 0) {
            return result;
        }
        if (o instanceof TileOverride) {
            return baseFilename.compareTo(((TileOverride) o).baseFilename);
        } else {
            return -1;
        }
    }

    final boolean shouldConnect(RenderBlockState renderBlockState, IIcon icon, int relativeDirection) {
        return shouldConnect(
            renderBlockState,
            icon,
            renderBlockState.getOffset(renderBlockState.getBlockFace(), relativeDirection));
    }

    final boolean shouldConnect(RenderBlockState renderBlockState, IIcon icon, int blockFace, int relativeDirection) {
        return shouldConnect(renderBlockState, icon, renderBlockState.getOffset(blockFace, relativeDirection));
    }

    private boolean shouldConnect(RenderBlockState renderBlockState, IIcon icon, int[] offset) {
        IBlockAccess blockAccess = renderBlockState.getBlockAccess();
        Block block = renderBlockState.getBlock();
        int x = renderBlockState.getX();
        int y = renderBlockState.getY();
        int z = renderBlockState.getZ();
        x += offset[0];
        y += offset[1];
        z += offset[2];
        Block neighbor = blockAccess.getBlock(x, y, z);
        if (neighbor == null) {
            return false;
        }
        if (block == neighbor) {
            BlockStateMatcher filter = renderBlockState.getFilter();
            if (filter != null && !filter.match(blockAccess, x, y, z)) {
                return false;
            }
        }
        if (innerSeams) {
            int blockFace = renderBlockState.getBlockFace();
            if (blockFace >= 0) {
                int[] normal = NORMALS[blockFace];
                if (!neighbor.shouldSideBeRendered(
                    blockAccess,
                    x + normal[0],
                    y + normal[1],
                    z + normal[2],
                    blockFace)) {
                    return false;
                }
            }
        }
        return switch (connectType) {
            case CONNECT_BY_TILE -> renderBlockState.shouldConnectByTile(neighbor, icon, x, y, z);
            case CONNECT_BY_BLOCK -> renderBlockState.shouldConnectByBlock(neighbor, x, y, z);
            case CONNECT_BY_MATERIAL -> block.blockMaterial == neighbor.blockMaterial;
            default -> false;
        };
    }

    public final IIcon getTileWorld(RenderBlockState renderBlockState, IIcon origIcon) {
        if (icons == null) {
            properties.error("no images loaded, disabling");
            return null;
        }
        IBlockAccess blockAccess = renderBlockState.getBlockAccess();
        Block block = renderBlockState.getBlock();
        int x = renderBlockState.getX();
        int y = renderBlockState.getY();
        int z = renderBlockState.getZ();
        if (renderBlockState.getBlockFace() < 0 && requiresFace()) {
            properties.warning(
                "method=%s is not supported for non-standard block %s:%d @ %d %d %d",
                getMethod(),
                BlockAPI.getBlockName(block),
                blockAccess.getBlockMetadata(x, y, z),
                x,
                y,
                z);
            return null;
        }
        if (block == null || RenderPassAPI.instance.skipThisRenderPass(block, renderPass)) {
            return null;
        }
        BlockStateMatcher filter = renderBlockState.getFilter();
        if (filter != null && !filter.match(blockAccess, x, y, z)) {
            return null;
        }
        if (faceMatcher != null && !faceMatcher.match(renderBlockState)) {
            return null;
        }
        if (height != null && !height.get(y)) {
            return null;
        }
        if (biomes != null && !biomes.get(BiomeAPI.getBiomeIDAt(blockAccess, x, y, z))) {
            return null;
        }
        return getTileWorld_Impl(renderBlockState, origIcon);
    }

    public final IIcon getTileHeld(RenderBlockState renderBlockState, IIcon origIcon) {
        if (icons == null) {
            properties.error("no images loaded, disabling");
            return null;
        }
        Block block = renderBlockState.getBlock();
        if (block == null || RenderPassAPI.instance.skipThisRenderPass(block, renderPass)) {
            return null;
        }
        int face = renderBlockState.getTextureFace();
        if (face < 0 && requiresFace()) {
            properties.warning("method=%s is not supported for non-standard block %s", getMethod(), renderBlockState);
            return null;
        }
        if (height != null || biomes != null) {
            return null;
        }
        if (faceMatcher != null && !faceMatcher.match(renderBlockState)) {
            return null;
        }
        return getTileHeld_Impl(renderBlockState, origIcon);
    }

    abstract String getMethod();

    abstract IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon);

    abstract IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon);
}
