package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
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

	@Getter
    private boolean reloadRequired;
	private Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;
	private Map<Block, BlockRenderLayer> blockTypeIds;
    // note: no reload needed, entities are rebuilt every frame.
    @Setter
    private Object2IntFunction<NamespacedId> entityIds;
    // note: no reload needed, items are rendered every frame.
    @Setter
    private Object2IntFunction<NamespacedId> itemIds;
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

	public void setBlockMetaMatches(Reference2ObjectMap<Block, Int2IntMap> blockMetaIds) {
		this.reloadRequired = true;
		this.blockMetaMatches = blockMetaIds;
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
