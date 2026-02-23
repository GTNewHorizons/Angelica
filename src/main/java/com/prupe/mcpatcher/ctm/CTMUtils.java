package com.prupe.mcpatcher.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.block.BlockAPI;
import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import com.prupe.mcpatcher.mal.block.RenderBlocksUtils;
import com.prupe.mcpatcher.mal.block.RenderPassAPI;
import com.prupe.mcpatcher.mal.resource.BlendMethod;
import com.prupe.mcpatcher.mal.resource.ResourceList;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;
import com.prupe.mcpatcher.mal.tile.TileLoader;

import jss.notfine.config.MCPatcherForgeConfig;

public class CTMUtils {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CONNECTED_TEXTURES, "CTM");

    private static class Overrides {
        private final List<TileOverride> all = new ArrayList<>();
        private final Map<Block, List<BlockStateMatcher>> block = new IdentityHashMap<>();
        private final Map<String, List<TileOverride>> tile = new HashMap<>();
    }

    private static Overrides overrides = new Overrides();
    private static Overrides newOverrides = null;
    private static TileLoader tileLoader;

    private static final StampedLock lock = new StampedLock();

    private static class StateAndIterator {
        private final TileOverrideIterator.IJK ijkIterator = CTMUtils.newIJKIterator();
        private final TileOverrideIterator.Metadata metadataIterator = CTMUtils.newMetadataIterator();
        private final BlockOrientation renderBlockState = new BlockOrientation();
        private final int texturePackChangeCounter;

        public StateAndIterator() {
            this.texturePackChangeCounter = 0;
        }

        public StateAndIterator(int texturePackChangeCounter) {
            this.texturePackChangeCounter = texturePackChangeCounter;
        }
    }

    private static final ThreadLocal<StateAndIterator> stateAndIterator = ThreadLocal.withInitial(StateAndIterator::new);

    private static final AtomicInteger texturePackChangeCounter = new AtomicInteger(0);

    static {
        try {
            Class.forName(MCPatcherUtils.RENDER_PASS_CLASS)
                .getMethod("finish")
                .invoke(null);
        } catch (Exception ignore) {}

        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.CONNECTED_TEXTURES, 3) {
            @Override
            public void initialize() {
            }

            @Override
            public void beforeChange() {
                long stamp = lock.writeLock();
                try {
                    RenderPassAPI.instance.clear();
                    try {
                        GlassPaneRenderer.clear();
                    } catch (NoClassDefFoundError e) {
                        // nothing
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    RenderBlocksUtils.blankIcon = null;
                    tileLoader = new TileLoader("textures/blocks", logger);
                    RenderPassAPI.instance.refreshBlendingOptions();

                    logger.info("loading CTM overrides");
                    newOverrides = new Overrides();
                    if (MCPatcherForgeConfig.ConnectedTextures.standard || MCPatcherForgeConfig.ConnectedTextures.nonStandard) {
                        for (ResourceLocation resource : ResourceList.getInstance()
                            .listResources(TexturePackAPI.MCPATCHER_SUBDIR + "ctm", ".properties", true)) {
                            registerOverrideWithoutLock(newOverrides, TileOverride.create(resource, tileLoader));
                        }
                    }
                    for (ResourceLocation resource : BlendMethod.getAllBlankResources()) {
                        tileLoader.preloadTile(resource, false);
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
            }

            @Override
            public void afterChange() {
                if (newOverrides == null) {
                    logger.error("CTM overrides were not initialized during texture pack change");
                    logger.error("Maybe beforeChange was not called, or threw an exception?");
                    return;
                }
                long stamp = lock.writeLock();
                try {
                    for (TileOverride override : newOverrides.all) {
                        override.registerIcons();
                    }
                    for (Map.Entry<Block, List<BlockStateMatcher>> entry : newOverrides.block.entrySet()) {
                        for (BlockStateMatcher matcher : entry.getValue()) {
                            TileOverride override = (TileOverride) matcher.getData();
                            if (override.getRenderPass() >= 0) {
                                RenderPassAPI.instance.setRenderPassForBlock(entry.getKey(), override.getRenderPass());
                            }
                        }
                    }
                    for (List<BlockStateMatcher> overrides : newOverrides.block.values()) {
                        overrides.sort((m1, m2) -> {
                            TileOverride o1 = (TileOverride) m1.getData();
                            TileOverride o2 = (TileOverride) m2.getData();
                            return o1.compareTo(o2);
                        });
                    }
                    for (List<TileOverride> overrides : newOverrides.tile.values()) {
                        Collections.sort(overrides);
                    }
                    setBlankResourceWithoutLock();
                    overrides = newOverrides;
                    newOverrides = null;
                    texturePackChangeCounter.getAndIncrement();
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
        });
    }

    private static StateAndIterator getStateAndIterator() {
        int globalTexturePackChangeCounter = texturePackChangeCounter.get();
        StateAndIterator local = stateAndIterator.get();
        if (local.texturePackChangeCounter < globalTexturePackChangeCounter) {
            local = new StateAndIterator(globalTexturePackChangeCounter);
            stateAndIterator.set(local);
        }
        return local;
    }

    private static IIcon getBlockIconWithoutLock(IIcon icon, Block block, IBlockAccess blockAccess,
                                                 int x, int y, int z, int face) {
        StateAndIterator local = getStateAndIterator();
        if (blockAccess != null && checkFace(face)) {
            local.renderBlockState.setBlock(block, blockAccess, x, y, z);
            local.renderBlockState.setFace(face);
            TileOverride lastOverride = local.ijkIterator.go(local.renderBlockState, icon);
            if (lastOverride != null) {
                return skipDefaultRendering(block) ? RenderBlocksUtils.blankIcon : local.ijkIterator.getIcon();
            }
        }
        return skipDefaultRendering(block) ? RenderBlocksUtils.blankIcon : icon;
    }

    private static IIcon getBlockIconWithoutLock(IIcon icon, Block block, int face, int metadata) {
        StateAndIterator local = getStateAndIterator();
        if (checkFace(face) && checkRenderType(block)) {
            local.renderBlockState.setBlockMetadata(block, metadata, face);
            TileOverride lastOverride = local.metadataIterator.go(local.renderBlockState, icon);
            if (lastOverride != null) {
                return local.metadataIterator.getIcon();
            }
        }
        return icon;
    }

    public static IIcon getBlockIcon(IIcon icon, Block block, IBlockAccess blockAccess,
                                     int x, int y, int z, int face) {
        long stamp = lock.tryOptimisticRead();
        IIcon value = getBlockIconWithoutLock(icon, block, blockAccess, x, y, z, face);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                value = getBlockIconWithoutLock(icon, block, blockAccess, x, y, z, face);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }

    public static IIcon getBlockIcon(IIcon icon, Block block, int face, int metadata) {
        long stamp = lock.tryOptimisticRead();
        IIcon value = getBlockIconWithoutLock(icon, block, face, metadata);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                value = getBlockIconWithoutLock(icon, block, face, metadata);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return value;
    }

    public static IIcon getBlockIcon(IIcon icon, Block block, int face) {
        return getBlockIcon(icon, block, face, 0);
    }

    public static void reset() {
    }

    private static boolean checkFace(int face) {
        return face < 0 ? MCPatcherForgeConfig.ConnectedTextures.nonStandard : MCPatcherForgeConfig.ConnectedTextures.standard;
    }

    private static boolean checkRenderType(Block block) {
        return switch (block.getRenderType()) {
            case 11, 21 -> // fence, fence gate
                false;
            default -> true;
        };
    }

    private static boolean skipDefaultRendering(Block block) {
        return RenderPassAPI.instance.skipDefaultRendering(block);
    }

    private static void registerOverrideWithoutLock(Overrides overrides, TileOverride override) {
        if (override != null && !override.isDisabled()) {
            boolean registered = false;
            List<BlockStateMatcher> matchingBlocks = override.getMatchingBlocks();
            if (!MCPatcherUtils.isNullOrEmpty(matchingBlocks)) {
                for (BlockStateMatcher matcher : matchingBlocks) {
                    if (matcher == null) {
                        continue;
                    }
                    Block block = matcher.getBlock();
                    List<BlockStateMatcher> list = overrides.block.computeIfAbsent(block, k -> new ArrayList<>());
                    list.add(matcher);
                    logger.fine("using %s for block %s", override, BlockAPI.getBlockName(block));
                    registered = true;
                }
            }
            Set<String> matchingTiles = override.getMatchingTiles();
            if (!MCPatcherUtils.isNullOrEmpty(matchingTiles)) {
                for (String name : matchingTiles) {
                    List<TileOverride> list = overrides.tile.computeIfAbsent(name, k -> new ArrayList<>());
                    list.add(override);
                    logger.fine("using %s for tile %s", override, name);
                    registered = true;
                }
            }
            if (registered) {
                overrides.all.add(override);
            }
        }
    }

    private static void setBlankResourceWithoutLock() {
        RenderBlocksUtils.blankIcon = tileLoader.getIcon(RenderPassAPI.instance.getBlankResource());
    }

    public static void setBlankResource() {
        long stamp = lock.writeLock();
        try {
            setBlankResourceWithoutLock();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private static TileOverrideIterator.IJK newIJKIterator() {
        return new TileOverrideIterator.IJK(overrides.block, overrides.tile);
    }

    private static TileOverrideIterator.Metadata newMetadataIterator() {
        return new TileOverrideIterator.Metadata(overrides.block, overrides.tile);
    }
}
