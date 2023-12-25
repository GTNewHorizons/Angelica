package net.coderbot.iris.block_rendering;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import net.coderbot.iris.shaderpack.materialmap.BlockEntry;
import net.coderbot.iris.shaderpack.materialmap.BlockRenderType;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;

public class BlockMaterialMapping {
	public static Object2IntMap<Object> createBlockStateIdMap(Int2ObjectMap<List<BlockEntry>> blockPropertiesMap) {
		Object2IntMap<Object> blockStateIds = new Object2IntOpenHashMap<>();

        // TODO: BlockStateIdMap
//		blockPropertiesMap.forEach((intId, entries) -> {
//			for (BlockEntry entry : entries) {
//				addBlockStates(entry, blockStateIds, intId);
//			}
//		});

		return blockStateIds;
	}

	public static Map<Block, RenderLayer> createBlockTypeMap(Map<NamespacedId, BlockRenderType> blockPropertiesMap) {
		Map<Block, RenderLayer> blockTypeIds = new Reference2ReferenceOpenHashMap<>();

		blockPropertiesMap.forEach((id, blockType) -> {
			ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());

			Block block = Block.getBlockFromName(resourceLocation.toString());

			blockTypeIds.put(block, convertBlockToRenderType(blockType));
		});

		return blockTypeIds;
	}

	private static RenderLayer convertBlockToRenderType(BlockRenderType type) {
		if (type == null) {
			return null;
		}

        return switch (type) {
            // TODO: RenderType
//            case SOLID -> RenderType.solid();
//            case CUTOUT -> RenderType.cutout();
//            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
//            case TRANSLUCENT -> RenderType.translucent();
            default -> null;
        };
	}

    // TODO: BlockStateIdMap
//	private static void addBlockStates(BlockEntry entry, Object2IntMap<BlockState> idMap, int intId) {
//		NamespacedId id = entry.getId();
//		ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());
//
//		Block block = Registry.BLOCK.get(resourceLocation);
//
//		// If the block doesn't exist, by default the registry will return AIR. That probably isn't what we want.
//		// TODO: Assuming that Registry.BLOCK.getDefaultId() == "minecraft:air" here
//		if (block == Blocks.air) {
//			return;
//		}
//
//		Map<String, String> propertyPredicates = entry.getPropertyPredicates();
//
//		if (propertyPredicates.isEmpty()) {
//			// Just add all the states if there aren't any predicates
//			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
//				// NB: Using putIfAbsent means that the first successful mapping takes precedence
//				//     Needed for OptiFine parity:
//				//     https://github.com/IrisShaders/Iris/issues/1327
//				idMap.putIfAbsent(state, intId);
//			}
//
//			return;
//		}
//
//		// As a result, we first collect each key=value pair in order to determine what properties we need to filter on.
//		// We already get this from BlockEntry, but we convert the keys to `Property`s to ensure they exist and to avoid
//		// string comparisons later.
//		Map<Property<?>, String> properties = new HashMap<>();
//		StateDefinition<Block, BlockState> stateManager = block.getStateDefinition();
//
//		propertyPredicates.forEach((key, value) -> {
//			Property<?> property = stateManager.getProperty(key);
//
//			if (property == null) {
//				Iris.logger.warn("Error while parsing the block ID map entry for \"" + "block." + intId + "\":");
//				Iris.logger.warn("- The block " + resourceLocation + " has no property with the name " + key + ", ignoring!");
//
//				return;
//			}
//
//			properties.put(property, value);
//		});
//
//		// Once we have a list of properties and their expected values, we iterate over every possible state of this
//		// block and check for ones that match the filters. This isn't particularly efficient, but it works!
//		for (BlockState state : stateManager.getPossibleStates()) {
//			if (checkState(state, properties)) {
//				// NB: Using putIfAbsent means that the first successful mapping takes precedence
//				//     Needed for OptiFine parity:
//				//     https://github.com/IrisShaders/Iris/issues/1327
//				idMap.putIfAbsent(state, intId);
//			}
//		}
//	}

	// We ignore generics here, the actual types don't matter because we just convert
	// them to strings anyways, and the compiler checks just get in the way.
	//
	// If you're able to rewrite this function without SuppressWarnings, feel free.
	// But otherwise it works fine.
    // TODO: BlockStateIdMap
//	@SuppressWarnings({"rawtypes", "unchecked"})
//	private static boolean checkState(BlockState state, Map<Property<?>, String> expectedValues) {
//		for (Map.Entry<Property<?>, String> condition : expectedValues.entrySet()) {
//			Property property = condition.getKey();
//			String expectedValue = condition.getValue();
//
//			String actualValue = property.getName(state.getValue(property));
//
//			if (!expectedValue.equals(actualValue)) {
//				return false;
//			}
//		}
//
//		return true;
//	}
}
