package com.prupe.mcpatcher.ctm;

import static com.prupe.mcpatcher.ctm.RenderBlockState.EAST_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.NORTH_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_D;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_DL;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_DR;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_L;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_R;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_U;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_UL;
import static com.prupe.mcpatcher.ctm.RenderBlockState.REL_UR;
import static com.prupe.mcpatcher.ctm.RenderBlockState.TOP_FACE;

import com.prupe.mcpatcher.mal.block.RenderPassAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import net.minecraft.util.IIcon;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.tile.TileLoader;
import com.prupe.mcpatcher.mal.util.WeightedIndex;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class TileOverrideImpl {

    static class CTM extends TileOverride {

        CTM(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "ctm";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() >= 47) {
                return null;
            } else {
                return "requires at least 47 tiles";
            }
        }

        @Override
        boolean requiresFace() {
            return true;
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[neighborMap[getNeighborBits(renderBlockState, origIcon)]];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[0];
        }
    }

    static class Horizontal extends TileOverride {

        // Index into this array is formed from these bit values:
        // 1 * 2
        private static final int[] neighborMap = new int[] { 3, 2, 0, 1, };

        Horizontal(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "horizontal";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 4) {
                return null;
            } else {
                return "requires exactly 4 tiles";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            int face = renderBlockState.getFaceForHV();
            if (face < 0) {
                return null;
            }
            int neighborBits = 0;
            if (shouldConnect(renderBlockState, origIcon, REL_L)) {
                neighborBits |= 1;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_R)) {
                neighborBits |= 2;
            }
            return icons[neighborMap[neighborBits]];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[3];
        }
    }

    final static class HorizontalVertical extends Horizontal {

        // Index into this array is formed from these bit values:
        // 32 16 8
        // *
        // 1 2 4
        private static final int[] neighborMap = new int[] { 3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3, 4, 4, 5, 4,
            4, 4, 4, 4, 3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
            3, 3, 6, 3, 3, 3, 3, 3, };

        HorizontalVertical(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "horizontal+vertical";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 7) {
                return null;
            } else {
                return "requires exactly 7 tiles";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            IIcon icon = super.getTileWorld_Impl(renderBlockState, origIcon);
            if (icon != icons[3]) {
                return icon;
            }
            int neighborBits = 0;
            if (shouldConnect(renderBlockState, origIcon, REL_DL)) {
                neighborBits |= 1;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_D)) {
                neighborBits |= 2;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_DR)) {
                neighborBits |= 4;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_UR)) {
                neighborBits |= 8;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_U)) {
                neighborBits |= 16;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_UL)) {
                neighborBits |= 32;
            }
            return icons[neighborMap[neighborBits]];
        }
    }

    static class Vertical extends TileOverride {

        // Index into this array is formed from these bit values:
        // 2
        // *
        // 1
        private static final int[] neighborMap = new int[] { 3, 2, 0, 1, };

        Vertical(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "vertical";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 4) {
                return null;
            } else {
                return "requires exactly 4 tiles";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            int face = renderBlockState.getFaceForHV();
            if (face < 0) {
                return null;
            }
            int neighborBits = 0;
            if (shouldConnect(renderBlockState, origIcon, REL_D)) {
                neighborBits |= 1;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_U)) {
                neighborBits |= 2;
            }
            return icons[neighborMap[neighborBits]];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[3];
        }
    }

    final static class VerticalHorizontal extends Vertical {

        // Index into this array is formed from these bit values:
        // 32 16
        // 1 * 8
        // 2 4
        private static final int[] neighborMap = new int[] { 3, 6, 3, 3, 3, 6, 3, 3, 4, 5, 4, 4, 3, 6, 3, 3, 3, 6, 3, 3,
            3, 6, 3, 3, 3, 6, 3, 3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, };

        VerticalHorizontal(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "vertical+horizontal";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 7) {
                return null;
            } else {
                return "requires exactly 7 tiles";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            IIcon icon = super.getTileWorld_Impl(renderBlockState, origIcon);
            if (icon != icons[3]) {
                return icon;
            }
            int neighborBits = 0;
            if (shouldConnect(renderBlockState, origIcon, REL_L)) {
                neighborBits |= 1;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_DL)) {
                neighborBits |= 2;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_DR)) {
                neighborBits |= 4;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_R)) {
                neighborBits |= 8;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_UR)) {
                neighborBits |= 16;
            }
            if (shouldConnect(renderBlockState, origIcon, REL_UL)) {
                neighborBits |= 32;
            }
            return icons[neighborMap[neighborBits]];
        }
    }

    final static class Top extends TileOverride {

        Top(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "top";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 1) {
                return null;
            } else {
                return "requires exactly 1 tile";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            int face = renderBlockState.getBlockFace();
            if (face < 0) {
                face = NORTH_FACE;
            } else if (face <= TOP_FACE) {
                return null;
            }
            if (shouldConnect(renderBlockState, origIcon, face, REL_U)) {
                return icons[0];
            }
            return null;
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return null;
        }
    }

    final static class Random1 extends TileOverride {

        private final int symmetry;
        private final boolean linked;
        private final WeightedIndex chooser;

        Random1(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);

            String sym = properties.getString("symmetry", "none");
            if (sym.equals("all")) {
                symmetry = 6;
            } else if (sym.equals("opposite")) {
                symmetry = 2;
            } else {
                symmetry = 1;
            }

            linked = properties.getBoolean("linked", false);

            chooser = WeightedIndex.create(getNumberOfTiles(), properties.getString("weights", ""));
            if (chooser == null) {
                properties.error("invalid weights");
            }
        }

        @Override
        String getMethod() {
            return "random";
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            int face = renderBlockState.getBlockFace();
            if (face < 0) {
                face = 0;
            }
            int x = renderBlockState.getX();
            int y = renderBlockState.getY();
            int z = renderBlockState.getZ();
            if (linked && renderBlockState.setCoordOffsetsForRenderType()) {
                x += renderBlockState.getDX();
                y += renderBlockState.getDY();
                z += renderBlockState.getDZ();
            }
            long hash = WeightedIndex.hash128To64(x, y, z, face / symmetry);
            int index = chooser.choose(hash);
            return icons[index];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[0];
        }
    }

    final static class Repeat extends TileOverride {

        private final int width;
        private final int height;
        private final int symmetry;

        Repeat(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
            width = properties.getInt("width", 0);
            height = properties.getInt("height", 0);
            if (width <= 0 || height <= 0) {
                properties.error("invalid width and height (%dx%d)", width, height);
            }

            String sym = properties.getString("symmetry", "none");
            if (sym.equals("opposite")) {
                symmetry = ~1;
            } else {
                symmetry = -1;
            }
        }

        @Override
        String getMethod() {
            return "repeat";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == width * height) {
                return null;
            } else {
                return String.format("requires exactly %dx%d tiles", width, height);
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            int face = renderBlockState.getBlockFace();
            if (face < 0) {
                face = 0;
            }
            face &= symmetry;
            int x = renderBlockState.getX();
            int y = renderBlockState.getY();
            int z = renderBlockState.getZ();
            int[] xOffset = renderBlockState.getOffset(face, REL_R);
            int[] yOffset = renderBlockState.getOffset(face, REL_D);
            int offsetX = x * xOffset[0] + y * xOffset[1] + z * xOffset[2];
            int offsetY = x * yOffset[0] + y * yOffset[1] + z * yOffset[2];
            if (face == NORTH_FACE || face == EAST_FACE) {
                offsetX--;
            }
            offsetX %= width;
            if (offsetX < 0) {
                offsetX += width;
            }
            offsetY %= height;
            if (offsetY < 0) {
                offsetY += height;
            }
            return icons[width * offsetY + offsetX];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[0];
        }
    }

    final static class Fixed extends TileOverride {

        Fixed(PropertiesFile properties, TileLoader tileLoader) {
            super(properties, tileLoader);
        }

        @Override
        String getMethod() {
            return "fixed";
        }

        @Override
        String checkTileMap() {
            if (getNumberOfTiles() == 1) {
                return null;
            } else {
                return "requires exactly 1 tile";
            }
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[0];
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons[0];
        }
    }

    final public static class CTMCompact extends TileOverride {

        private final CompactConnectingCtmProperties properties;
        private volatile CompactCtmQuadProcessor processor;

        CTMCompact(PropertiesFile propertiesFile, TileLoader tileLoader) {
            super(propertiesFile, tileLoader);
            this.properties = new CompactConnectingCtmProperties(propertiesFile);
        }

        @Override
        String getMethod() {
            return "compact";
        }

        @Override
        String checkTileMap() {
            return getNumberOfTiles() == 5 ? null : "requires exactly 5 tiles";
        }

        @Override
        boolean requiresFace() {
            return true;
        }

        @Override
        IIcon getTileWorld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return origIcon;
        }

        @Override
        IIcon getTileHeld_Impl(RenderBlockState renderBlockState, IIcon origIcon) {
            return icons.length > 0 ? icons[0] : origIcon;
        }

        public CompactCtmQuadProcessor getProcessor() {
            CompactCtmQuadProcessor p = this.processor;
            if (p == null) {
                p = new CompactCtmQuadProcessor(icons, properties, this);
                this.processor = p;
            }
            return p;
        }
    }

    final public static class CTMCompactExpanded extends CTM {

        CTMCompactExpanded(PropertiesFile propertiesFile, TileLoader tileLoader) {
            super(propertiesFile, tileLoader);
        }

        @Override
        String checkTileMap() {
            return null;
        }

        @Override
        String getMethod() {
            return "compact_expanded";
        }

        @Override
        protected void loadOverrideIcons(int from, int to, ResourceLocation blankResource) {
            List<BufferedImage> compactIcons = new ArrayList<>();
            if(to != 0){
                properties.error("compact ctm requires exactly 5 icons, got range %d - %d", to, from);
            }
            if(to - from != 5){
                properties.error("compact ctm requires exactly 5 icons, got range %d - %d", to, from);
            }
            for (int i = from; i <= to; i++) {
                ResourceLocation resource = TileLoader
                    .parseTileAddress(properties.getResource(), String.valueOf(i), blankResource);
                if (TexturePackAPI.hasResource(resource)) {
                    compactIcons.add(tileLoader.loadResourceImage(resource,
                        renderPass > RenderPassAPI.MAX_BASE_RENDER_PASS));
                } else {
                    // Promote from warning to error for compact CTM
                    properties.error("could not find image %s, required for compact CTM", resource);
                    tileNames.add(null);
                }
            }
            if(compactIcons.size() == 5){
                CTMTextureGenerator.generateTextures(compactIcons.toArray(new BufferedImage[0]),
                    this, properties.getResource(), blankResource);
            }
        }
    }
}
