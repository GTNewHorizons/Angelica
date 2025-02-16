package net.coderbot.iris.block_rendering;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.coderbot.iris.shaderpack.materialmap.BlockMetaEntry;
import net.coderbot.iris.shaderpack.materialmap.BlockRenderType;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Map;

public class BlockMaterialMapping {

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
            // Everything renders in cutout or translucent in 1.7.10
            case SOLID, CUTOUT, CUTOUT_MIPPED -> RenderLayer.cutout();
            // case SOLID -> RenderLayer.solid();
            // case CUTOUT_MIPPED -> RenderLayer.cutoutMipped();
            case TRANSLUCENT -> RenderLayer.translucent();
            default -> null;
        };
	}

	private static void addBlock(BlockMetaEntry entry, Object2IntMap<Block> idMap, int intId) {
		final NamespacedId id = entry.getId();
		final ResourceLocation resourceLocation = new ResourceLocation(id.getNamespace(), id.getName());

		final Block block = (Block) Block.blockRegistry.getObject(resourceLocation.toString());

		// If the block doesn't exist, by default the registry will return AIR. That probably isn't what we want.
		// TODO: Assuming that Registry.BLOCK.getDefaultId() == "minecraft:air" here
		if (block == null || block == Blocks.air) {
			return;
		}

        idMap.put(block, intId);
	}

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
