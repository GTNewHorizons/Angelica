package com.prupe.mcpatcher.cit;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.tile.TileLoader;

final class ItemOverride extends OverrideBase {

    private IIcon icon;
    private final Map<IIcon, IIcon> iconMap;

    ItemOverride(PropertiesFile properties) {
        super(properties);

        if (items == null) {
            properties.error("no matching items specified");
        }

        iconMap = alternateTextures == null ? null : new HashMap<>();
    }

    @Override
    String getType() {
        return "item";
    }

    IIcon getReplacementIcon(IIcon origIcon) {
        if (iconMap != null) {
            IIcon newIcon = iconMap.get(origIcon);
            if (newIcon != null) {
                return newIcon;
            }
        }
        return icon;
    }

    void preload(TileLoader tileLoader) {
        String special = null;
        if (items != null) {
            if (items.contains(CITUtils.itemCompass)) {
                special = "compass";
            } else if (items.contains(CITUtils.itemClock)) {
                special = "clock";
            }
        }
        if (textureName != null) {
            tileLoader.preloadTile(textureName, false, special);
        }
        if (alternateTextures != null) {
            for (Map.Entry<String, ResourceLocation> entry : alternateTextures.entrySet()) {
                tileLoader.preloadTile(entry.getValue(), false, special);
            }
        }
    }

    void registerIcon(TileLoader tileLoader) {
        if (textureName != null) {
            icon = tileLoader.getIcon(textureName);
        }
        if (alternateTextures != null) {
            for (Map.Entry<String, ResourceLocation> entry : alternateTextures.entrySet()) {
                IIcon from = tileLoader.getIcon(entry.getKey());
                IIcon to = tileLoader.getIcon(entry.getValue());
                if (from != null && to != null) {
                    iconMap.put(from, to);
                }
            }
        }
    }

    @Override
    String preprocessAltTextureKey(String name) {
        if (name.startsWith("textures/items/")) {
            name = name.substring(15);
            if (name.endsWith(".png")) {
                name = name.substring(0, name.length() - 4);
            }
        }
        return name;
    }
}
