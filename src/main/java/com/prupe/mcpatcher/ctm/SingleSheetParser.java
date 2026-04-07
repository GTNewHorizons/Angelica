package com.prupe.mcpatcher.ctm;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class SingleSheetParser {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CONNECTED_TEXTURES, "SingleSheet");

    private SingleSheetParser() {}

    private static final Map<ResourceLocation, Map<Integer, ResourceLocation>> cache = new HashMap<>();

    public static Map<Integer, ResourceLocation> parse(ResourceLocation sheetResource, int[][] layout, String virtualPrefix) {
        if (cache.containsKey(sheetResource)) {
            return cache.get(sheetResource);
        }

        BufferedImage sheet = TexturePackAPI.getImage(sheetResource);
        if (sheet == null) {
            logger.error("image %s not found", sheetResource);
            return null;
        }

        int rows = layout.length;
        if (rows == 0) {
            logger.error("layout has 0 rows");
            return null;
        }

        int cols = maxCols(layout);
        if (cols == 0) {
            logger.error("layout has 0 columns");
            return null;
        }

        int tileWidth = sheet.getWidth() / cols;
        int tileHeight = sheet.getHeight() / rows;

        if (tileWidth <= 0 || tileHeight <= 0) {
            logger.error("tile size %dx%d invalid (image %dx%d, layout %dx%d)",
                tileWidth, tileHeight, sheet.getWidth(), sheet.getHeight(), cols, rows);
            return null;
        }

        Map<Integer, ResourceLocation> result = new HashMap<>();
        String domain = sheetResource.getResourceDomain();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < layout[row].length; col++) {
                int tileIndex = layout[row][col];
                if (tileIndex < 0) continue;

                BufferedImage tile = sheet.getSubimage(
                    col * tileWidth,
                    row * tileHeight,
                    tileWidth,
                    tileHeight
                );

                String basePath = virtualPrefix;
                int lastSlash = basePath.lastIndexOf('/');
                if (lastSlash > 0) {
                    basePath = basePath.substring(0, lastSlash);
                }

                ResourceLocation virtualLoc = new ResourceLocation(
                    domain,
                    basePath + "/" + tileIndex + ".png"
                );

                SingleSheetVirtualResources.register(virtualLoc, tile);
                result.put(tileIndex, virtualLoc);
            }
        }

        cache.put(sheetResource, result);
        logger.info("successfully parsed %d tiles from single sheet %s", result.size(), sheetResource);
        return result;
    }

    private static @NotNull ResourceLocation getVirtualLoc(String virtualPrefix, String domain, int tileIndex) {
        String basePath = virtualPrefix;
        int lastSlash = basePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            String last = basePath.substring(lastSlash + 1);
            if (basePath.endsWith("/" + last)) {
                basePath = basePath.substring(0, lastSlash);
            }
        }

        return new ResourceLocation(
            domain,
            basePath + "/" + tileIndex + ".png"
        );
    }

    private static int maxCols(int[][] layout) {
        int max = 0;
        for (int[] row : layout) {
            if (row.length > max) {
                max = row.length;
            }
        }
        return max;
    }

    public static void clearCache() {
        cache.clear();
    }
}
