package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import lombok.Getter;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BlockRenderingSettings {
	public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

	@Getter
    private boolean reloadRequired;
	private Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;
	private Map<Block, RenderLayer> blockTypeIds;
	private Object2IntFunction<NamespacedId> entityIds;
	private Object2IntFunction<NamespacedId> itemIds;
	private float ambientOcclusionLevel;
	private boolean disableDirectionalShading;
	private boolean useSeparateAo;
	private boolean useExtendedVertexFormat;

	public BlockRenderingSettings() {
		reloadRequired = false;
		blockMetaMatches = null;
		blockTypeIds = null;
		ambientOcclusionLevel = 1.0F;
		disableDirectionalShading = false;
		useSeparateAo = false;
		useExtendedVertexFormat = false;
	}

    public void clearReloadRequired() {
		reloadRequired = false;
	}

    @Nullable
	public Reference2ObjectMap<Block, Int2IntMap> getBlockMetaMatches() {
		return blockMetaMatches;
	}

	@Nullable
	public Map<Block, RenderLayer> getBlockTypeIds() {
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

	public void setBlockTypeIds(Map<Block, RenderLayer> blockTypeIds) {
		if (this.blockTypeIds != null && this.blockTypeIds.equals(blockTypeIds)) {
			return;
		}

		this.reloadRequired = true;
		this.blockTypeIds = blockTypeIds;
	}

	public void setEntityIds(Object2IntFunction<NamespacedId> entityIds) {
		// note: no reload needed, entities are rebuilt every frame.
		this.entityIds = entityIds;
	}

	public void setItemIds(Object2IntFunction<NamespacedId> itemIds) {
		// note: no reload needed, items are rendered every frame.
		this.itemIds = itemIds;
	}

	public float getAmbientOcclusionLevel() {
		return ambientOcclusionLevel;
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
