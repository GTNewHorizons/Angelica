package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
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

	/** Meta-key bit OR'd in at runtime when a snowy-tagged block has snow above it. */
	public static final int SNOWY_META_BIT = 0x10;

	/**
	 * Creates a two-level map structure for block material IDs.
	 * Based on Iris's BlockState mapping approach adapted for 1.7.10's metadata system.
	 */
	public static Reference2ObjectMap<Block, Int2IntMap> createBlockMetaIdMap(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap, boolean skipFlattening) {
		final Reference2ObjectMap<Block, Int2IntMap> blockMatches = new Reference2ObjectOpenHashMap<>();
		final ReferenceSet<Block> snowyBlocks = new ReferenceOpenHashSet<>();

		blockPropertiesMap.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				addBlockMetas(entry, blockMatches, intId, skipFlattening, snowyBlocks);
			}
		});

		BlockRenderingSettings.INSTANCE.setHasSnowyEntries(!snowyBlocks.isEmpty());
		BlockRenderingSettings.INSTANCE.setSnowyBlocks(snowyBlocks);
		return blockMatches;
	}

	public static Map<Block, BlockRenderLayer> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		final Map<Block, BlockRenderLayer> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			Block block = resolveBlockOrNull(id);

			// Modern names like "grass_block" don't exist in 1.7.10's registry; fall back to flattening.
			if (block == null && "minecraft".equals(id.getNamespace())) {
				final List<BlockEntry> legacyEntries = FlatteningMap.toLegacy(id.getName(), Map.of());
				if (legacyEntries != null) {
					block = resolveBlockOrNull(legacyEntries.getFirst().getId());
				}
			}

			if (block == null) return;
			final BlockRenderLayer layer = convertBlockToRenderLayer(blockType);
			if (layer != null) blockTypeIds.put(block, layer);
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
	private static void addBlockMetas(BlockEntry entry, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId, boolean skipFlattening, ReferenceSet<Block> snowyBlocks) {
		final NamespacedId id = entry.getId();
		final Map<String, String> stateProps = entry.getStateProperties();
		final String snowy = stateProps.get("snowy");
		final int snowyBit = "true".equals(snowy) ? SNOWY_META_BIT : 0;

		// Vanilla modern names go through FlatteningMap; legacy-section packs and modded blocks
		// resolve directly from the registry.
		List<BlockEntry> targets = null;
		if (!skipFlattening && "minecraft".equals(id.getNamespace()) && (!stateProps.isEmpty() || entry.getMetas().isEmpty())) {
			targets = FlatteningMap.toLegacy(id.getName(), stateProps);
		}
		if (targets == null) targets = List.of(entry);

		for (BlockEntry target : targets) {
			final Block block = resolveBlockOrNull(target.getId());
			if (block == null) continue;
			applyMetas(block, target.getMetas(), idMap, intId, snowyBit);
			if (snowy != null) snowyBlocks.add(block);
		}
	}

	private static void applyMetas(Block block, Set<Integer> metas, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId, int snowyBit) {
		Int2IntMap metaMap = idMap.get(block);
		if (metaMap == null) {
			metaMap = new Int2IntOpenHashMap();
			metaMap.defaultReturnValue(-1);
			idMap.put(block, metaMap);
		}

		if (metas.isEmpty()) {
			for (int meta = 0; meta < 16; meta++) metaMap.putIfAbsent(meta | snowyBit, intId);
		} else {
			for (int meta : metas) metaMap.putIfAbsent(meta | snowyBit, intId);
		}
	}

	/**
	 * Returns the registered Block for an id, or null if unknown.
	 * The registry returns Blocks.air (its default) for missing keys, which we coerce to null.
	 */
	private static Block resolveBlockOrNull(NamespacedId id) {
		final Block block = (Block) Block.blockRegistry.getObject(new ResourceLocation(id.getNamespace(), id.getName()).toString());
		return block == Blocks.air ? null : block;
	}
}
