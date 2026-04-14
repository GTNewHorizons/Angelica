package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.coderbot.iris.shaderpack.materialmap.BlockEntry;
import net.coderbot.iris.shaderpack.materialmap.BlockRenderType;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockMaterialMapping {

	public record BlockIdMaps(Reference2ObjectMap<Block, Int2IntMap> blockMetaMap, NbtConditionalIdMap<Block> tileEntityMap) {}

	/**
	 * Creates both the standard block meta ID map and the TileEntity NBT-conditional map.
	 */
	public static BlockIdMaps createBlockIdMaps(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
		Reference2ObjectMap<Block, Int2IntMap> blockMatches = new Reference2ObjectOpenHashMap<>();
		NbtConditionalIdMap<Block> tileEntityMap = new NbtConditionalIdMap<>();

		blockPropertiesMap.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				if (entry.hasNbtProperties()) {
					addTileEntityEntry(entry, tileEntityMap, intId);
				} else {
					addBlockMetas(entry, blockMatches, intId);
				}
			}
		});

		return new BlockIdMaps(blockMatches, tileEntityMap);
	}

	public static Map<Block, BlockRenderLayer> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		Map<Block, BlockRenderLayer> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			final ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());
			final Block block = Block.getBlockFromName(resourceLocation.toString());

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
	 * Creates an NBT-conditional ID map keyed by NamespacedId, for items or entities.
	 */
	public static NbtConditionalIdMap<NamespacedId> createNamespacedNbtMap(Int2ObjectMap<List<BlockEntry>> nbtEntries) {
		NbtConditionalIdMap<NamespacedId> map = new NbtConditionalIdMap<>();

		nbtEntries.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				if (entry.hasNbtProperties()) {
					map.addCondition(entry.id(), entry.nbtProperties(), intId);
				}
			}
		});

		return map;
	}

	private static void addTileEntityEntry(BlockEntry entry, NbtConditionalIdMap<Block> teMap, int intId) {
		final NamespacedId id = entry.id();
		final ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());

		final Block block = (Block) Block.blockRegistry.getObject(resourceLocation.toString());

		if (block == null || block == Blocks.air) {
			return;
		}

		teMap.addCondition(block, entry.nbtProperties(), intId);
	}

	/**
	 * Adds block+metadata combinations to the material ID map.
	 * Based on Iris's addBlockStates method, adapted for 1.7.10 metadata system.
	 */
	private static void addBlockMetas(BlockEntry entry, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId) {
		final NamespacedId id = entry.id();
		final ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());

		final Block block = (Block) Block.blockRegistry.getObject(resourceLocation.toString());

		// If the block doesn't exist, by default the registry will return AIR. That probably isn't what we want.
		// TODO: Assuming that Registry.BLOCK.getDefaultId() == "minecraft:air" here
		if (block == null || block == Blocks.air) {
			return;
		}

		Set<Integer> metas = entry.metas();

		Int2IntMap metaMap = idMap.get(block);
		if (metaMap == null) {
			metaMap = new Int2IntOpenHashMap();
			metaMap.defaultReturnValue(-1);
			idMap.put(block, metaMap);
		}

		if (metas.isEmpty()) {
			// Add all metadata values (0-15) if there aren't any specific ones
			for (int meta = 0; meta < 16; meta++) {
				metaMap.putIfAbsent(meta, intId);
			}
		} else {
			// Add only specific metadata values
			for (int meta : metas) {
				metaMap.putIfAbsent(meta, intId);
			}
		}
	}
}
