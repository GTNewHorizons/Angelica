package com.prupe.mcpatcher.ctm;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

import com.prupe.mcpatcher.mal.block.BlockStateMatcher;
import com.gtnewhorizons.angelica.rendering.celeritas.threading.RenderPassHelper;

import jss.notfine.config.MCPatcherForgeConfig;

abstract public class TileOverrideIterator implements Iterator<TileOverride> {

    private final Map<Block, List<BlockStateMatcher>> allBlockOverrides;
    private final Map<String, List<TileOverride>> allTileOverrides;

    protected IIcon currentIcon;

    private List<BlockStateMatcher> blockOverrides;
    private List<TileOverride> tileOverrides;
    private final Set<TileOverride> skipOverrides = new HashSet<>();

    private RenderBlockState renderBlockState;
    private int blockPos;
    private int iconPos;
    private boolean foundNext;
    private TileOverride nextOverride;
    private TileOverride lastMatchedOverride;

    protected TileOverrideIterator(Map<Block, List<BlockStateMatcher>> allBlockOverrides,
        Map<String, List<TileOverride>> allTileOverrides) {
        this.allBlockOverrides = allBlockOverrides;
        this.allTileOverrides = allTileOverrides;
    }

     void clear() {
        currentIcon = null;
        blockOverrides = null;
        tileOverrides = null;
        nextOverride = null;
        lastMatchedOverride = null;
        skipOverrides.clear();
    }

    private void resetForNextPass() {
        blockOverrides = null;
        tileOverrides = allTileOverrides.get(currentIcon.getIconName());
        blockPos = 0;
        iconPos = 0;
        foundNext = false;
    }

    @Override
    public boolean hasNext() {
        if (foundNext) {
            return true;
        }
        if (tileOverrides != null) {
            while (iconPos < tileOverrides.size()) {
                if (checkOverride(tileOverrides.get(iconPos++))) {
                    renderBlockState.setFilter(null);
                    return true;
                }
            }
        }
        if (blockOverrides != null) {
            while (blockPos < blockOverrides.size()) {
                BlockStateMatcher matcher = blockOverrides.get(blockPos++);
                if (renderBlockState.match(matcher) && checkOverride((TileOverride) matcher.getData())) {
                    renderBlockState.setFilter(matcher);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public TileOverride next() {
        if (!foundNext) {
            throw new IllegalStateException("next called before hasNext() == true");
        }
        foundNext = false;
        return nextOverride;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove not supported");
    }

    private boolean checkOverride(TileOverride override) {
        if (override != null && !override.isDisabled() && !skipOverrides.contains(override)) {
            foundNext = true;
            nextOverride = override;
            return true;
        } else {
            return false;
        }
    }

    public TileOverride go(RenderBlockState renderBlockState, IIcon origIcon) {
        CTMUtils.clearCurrentCompact();
        this.renderBlockState = renderBlockState;
        renderBlockState.setFilter(null);
        currentIcon = origIcon;
        blockOverrides = allBlockOverrides.get(renderBlockState.getBlock());
        tileOverrides = allTileOverrides.get(origIcon.getIconName());
        blockPos = 0;
        iconPos = 0;
        foundNext = false;
        nextOverride = null;
        lastMatchedOverride = null;
        skipOverrides.clear();

        passLoop: for (int pass = 0; pass < MCPatcherForgeConfig.ConnectedTextures.maxRecursion; pass++) {
            while (hasNext()) {
                TileOverride override = next();
                int worldRenderPass = RenderPassHelper.getWorldRenderPass();
                if(!(override.getRenderPass() == -1 || override.getRenderPass() == worldRenderPass)) {
                    continue;
                }
                IIcon newIcon = getTile(override, renderBlockState, origIcon);
                if (newIcon != null) {
                    if (override instanceof TileOverrideImpl.CTMCompact compact) {
                            if (worldRenderPass == 0) {
                                CTMUtils.setCurrentCompact(new TileOverrideImpl.CTMCompact[] {compact}, renderBlockState);
                            }  else {
                            CTMUtils.setCurrentCompact(
                                collectCompactLayers(compact, renderBlockState, origIcon, worldRenderPass),
                                renderBlockState);
                            }
                    }
                    lastMatchedOverride = override;
                    skipOverrides.add(override);
                    currentIcon = newIcon;
                    resetForNextPass();
                    continue passLoop;
                }
            }
            break;
        }
        return lastMatchedOverride;
    }
    private TileOverrideImpl.CTMCompact[] collectCompactLayers(TileOverrideImpl.CTMCompact primary,
        RenderBlockState renderBlockState, IIcon origIcon, int worldRenderPass) {
        Int2ObjectSortedMap<TileOverrideImpl.CTMCompact> layers = new Int2ObjectAVLTreeMap<>();
        layers.putIfAbsent(primary.getRenderLayer(), primary);
        BlockStateMatcher primaryFilter = renderBlockState.getFilter();
        Int2ObjectMap<List<BlockStateMatcher>> layeredCandidates = CTMUtils.getCompactLayers(renderBlockState.getBlock());
        if(layeredCandidates==null) {
            return new TileOverrideImpl.CTMCompact[] {compact};
        }

        try {
            LayerLoop: for (Map.Entry<Integer, List<BlockStateMatcher>> entry: layeredCandidates.int2ObjectEntrySet()) {
                if(layers.containsKey(entry.getKey())) {
                    continue;
                }
                for (BlockStateMatcher candidate : entry.getValue()) {
                    TileOverrideImpl.CTMCompact candidateOverride = (TileOverrideImpl.CTMCompact) candidate.getData();
                    if (candidateOverride.isDisabled()) {
                        continue;
                    }
                    if (candidateOverride.getRenderPass() != -1 && candidateOverride.getRenderPass() != worldRenderPass) {
                        continue;
                    }
                    if (!renderBlockState.match(candidate)) {
                        continue;
                    }
                    renderBlockState.setFilter(candidate);
                    if (getTile(candidateOverride, renderBlockState, origIcon) != null) {
                        layers.putIfAbsent(candidateOverride.getRenderLayer(), candidateOverride);
                        continue LayerLoop;
                    }
                }
            }
        } finally {
            renderBlockState.setFilter(primaryFilter);
        }

        //layers.sort(Comparator.comparingInt(TileOverrideImpl.CTMCompact::getRenderLayer));
        return layers.values().toArray(new TileOverrideImpl.CTMCompact[0]);
    }

    public IIcon getIcon() {
        return currentIcon;
    }

    abstract protected IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon);

    public static final class IJK extends TileOverrideIterator {

        IJK(Map<Block, List<BlockStateMatcher>> blockOverrides, Map<String, List<TileOverride>> tileOverrides) {
            super(blockOverrides, tileOverrides);
        }

        @Override
        protected IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon) {
            return override.getTileWorld(renderBlockState, origIcon);
        }
    }

    public static final class Metadata extends TileOverrideIterator {

        Metadata(Map<Block, List<BlockStateMatcher>> blockOverrides, Map<String, List<TileOverride>> tileOverrides) {
            super(blockOverrides, tileOverrides);
        }

        @Override
        protected IIcon getTile(TileOverride override, RenderBlockState renderBlockState, IIcon origIcon) {
            return override.getTileHeld(renderBlockState, origIcon);
        }
    }
}
