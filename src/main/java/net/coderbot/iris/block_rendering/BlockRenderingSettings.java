package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class BlockRenderingSettings {
	public static final BlockRenderingSettings INSTANCE = new BlockRenderingSettings();

	@Getter
    private boolean reloadRequired;
	private Object2IntMap<Block> blockMatches;
	private Map<Block, RenderLayer> blockTypeIds;
	private Object2IntFunction<NamespacedId> entityIds;
	private float ambientOcclusionLevel;
	private boolean disableDirectionalShading;
	private boolean useSeparateAo;
	private boolean useExtendedVertexFormat;

	public BlockRenderingSettings() {
		reloadRequired = false;
		blockMatches = null;
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
	public Object2IntMap<Block> getBlockMatches() {
		return blockMatches;
	}

	@Nullable
	public Map<Block, RenderLayer> getBlockTypeIds() {
		return blockTypeIds;
	}

	@Nullable
	public Object2IntFunction<NamespacedId> getEntityIds() {
		return entityIds;
	}

	public void setBlockMatches(Object2IntMap<Block> blockIds) {
		if (this.blockMatches != null && this.blockMatches.equals(blockIds)) {
			return;
		}

		this.reloadRequired = true;
		this.blockMatches = blockIds;
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
