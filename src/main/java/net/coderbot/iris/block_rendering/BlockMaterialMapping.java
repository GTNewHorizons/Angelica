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
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockMaterialMapping {

	public record BlockIdMaps(Reference2ObjectMap<Block, Int2IntMap> blockMetaMap, NbtConditionalIdMap<Block> tileEntityMap) {}

	// Meta-key bits OR'd in at runtime
	public static final int SNOWY_META_BIT = 0x10;

	public static final int DOUBLE_PLANT_TOP_BIT = 0x8;

	/**
	 * Creates the standard block meta ID map, the TileEntity NBT-conditional map, and registers
	 * snowy blocks on {@link BlockRenderingSettings}.
	 */
	public static BlockIdMaps createBlockIdMaps(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap, boolean skipFlattening) {
		final Reference2ObjectMap<Block, Int2IntMap> blockMatches = new Reference2ObjectOpenHashMap<>();
		final NbtConditionalIdMap<Block> tileEntityMap = new NbtConditionalIdMap<>();
		final ReferenceSet<Block> snowyBlocks = new ReferenceOpenHashSet<>();

		blockPropertiesMap.forEach((intId, entries) -> {
			for (BlockEntry entry : entries) {
				if (entry.hasNbtProperties()) {
					addTileEntityEntry(entry, tileEntityMap, intId);
				} else {
					addBlockMetas(entry, blockMatches, tileEntityMap, intId, skipFlattening, snowyBlocks);
				}
			}
		});

		BlockRenderingSettings.INSTANCE.setHasSnowyEntries(!snowyBlocks.isEmpty());
		BlockRenderingSettings.INSTANCE.setSnowyBlocks(snowyBlocks);
		return new BlockIdMaps(blockMatches, tileEntityMap);
	}

	public static Map<Block, BlockRenderLayer> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		final Map<Block, BlockRenderLayer> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			Block block = resolveBlockOrNull(id);

			// Modern names like "grass_block" don't exist in 1.7.10's registry; fall back to flattening.
			if (block == null && "minecraft".equals(id.getNamespace())) {
				final List<BlockEntry> legacyEntries = FlatteningMap.toLegacy(id.getName(), Map.of());
				if (legacyEntries != null) {
					// Use the first entry for render type (render type applies per-block)
					block = resolveBlockOrNull(legacyEntries.getFirst().id());
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
	private static void addBlockMetas(BlockEntry entry, Reference2ObjectMap<Block, Int2IntMap> idMap,
									  NbtConditionalIdMap<Block> tileEntityMap, int intId,
									  boolean skipFlattening, ReferenceSet<Block> snowyBlocks) {
		final NamespacedId id = entry.id();
		final Map<String, String> stateProps = entry.stateProperties();
		final String snowy = stateProps.get("snowy");
		final int snowyBit = "true".equals(snowy) ? SNOWY_META_BIT : 0;
		final boolean wantsDoublePlantTop = "upper".equals(stateProps.get("half"));

		// Vanilla modern names go through FlatteningMap; legacy-section packs and modded blocks
		// resolve directly from the registry.
		List<BlockEntry> targets = null;
		if (!skipFlattening && "minecraft".equals(id.getNamespace()) && (!stateProps.isEmpty() || entry.metas().isEmpty())) {
			targets = FlatteningMap.toLegacy(id.getName(), stateProps);
		}
		if (targets == null) targets = List.of(entry);

		for (BlockEntry target : targets) {
			if (target.hasNbtProperties()) {
				addTileEntityEntry(target, tileEntityMap, intId);
				continue;
			}
			final Block block = resolveBlockOrNull(target.id());
			if (block == null) continue;
			int extraBits = snowyBit;
			if (wantsDoublePlantTop && block instanceof BlockDoublePlant) {
				extraBits |= DOUBLE_PLANT_TOP_BIT;
			}
			applyMetas(block, target.metas(), idMap, intId, extraBits);
			if (snowy != null) snowyBlocks.add(block);
		}
	}

	private static void applyMetas(Block block, Set<Integer> metas, Reference2ObjectMap<Block, Int2IntMap> idMap, int intId, int extraBits) {
		Int2IntMap metaMap = idMap.get(block);
		if (metaMap == null) {
			metaMap = new Int2IntOpenHashMap();
			metaMap.defaultReturnValue(-1);
			idMap.put(block, metaMap);
		}

		if (metas.isEmpty()) {
			for (int meta = 0; meta < 16; meta++) metaMap.putIfAbsent(meta | extraBits, intId);
		} else {
			for (int meta : metas) metaMap.putIfAbsent(meta | extraBits, intId);
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
