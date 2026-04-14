package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockRenderingSettings {
	public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

	private static final ConcurrentHashMap<Long, Integer> teNbtIdCache = new ConcurrentHashMap<>(256);

	public static long packBlockPos(int x, int y, int z) {
		return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
	}

	public static void invalidateTeNbtCache(int x, int y, int z) {
		teNbtIdCache.remove(packBlockPos(x, y, z));
	}

	public static Integer getCachedTeNbtId(long packedPos) {
		return teNbtIdCache.get(packedPos);
	}

	public static void cacheTeNbtId(long packedPos, int shaderId) {
		teNbtIdCache.put(packedPos, shaderId);
	}

	public static void clearTeNbtCache() {
		teNbtIdCache.clear();
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

	public BlockRenderingSettings() {
		reloadRequired = false;
		blockMetaMatches = null;
		blockNbtMap = null;
		blockTypeIds = null;
		ambientOcclusionLevel = 1.0F;
		disableDirectionalShading = false;
		useSeparateAo = false;
		useExtendedVertexFormat = false;
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
