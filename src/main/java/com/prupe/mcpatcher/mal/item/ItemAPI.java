package com.prupe.mcpatcher.mal.item;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import net.minecraft.item.Item;

import com.prupe.mcpatcher.MCPatcherUtils;

import cpw.mods.fml.common.registry.GameData;

public class ItemAPI {

    ItemAPI() {
        File outputFile = new File("items17.txt");
        if (outputFile.isFile()) {
            PrintStream ps = null;
            try {
                ps = new PrintStream(outputFile);
                String[] nameList = new String[32000];
                for (String name17 : (Set<String>) GameData.getItemRegistry()
                    .getKeys()) {
                    Item item = GameData.getItemRegistry()
                        .getObject(name17);
                    if (item != null) {
                        int id = GameData.getItemRegistry()
                            .getIDForObject(item);
                        if (id >= 256 && id < nameList.length) {
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

    public static Item getFixedItem(String name) {
        Item item = parseItemName(name);
        if (item == null) {
            throw new IllegalArgumentException("unknown item " + name);
        } else {
            return item;
        }
    }

    public static Item parseItemName(String name) {
        if (MCPatcherUtils.isNullOrEmpty(name)) {
            return null;
        }
        if (name.matches("\\d+")) {
            int id = Integer.parseInt(name);
            return GameData.getItemRegistry()
                .getObjectById(id);
        }
        name = getFullName(name);
        return GameData.getItemRegistry()
            .getObject(name);
    }

    public static String getItemName(Item item) {
        if (item == null) {
            return "(null)";
        }
        String name = GameData.getItemRegistry()
            .getNameForObject(item);
        return name == null ? String.valueOf(
            GameData.getItemRegistry()
                .getIDForObject(item))
            : name;
    }

    public static String getFullName(String name) {
        return name == null ? null : name.indexOf(':') >= 0 ? name : "minecraft:" + name;
    }
}
