package net.coderbot.iris.shaderpack;

import cpw.mods.fml.common.registry.GameData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.coderbot.iris.shaderpack.materialmap.BlockEntry;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LegacyIdMap {

	/**
	 * Fills the block ID map with all vanilla blocks in the case where block.properties is not found.
	 *
	 * ShadersMod passed block IDs as a signed byte, so IDs 128-255 wrapped to -128 to -1.
	 * For example, emerald_block (ID 133) becomes -123. Shader packs like SEUS v11 were written
	 * against this behavior, so we replicate it here.
	 */
	public static void addLegacyValues(Int2ObjectMap<List<BlockEntry>> blockIdMap) {
		for (String name : (Iterable<String>) GameData.getBlockRegistry().getKeys()) {
			// Only include vanilla minecraft blocks
			if (!name.startsWith("minecraft:")) {
				continue;
			}

			final Block block = (Block) GameData.getBlockRegistry().getObject(name);
			if (block == null) {
				continue;
			}

			final int id = GameData.getBlockRegistry().getIDForObject(block);
			if (id <= 0 || id > 255) {
				continue;
			}

			// Apply signed byte overflow
			final int overflowId = (byte) id;

			final String blockName = name.substring("minecraft:".length());
			final BlockEntry entry = new BlockEntry(new NamespacedId("minecraft", blockName), Collections.emptySet());

			blockIdMap.computeIfAbsent(overflowId, k -> new ArrayList<>()).add(entry);
		}
	}
}
