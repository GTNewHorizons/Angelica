package com.prupe.mcpatcher.cit;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

final class ArmorOverride extends OverrideBase {

    private final Map<ResourceLocation, ResourceLocation> armorMap;

    ArmorOverride(PropertiesFile properties) {
        super(properties);

        if (items == null) {
            properties.error("no matching items specified");
        }
        if (textureName == null && alternateTextures == null) {
            properties.error("no replacement textures specified");
        }

        if (alternateTextures == null) {
            armorMap = null;
        } else {
            armorMap = new HashMap<>();
            for (Map.Entry<String, ResourceLocation> entry : alternateTextures.entrySet()) {
                String key = entry.getKey();
                ResourceLocation value = entry.getValue();
                armorMap.put(TexturePackAPI.parseResourceLocation(CITUtils.FIXED_ARMOR_RESOURCE, key), value);
            }
        }
    }

    @Override
    String getType() {
        return "armor";
    }

    ResourceLocation getReplacementTexture(ResourceLocation origResource) {
        if (armorMap != null) {
            ResourceLocation newResource = armorMap.get(origResource);
            if (newResource != null) {
                return newResource;
            }
        }
        return textureName;
    }

    @Override
    String preprocessAltTextureKey(String name) {
        if (!name.endsWith(".png")) {
            name += ".png";
        }
        if (!name.contains("/")) {
            name = "./" + name;
        }
        return TexturePackAPI.parseResourceLocation(CITUtils.FIXED_ARMOR_RESOURCE, name)
            .toString();
    }
}
