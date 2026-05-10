package com.gtnewhorizons.angelica.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.concurrent.ConcurrentHashMap;

@SideOnly(Side.CLIENT)
public final class EmissiveTextureHelper {
    private static final ConcurrentHashMap<String, IIcon> EMISSIVE_ICON_CACHE = new ConcurrentHashMap<>();

    public static IIcon getEmissiveIcon(Block block, int side, int metadata) {
        IIcon baseIcon = block.getIcon(side, metadata);
        if (baseIcon == null) return null;

        String baseIconName = baseIcon.getIconName();
        if (baseIconName == null) return null;

        return EMISSIVE_ICON_CACHE.get(baseIconName);
    }

    public static void clearCache() {
        EMISSIVE_ICON_CACHE.clear();
    }

    public static void cacheEmissive(String baseIconName, IIcon emissiveIcon) {
        EMISSIVE_ICON_CACHE.put(baseIconName, emissiveIcon);
    }

    public static void renderEmissiveBlockOverlay(RenderBlocks renderer, Block block, int x, int y, int z, int metadata) {
        Tessellator tess = Tessellator.instance;

        for (int side = 0; side < 6; side++) {
            IIcon icon = getEmissiveIcon(block, side, metadata);
            if (icon == null) continue;

            tess.setColorOpaque_F(1f, 1f, 1f);

            switch (side) {
                case 0 -> renderer.renderFaceYNeg(block, x, y, z, icon);
                case 1 -> renderer.renderFaceYPos(block, x, y, z, icon);
                case 2 -> renderer.renderFaceZNeg(block, x, y, z, icon);
                case 3 -> renderer.renderFaceZPos(block, x, y, z, icon);
                case 4 -> renderer.renderFaceXNeg(block, x, y, z, icon);
                case 5 -> renderer.renderFaceXPos(block, x, y, z, icon);
            }
        }
    }
}
