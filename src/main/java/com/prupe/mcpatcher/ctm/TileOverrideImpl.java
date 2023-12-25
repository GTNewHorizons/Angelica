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

import net.minecraft.util.IIcon;

import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.tile.TileLoader;
import com.prupe.mcpatcher.mal.util.WeightedIndex;

class TileOverrideImpl {

    final static class CTM extends TileOverride {

        // Index into this array is formed from these bit values:
        // 128 64 32
        // 1 * 16
        // 2 4 8
        private static final int[] neighborMap = new int[] { 0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15, 1, 2,
            1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14, 0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15, 1, 2,
            1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14, 36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24,
            43, 16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22, 36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36,
            17, 24, 19, 24, 43, 37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45, 0, 3, 0, 3, 12, 5, 12,
            15, 0, 3, 0, 3, 12, 5, 12, 15, 1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14, 0, 3, 0, 3, 12, 5, 12,
            15, 0, 3, 0, 3, 12, 5, 12, 15, 1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14, 36, 39, 36, 39, 24, 41,
            24, 27, 36, 39, 36, 39, 24, 41, 24, 27, 16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44, 36,
            39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27, 37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38,
            25, 33, 25, 26, };

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
            int neighborBits = 0;
            for (int bit = 0; bit < 8; bit++) {
                if (shouldConnect(renderBlockState, origIcon, bit)) {
                    neighborBits |= (1 << bit);
                }
            }
            return icons[neighborMap[neighborBits]];
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
            int i = renderBlockState.getI();
            int j = renderBlockState.getJ();
            int k = renderBlockState.getK();
            if (linked && renderBlockState.setCoordOffsetsForRenderType()) {
                i += renderBlockState.getDI();
                j += renderBlockState.getDJ();
                k += renderBlockState.getDK();
            }
            long hash = WeightedIndex.hash128To64(i, j, k, face / symmetry);
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
            int i = renderBlockState.getI();
            int j = renderBlockState.getJ();
            int k = renderBlockState.getK();
            int[] xOffset = renderBlockState.getOffset(face, REL_R);
            int[] yOffset = renderBlockState.getOffset(face, REL_D);
            int x = i * xOffset[0] + j * xOffset[1] + k * xOffset[2];
            int y = i * yOffset[0] + j * yOffset[1] + k * yOffset[2];
            if (face == NORTH_FACE || face == EAST_FACE) {
                x--;
            }
            x %= width;
            if (x < 0) {
                x += width;
            }
            y %= height;
            if (y < 0) {
                y += height;
            }
            return icons[width * y + x];
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
}
