package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BlockRenderingSettings {
	public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

	public static final int CACHE_MISS = Integer.MIN_VALUE;

	private static final int NBT_CACHE_INTERVAL_TICKS = 20;
	private static final int TE_NBT_CACHE_MAX = 8192;

	private static final Long2LongLinkedOpenHashMap teNbtIdCache = new Long2LongLinkedOpenHashMap(256);
	private static final Object teNbtIdCacheLock = new Object();

	static {
		teNbtIdCache.defaultReturnValue(-1L);
	}

	public static long packBlockPos(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
	}

	public static void invalidateTeNbtCache(int x, int y, int z) {
		final long key = packBlockPos(x, y, z);
		synchronized (teNbtIdCacheLock) {
			teNbtIdCache.remove(key);
		}
	}

	public static int getCachedTeNbtId(long packedPos, long currentTick) {
		final long entry;
		synchronized (teNbtIdCacheLock) {
			entry = teNbtIdCache.get(packedPos);
		}
		if (entry == -1L) {
			return CACHE_MISS;
		}
		final long entryTick = entry >>> 32;
		if (currentTick - entryTick >= NBT_CACHE_INTERVAL_TICKS) {
			return CACHE_MISS;
		}
		return (int) entry;
	}

	public static void cacheTeNbtId(long packedPos, int shaderId, long currentTick) {
		// Mask tick into low 31 bits so packed value never collides with the -1L sentinel.
		final long packed = ((currentTick & 0x7FFFFFFFL) << 32) | (shaderId & 0xFFFFFFFFL);
		synchronized (teNbtIdCacheLock) {
			teNbtIdCache.put(packedPos, packed);
			while (teNbtIdCache.size() > TE_NBT_CACHE_MAX) {
				teNbtIdCache.removeFirstLong();
			}
		}
	}

	public static void clearTeNbtCache() {
		synchronized (teNbtIdCacheLock) {
			teNbtIdCache.clear();
		}
	}

	@Getter
    private boolean reloadRequired;
	private Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;
	private NbtConditionalIdMap<Block> blockNbtMap;
	private Map<Block, BlockRenderLayer> blockTypeIds;
    // note: no reload needed, entities are rebuilt every frame.
    @Setter
    private Object2IntFunction<NamespacedId> entityIds;
	@Setter
    private NbtConditionalIdMap<NamespacedId> entityNbtMap;
    // note: no reload needed, items are rendered every frame.
    @Setter
    private Object2IntFunction<NamespacedId> itemIds;
	@Setter
    private NbtConditionalIdMap<NamespacedId> itemNbtMap;
	@Getter
    private float ambientOcclusionLevel;
	private boolean disableDirectionalShading;
	private boolean useSeparateAo;
	private boolean useExtendedVertexFormat;
	@Setter
    private boolean hasSnowyEntries;
	@Getter
    private ReferenceSet<Block> snowyBlocks = ReferenceSets.emptySet();

	public BlockRenderingSettings() {
		reloadRequired = false;
		blockMetaMatches = null;
		blockNbtMap = null;
		blockTypeIds = null;
		ambientOcclusionLevel = 1.0F;
		disableDirectionalShading = false;
		useSeparateAo = false;
		useExtendedVertexFormat = false;
		hasSnowyEntries = false;
	}

    public void clearReloadRequired() {
		reloadRequired = false;
	}

	public void reloadRendererIfRequired() {
		if (isReloadRequired()) {
			if (Minecraft.getMinecraft().renderGlobal != null) {
				Minecraft.getMinecraft().renderGlobal.loadRenderers();
			}
			clearReloadRequired();
		}
	}

    @Nullable
	public Reference2ObjectMap<Block, Int2IntMap> getBlockMetaMatches() {
		return blockMetaMatches;
	}

	@Nullable
	public Map<Block, BlockRenderLayer> getBlockTypeIds() {
		return blockTypeIds;
	}

	@Nullable
	public Object2IntFunction<NamespacedId> getEntityIds() {
		return entityIds;
	}

	@Nullable
	public Object2IntFunction<NamespacedId> getItemIds() {
		return itemIds;
	}

	@Nullable
	public NbtConditionalIdMap<Block> getBlockNbtMap() {
		return blockNbtMap;
	}

	@Nullable
	public NbtConditionalIdMap<NamespacedId> getItemNbtMap() {
		return itemNbtMap;
	}

	@Nullable
	public NbtConditionalIdMap<NamespacedId> getEntityNbtMap() {
		return entityNbtMap;
	}

	public void setBlockMetaMatches(Reference2ObjectMap<Block, Int2IntMap> blockMetaIds) {
		this.reloadRequired = true;
		this.blockMetaMatches = blockMetaIds;
	}

	public void setBlockNbtMap(NbtConditionalIdMap<Block> blockNbtMap) {
		this.reloadRequired = true;
		this.blockNbtMap = blockNbtMap;
		clearTeNbtCache();
	}

	public boolean hasSnowyEntries() {
		return hasSnowyEntries;
	}

    public void setSnowyBlocks(ReferenceSet<Block> snowyBlocks) {
		this.snowyBlocks = snowyBlocks != null ? snowyBlocks : ReferenceSets.emptySet();
	}

	public void setBlockTypeIds(Map<Block, BlockRenderLayer> blockTypeIds) {
		if (this.blockTypeIds != null && this.blockTypeIds.equals(blockTypeIds)) {
			return;
		}

		this.reloadRequired = true;
		this.blockTypeIds = blockTypeIds;
	}

    public void setAmbientOcclusionLevel(float ambientOcclusionLevel) {
		if (ambientOcclusionLevel == this.ambientOcclusionLevel) {
			return;
		}

		this.reloadRequired = true;
		this.ambientOcclusionLevel = ambientOcclusionLevel;
	}

	public boolean shouldDisableDirectionalShading() {
		return disableDirectionalShading;
	}

	public void setDisableDirectionalShading(boolean disableDirectionalShading) {
		if (disableDirectionalShading == this.disableDirectionalShading) {
			return;
		}

		this.reloadRequired = true;
		this.disableDirectionalShading = disableDirectionalShading;
	}

	public boolean shouldUseSeparateAo() {
		return useSeparateAo;
	}

	public void setUseSeparateAo(boolean useSeparateAo) {
		if (useSeparateAo == this.useSeparateAo) {
			return;
		}

		this.reloadRequired = true;
		this.useSeparateAo = useSeparateAo;
	}

	public boolean shouldUseExtendedVertexFormat() {
		return useExtendedVertexFormat;
	}

	public void setUseExtendedVertexFormat(boolean useExtendedVertexFormat) {
		if (useExtendedVertexFormat == this.useExtendedVertexFormat) {
			return;
		}

		this.reloadRequired = true;
		this.useExtendedVertexFormat = useExtendedVertexFormat;
	}
}
