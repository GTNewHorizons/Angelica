package com.gtnewhorizons.angelica.render;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class EmissiveTextureAutoloader {

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;

        TextureMap map = event.map;
        EmissiveTextureHelper.clearCache();

        @SuppressWarnings("unchecked")
        Map<String, ?> registeredSprites = map.mapRegisteredSprites;

        List<String> toRegister = new ArrayList<>();

        for (String baseName : registeredSprites.keySet()) {
            if (baseName.endsWith("_emissive")) continue;

            String emissiveName = baseName + "_emissive";
            if (resourceExists(emissiveName)) {
                toRegister.add(baseName);
            }
        }

        for (String baseName : toRegister) {
            String emissiveName = baseName + "_emissive";
            IIcon emissiveIcon = map.registerIcon(emissiveName);
            EmissiveTextureHelper.cacheEmissive(baseName, emissiveIcon);
        }
    }

    private boolean resourceExists(String iconName) {
        ResourceLocation loc = new ResourceLocation(iconName);
        ResourceLocation full = new ResourceLocation(
            loc.getResourceDomain(),
            "textures/blocks/" + loc.getResourcePath() + ".png"
        );
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(full);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
