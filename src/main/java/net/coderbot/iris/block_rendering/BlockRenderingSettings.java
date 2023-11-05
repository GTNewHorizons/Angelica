package net.coderbot.iris.block_rendering;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BlockRenderingSettings {
	public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

	private boolean reloadRequired;
    // TODO: BlockStateIdMap
	private Object2IntMap<Object> blockStateIds;
	private Map<Block, RenderLayer> blockTypeIds;
	private Object2IntFunction<NamespacedId> entityIds;
	private float ambientOcclusionLevel;
	private boolean disableDirectionalShading;
	private boolean useSeparateAo;
	private boolean useExtendedVertexFormat;

	public BlockRenderingSettings() {
		reloadRequired = false;
		blockStateIds = null;
		blockTypeIds = null;
		ambientOcclusionLevel = 1.0F;
		disableDirectionalShading = false;
		useSeparateAo = false;
		useExtendedVertexFormat = false;
	}

	public boolean isReloadRequired() {
		return reloadRequired;
	}

	public void clearReloadRequired() {
		reloadRequired = false;
	}

    // TODO: BlockStateIdMap
	@Nullable
	public Object2IntMap<Object> getBlockStateIds() {
		return blockStateIds;
	}

	@Nullable
	public Map<Block, RenderLayer> getBlockTypeIds() {
		return blockTypeIds;
	}

	// TODO (coderbot): This doesn't belong here. But I couldn't think of a nicer place to put it.
	@Nullable
	public Object2IntFunction<NamespacedId> getEntityIds() {
		return entityIds;
	}

    // TODO: BlockStateIdMap
	public void setBlockStateIds(Object2IntMap<Object> blockStateIds) {
		if (this.blockStateIds != null && this.blockStateIds.equals(blockStateIds)) {
			return;
		}

		this.reloadRequired = true;
		this.blockStateIds = blockStateIds;
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
