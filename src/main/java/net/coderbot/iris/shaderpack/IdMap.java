package net.coderbot.iris.shaderpack;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortFunction;
import it.unimi.dsi.fastutil.ints.Int2ShortMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.MaterialIdLookup;
import net.coderbot.iris.shaderpack.materialmap.BlockMetaEntry;
import net.coderbot.iris.shaderpack.materialmap.BlockRenderType;
import net.coderbot.iris.shaderpack.materialmap.Entry;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.shaderpack.materialmap.TagEntry;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;
import net.coderbot.iris.shaderpack.preprocessor.PropertiesPreprocessor;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * A utility class for parsing entries in item.properties, block.properties, and entities.properties files in shaderpacks
 */
@Getter
@EqualsAndHashCode
public class IdMap {
	/**
	 * Maps a given item ID to an integer ID
	 */
    private final Object2IntMap<NamespacedId> itemIdMap;

	/**
	 * Maps a given entity ID to an integer ID
	 */
    private final Object2IntMap<NamespacedId> entityIdMap;

    /**
     * Maps tags to block ids defined in block.properties
     */
    private Int2ObjectMap<List<TagEntry>> blockTagMap;

	/**
	 * Maps block states to block ids defined in block.properties
	 */
    private Int2ObjectMap<List<BlockMetaEntry>> blockPropertiesMap;

    @EqualsAndHashCode.Exclude
    private final Object2ObjectMap<Block, Int2ShortFunction> blockPropertiesLookup = new Object2ObjectOpenHashMap<>();

	/**
	 * A set of render type overrides for specific blocks. Allows shader packs to move blocks to different render types.
	 */
    private Map<NamespacedId, BlockRenderType> blockRenderTypeMap;

	IdMap(Path shaderPath, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
		itemIdMap = loadProperties(shaderPath, "item.properties", shaderPackOptions, environmentDefines)
            .map(IdMap::parseItemIdMap).orElse(Object2IntMaps.emptyMap());

		entityIdMap = loadProperties(shaderPath, "entity.properties", shaderPackOptions, environmentDefines)
            .map(IdMap::parseEntityIdMap).orElse(Object2IntMaps.emptyMap());
        blockTagMap = new Int2ObjectLinkedOpenHashMap<>();

		loadProperties(shaderPath, "block.properties", shaderPackOptions, environmentDefines).ifPresent(blockProperties -> {
			blockPropertiesMap = parseBlockMap(blockProperties, "block.", "block.properties", blockTagMap);
			blockRenderTypeMap = parseRenderTypeMap(blockProperties, "layer.", "block.properties");
		});

		// TODO: Properly override block render layers

		if (blockPropertiesMap == null) {
			// Fill in with default values...
			blockPropertiesMap = new Int2ObjectOpenHashMap<>();
			LegacyIdMap.addLegacyValues(blockPropertiesMap);
		}

        // if blocks aren't loaded, don't load the material lookup immediately
        if (ClientProxy.hitPostInit) {
            loadMaterialIdLookup();
        }

        if (blockRenderTypeMap == null) {
			blockRenderTypeMap = Collections.emptyMap();
		}
	}

    public void loadMaterialIdLookup() {
        blockPropertiesLookup.clear();

        blockPropertiesMap.forEach((materialId, blockEntries) -> {
            for (BlockMetaEntry entry : blockEntries) {
                Block block = entry.getId().getBlock();

                if (block == null) {
                    if (AngelicaConfig.enableDebugLogging) {
                        Iris.logger.warn("Not adding shader material id for missing block " + entry.getId().describe());
                    }
                    continue;
                }

                short matId = materialId.shortValue();

                Int2ShortMap metaMap = ((Int2ShortMap) blockPropertiesLookup.computeIfAbsent(block, ignored -> {
                    Int2ShortMap map = new Int2ShortOpenHashMap();
                    if (entry.getMetas().isEmpty()) {
                        // spec has no meta, meaning that it should be applied to all variants of the block
                        map.defaultReturnValue(matId);
                    } else {
                        // spec has meta, it should only be applied to specific variants of the block
                        // note that -1 can be overridden by a meta-less spec in the future, this isn't permanent
                        map.defaultReturnValue((short) -1);
                    }
                    return map;
                }));

                if (entry.getMetas().isEmpty()) {
                    if (metaMap.defaultReturnValue() != matId) {
                        if (metaMap.defaultReturnValue() != -1) {
                            Iris.logger.error("Attempted to replace default shader material id {} with {} for block {}", metaMap.defaultReturnValue(), materialId, entry.getId().describe());
                        } else {
                            metaMap.defaultReturnValue(matId);
                        }
                    }
                } else {
                    for (int meta : entry.getMetas()) {
                        metaMap.put(meta, materialId.shortValue());
                    }
                }
            }
        });
    }

	/**
	 * Loads properties from a properties file in a shaderpack path
	 */
	private static Optional<Properties> loadProperties(Path shaderPath, String name, ShaderPackOptions shaderPackOptions,
													   Iterable<StringPair> environmentDefines) {
		String fileContents = readProperties(shaderPath, name);
		if (fileContents == null) {
			return Optional.empty();
		}

		String processed = PropertiesPreprocessor.preprocessSource(fileContents, shaderPackOptions, environmentDefines);

		StringReader propertiesReader = new StringReader(processed);

		// Note: ordering of properties is significant
		// See https://github.com/IrisShaders/Iris/issues/1327 and the relevant putIfAbsent calls in
		// BlockMaterialMapping
		Properties properties = new OrderBackedProperties();
		try {
			properties.load(propertiesReader);
		} catch (IOException e) {
			Iris.logger.error("Error loading " + name + " at " + shaderPath, e);

			return Optional.empty();
		}

		return Optional.of(properties);
	}

	private static String readProperties(Path shaderPath, String name) {
		try {
			// ID maps should be encoded in ISO_8859_1.
			return new String(Files.readAllBytes(shaderPath.resolve(name)), StandardCharsets.ISO_8859_1);
		} catch (NoSuchFileException e) {
			Iris.logger.debug("An " + name + " file was not found in the current shaderpack");

			return null;
		} catch (IOException e) {
			Iris.logger.error("An IOException occurred reading " + name + " from the current shaderpack", e);

			return null;
		}
	}

	private static Object2IntMap<NamespacedId> parseItemIdMap(Properties properties) {
		return parseIdMap(properties, "item.", "item.properties");
	}

	private static Object2IntMap<NamespacedId> parseEntityIdMap(Properties properties) {
		return parseIdMap(properties, "entity.", "entity.properties");
	}

	/**
	 * Parses a NamespacedId map in OptiFine format
	 */
	private static Object2IntMap<NamespacedId> parseIdMap(Properties properties, String keyPrefix, String fileName) {
		Object2IntMap<NamespacedId> idMap = new Object2IntOpenHashMap<>();
		idMap.defaultReturnValue(-1);

		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if (!key.startsWith(keyPrefix)) {
				// Not a valid line, ignore it
				return;
			}

			int intId;

			try {
				intId = Integer.parseInt(key.substring(keyPrefix.length()));
			} catch (NumberFormatException e) {
				// Not a valid property line
				Iris.logger.warn("Failed to parse line in " + fileName + ": invalid key " + key);
				return;
			}

			// Split on any whitespace
			for (String part : value.split("\\s+")) {
				if (part.contains("=")) {
					// Avoid tons of logspam for now
					Iris.logger.warn("Failed to parse an ResourceLocation in " + fileName + " for the key " + key + ": state properties are currently not supported: " + part);
					continue;
				}

				// Note: NamespacedId performs no validation on the content. That will need to be done by whatever is
				//       converting these things to ResourceLocations.
				idMap.put(new NamespacedId(part), intId);
			}
		});

		return Object2IntMaps.unmodifiable(idMap);
	}

	private static Int2ObjectMap<List<BlockMetaEntry>> parseBlockMap(Properties properties, String keyPrefix, String fileName, Int2ObjectLinkedOpenHashMap<List<TagEntry>> blockTagMap) {
		Int2ObjectMap<List<BlockMetaEntry>> blockEntriesById = new Int2ObjectOpenHashMap<>();
        Int2ObjectLinkedOpenHashMap<List<TagEntry>> tagEntriesById = new Int2ObjectLinkedOpenHashMap<>();

		properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

			if (!key.startsWith(keyPrefix)) {
				// Not a valid line, ignore it
				return;
			}

			int matId;

			try {
				matId = Integer.parseInt(key.substring(keyPrefix.length()));
			} catch (NumberFormatException e) {
				// Not a valid property line
				Iris.logger.warn("Failed to parse line in " + fileName + ": invalid key " + key);
				return;
			}

			List<BlockMetaEntry> blockEntries = new ArrayList<>();
            List<TagEntry> tagEntries = new ArrayList<>();

			if (value.contains("minecraft:leaves")) {
				ArrayList<ItemStack> leaves = OreDictionary.getOres("treeLeaves");

                StringBuilder newValue = new StringBuilder(value);

				for (ItemStack leaf : leaves) {
					if (leaf.getItem() instanceof ItemBlock) {
                        if (AngelicaConfig.enableDebugLogging) {
                            Iris.logger.info("Found leaf " + Item.itemRegistry.getNameForObject(leaf.getItem()));
                        }
                        newValue.append(" ").append(Item.itemRegistry.getNameForObject(leaf.getItem()));
					}
				}

                value = newValue.toString();
			}

			// Split on whitespace groups, not just single spaces
			for (String part : value.split("\\s+")) {
				if (part.isEmpty()) {
					continue;
				}

				try {
                    Entry entry = BlockMetaEntry.parse(part);
                    if (entry instanceof BlockMetaEntry be) {
                        blockEntries.add(be);
                    } else if (entry instanceof TagEntry te) {
                        tagEntries.add(te);
                    }
				} catch (Exception e) {
					Iris.logger.warn("Unexpected error while parsing an entry from " + fileName + " for the key " + key + ":", e);
				}
			}

            blockEntriesById.put(matId, Collections.unmodifiableList(blockEntries));
            tagEntriesById.put(matId, Collections.unmodifiableList(tagEntries));
		});

        blockTagMap.putAll(tagEntriesById);

		return Int2ObjectMaps.unmodifiable(blockEntriesById);
	}

	/**
	 * Parses a render layer map.
	 *
	 * This feature is used by Chocapic v9 and Wisdom Shaders. Otherwise, it is a rarely-used feature.
	 */
	private static Map<NamespacedId, BlockRenderType> parseRenderTypeMap(Properties properties, String keyPrefix, String fileName) {
		Map<NamespacedId, BlockRenderType> overrides = new HashMap<>();

		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if (!key.startsWith(keyPrefix)) {
				// Not a valid line, ignore it
				return;
			}

			// Note: We have to remove the prefix "layer." because fromString expects "cutout", not "layer.cutout".
			String keyWithoutPrefix = key.substring(keyPrefix.length());

			BlockRenderType renderType = BlockRenderType.fromString(keyWithoutPrefix).orElse(null);

			if (renderType == null) {
				Iris.logger.warn("Failed to parse line in " + fileName + ": invalid block render type: " + key);
				return;
			}

			for (String part : value.split("\\s+")) {
				// Note: NamespacedId performs no validation on the content. That will need to be done by whatever is
				//       converting these things to ResourceLocations.
				overrides.put(new NamespacedId(part), renderType);
			}
		});

		return overrides;
	}

    public MaterialIdLookup getBlockIdLookup() {
        return (block, meta) -> {
            Int2ShortFunction fn = blockPropertiesLookup.get(block);

            if (fn == null) return (short) -1;

            return fn.get(meta);
        };
    }
}
