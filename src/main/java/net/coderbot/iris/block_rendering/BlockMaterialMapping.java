package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.materialmap.BlockEntry;
import net.coderbot.iris.shaderpack.materialmap.BlockRenderType;
import net.coderbot.iris.shaderpack.materialmap.FlatteningMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockMaterialMapping {
	/**
	 * Creates a two-level map structure for block material IDs.
	 * Based on Iris's BlockState mapping approach adapted for 1.7.10's metadata system.
	 */
	public static Reference2ObjectMap<Block, Int2IntMap> createBlockMetaIdMap(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
		Reference2ObjectMap<Block, Int2IntMap> blockMatches = new Reference2ObjectOpenHashMap<>();

		// Detect modern shader packs by looking for block names that only exist post-flattening.
		// This determines how to handle ambiguous names like "grass" (block in 1.7.10, plant in 1.13+).
		boolean isModernPack = false;
		for (List<BlockEntry> entries : blockPropertiesMap.values()) {
			for (BlockEntry entry : entries) {
				if ("minecraft".equals(entry.getId().getNamespace())) {
					String name = entry.getId().getName();
					if ("grass_block".equals(name) || "short_grass".equals(name)) {
						isModernPack = true;
						break;
					}
				}
			}
			if (isModernPack) break;
		}

		if (isModernPack) {
			Iris.logger.info("Detected modern shader pack, automatically changing grass into short_grass");
		}

		final boolean modernPack = isModernPack;
		blockPropertiesMap.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				addBlockMetas(entry, blockMatches, intId, modernPack);
			}
		});

		return blockMatches;
	}

	public static Map<Block, BlockRenderLayer> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		Map<Block, BlockRenderLayer> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			Block block = resolveBlock(id);

			// Try flattening map for modern names
			if ((block == null || block == Blocks.air) && "minecraft".equals(id.getNamespace())) {
				List<BlockEntry> legacyEntries = FlatteningMap.toLegacy(id.getName(), Map.of());
				if (legacyEntries != null) {
					// Use the first entry for render type (render type applies per-block)
					block = resolveBlock(legacyEntries.getFirst().getId());
				}
			}

			if (block == null || block == Blocks.air) {
				return;
			}

			final BlockRenderLayer layer = convertBlockToRenderLayer(blockType);
			if (layer != null) {
				blockTypeIds.put(block, layer);
			}
		});

		return blockTypeIds;
	}

	private static BlockRenderLayer convertBlockToRenderLayer(BlockRenderType type) {
		if (type == null) {
			return null;
		}

		return switch (type) {
			case SOLID -> BlockRenderLayer.SOLID;
			case CUTOUT -> BlockRenderLayer.CUTOUT;
			case CUTOUT_MIPPED -> BlockRenderLayer.CUTOUT_MIPPED;
			case TRANSLUCENT -> BlockRenderLayer.TRANSLUCENT;
		};
	}

	/**
	 * Adds block+metadata combinations to the material ID map.
	 * Based on Iris's addBlockStates method, adapted for 1.7.10 metadata system.
	 */
	private static void addBlockMetas(BlockEntry entry, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId, boolean isModernPack) {
		NamespacedId id = entry.getId();
		String name = id.getName();
		boolean hasStateProps = !entry.getStateProperties().isEmpty();
		boolean hasExplicitMetas = !entry.getMetas().isEmpty();

		// In modern packs, "grass" means the short grass plant (renamed to "short_grass" in 1.20.3),
		// not the grass block (which modern packs call "grass_block").
		if (isModernPack && "minecraft".equals(id.getNamespace()) && "grass".equals(name)) {
			name = "short_grass";
		}

        if ("minecraft".equals(id.getNamespace()) && (hasStateProps || !hasExplicitMetas)) {
			List<BlockEntry> legacyEntries = FlatteningMap.toLegacy(name, entry.getStateProperties());
			if (legacyEntries != null) {
				for (BlockEntry legacy : legacyEntries) {
					applyMetas(resolveBlock(legacy.getId()), legacy.getMetas(), idMap, intId);
				}
				return;
			}
		}

		// Fall back to registry with the entry's own metas
		Block block = resolveBlock(id);
		applyMetas(block, entry.getMetas(), idMap, intId);
	}
    // If the block doesn't exist, by default the registry will return AIR. That probably isn't what we want.
    // TODO: Assuming that Registry.BLOCK.getDefaultId() == "minecraft:air" here
	private static void applyMetas(Block block, Set<Integer> metas, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId) {
		if (block == null || block == Blocks.air) {
			return;
		}

		Int2IntMap metaMap = idMap.get(block);
		if (metaMap == null) {
			metaMap = new Int2IntOpenHashMap();
			metaMap.defaultReturnValue(-1);
			idMap.put(block, metaMap);
		}

		if (metas.isEmpty()) {
			for (int meta = 0; meta < 16; meta++) {
				metaMap.putIfAbsent(meta, intId);
			}
		} else {
			for (int meta : metas) {
				metaMap.putIfAbsent(meta, intId);
			}
		}
	}

	private static Block resolveBlock(NamespacedId id) {
		final ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());
		return (Block) Block.blockRegistry.getObject(resourceLocation.toString());
	}
}
