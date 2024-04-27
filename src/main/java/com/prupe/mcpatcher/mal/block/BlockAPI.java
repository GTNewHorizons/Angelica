package com.prupe.mcpatcher.mal.block;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;

import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;

public class BlockAPI {

    BlockAPI(FMLControlledNamespacedRegistry<Block> registry) {
        File outputFile = new File("blocks17.txt");
        if (outputFile.isFile()) {
            PrintStream ps = null;
            try {
                ps = new PrintStream(outputFile);
                String[] nameList = new String[4096];
                for (String name17 : (Set<String>) registry.getKeys()) {
                    Object block = registry.getObject(name17);
                    if (block != null) {
                        int id = registry.getIDForObject(block);
                        if (id >= 0 && id < nameList.length) {
                            nameList[id] = name17;
                        }
                    }
                }
                for (int id = 0; id < nameList.length; id++) {
                    if (nameList[id] != null) {
                        ps.printf("canonicalIdByName.put(\"%s\", %d);\n", nameList[id], id);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                MCPatcherUtils.close(ps);
            }
        }
    }

    public static String getBlockName(Block block) {
        return block == null ? "(null)" : block.getUnlocalizedName();
    }

    public static List<Block> getAllBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : GameData.getBlockRegistry()
            .typeSafeIterable()) {
            if (block != null && !blocks.contains(block)) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    public static BlockStateMatcher createMatcher(PropertiesFile source, String matchString) {
        Map<String, String> propertyMap = new HashMap<>();
        String namespace = null;
        String blockName = null;
        StringBuilder metadata = new StringBuilder();
        StringBuilder metaString = new StringBuilder();
        for (String s : matchString.split("\\s*:\\s*")) {
            if (s.isEmpty()) {
                continue;
            }
            boolean appendThis = false;
            String[] tokens = s.split("\\s*=\\s*", 2);
            if (blockName == null) {
                blockName = s;
            } else if (tokens.length == 2) {
                propertyMap.put(tokens[0], tokens[1]);
                appendThis = true;
            } else if (namespace == null && !s.matches("\\d[-, 0-9]*")) {
                namespace = blockName;
                blockName = s;
            } else if (s.matches("\\d[-, 0-9]*")) {
                metadata.append(' ')
                    .append(s);
                appendThis = true;
            } else {
                source.warning("invalid token '%s' in %s", source, s, matchString);
                return null;
            }
            if (appendThis) {
                metaString.append(':');
                metaString.append(s);
            }
        }

        if (MCPatcherUtils.isNullOrEmpty(namespace)) {
            namespace = source.getResource()
                .getResourceDomain();
        }
        if (MCPatcherUtils.isNullOrEmpty(blockName)) {
            source.warning("cannot parse namespace/block name from %s", matchString);
            return null;
        }
        Block block = GameData.getBlockRegistry()
            .getObject(namespace + ':' + blockName);
        if (block == null) {
            source.warning("unknown block %s:%s", namespace, blockName);
            return null;
        }
        return new BlockStateMatcher(
            metaString.toString(),
            block,
            metadata.toString()
                .trim());
    }

}
