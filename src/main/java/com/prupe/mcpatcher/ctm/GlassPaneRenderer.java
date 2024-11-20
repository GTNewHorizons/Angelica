package com.prupe.mcpatcher.ctm;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import jss.notfine.config.MCPatcherForgeConfig;

public class GlassPaneRenderer {

    public static boolean skipPaneRendering;
    public static boolean skipTopEdgeRendering;
    public static boolean skipBottomEdgeRendering;

    private static final IIcon[] icons = new IIcon[6];

    private static double uLeft; // left edge
    private static double uLeftThin; // 7/16 point
    private static double uRightThin; // 9/16 point
    private static double uRight; // right edge
    private static double vTop; // top edge
    private static double vBottom; // bottom edge

    private static double u1Scaled;
    private static double u2Scaled;

    public static void renderThin(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int x, int y, int z,
        boolean connectNorth, boolean connectSouth, boolean connectWest, boolean connectEast) {
        if (setupIcons(renderBlocks, blockPane, origIcon, x, y, z)) {
            render(x, y, z, connectNorth, connectSouth, connectWest, connectEast, 0.0, 0.0, 0.0, false);
        }
    }

    public static void renderThick(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int x, int y, int z,
        boolean connectNorth, boolean connectSouth, boolean connectWest, boolean connectEast) {
        if (setupIcons(renderBlocks, blockPane, origIcon, x, y, z)) {
            setupPaneEdges(renderBlocks.blockAccess, blockPane, x, y, z);
            render(x, y, z, connectNorth, connectSouth, connectWest, connectEast, 0.0625, 1.0, 0.001, true);
        }
    }

    private static boolean setupIcons(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int x, int y, int z) {
        skipPaneRendering = skipBottomEdgeRendering = skipTopEdgeRendering = false;
        if (!MCPatcherForgeConfig.ConnectedTextures.glassPane) {
            return false;
        }
        for (int face = BlockOrientation.NORTH_FACE; face <= BlockOrientation.EAST_FACE; face++) {
            icons[face] = CTMUtils
                .getBlockIcon(origIcon, blockPane, renderBlocks.blockAccess, x, y, z, face);
            if (icons[face] == null) {
                skipPaneRendering = false;
                return false;
            } else if (icons[face] != origIcon) {
                skipPaneRendering = true;
            }
        }
        return skipPaneRendering;
    }

    private static void setupPaneEdges(IBlockAccess blockAccess, Block blockPane, int x, int y, int z) {
        int metadata = blockAccess.getBlockMetadata(x, y, z);
        skipBottomEdgeRendering = blockAccess.getBlock(x, y - 1, z) == blockPane
            && blockAccess.getBlockMetadata(x, y - 1, z) == metadata;
        skipTopEdgeRendering = blockAccess.getBlock(x, y + 1, z) == blockPane
            && blockAccess.getBlockMetadata(x, y + 1, z) == metadata;
    }

    private static void render(int x, int y, int z, boolean connectNorth, boolean connectSouth, boolean connectWest,
        boolean connectEast, double thickness, double uOffset, double yOffset, boolean edges) {
        final double west = x;
        final double westThin = x + 0.5 - thickness;
        final double eastThin = x + 0.5 + thickness;
        final double east = x + 1.0;
        final double down = y + yOffset;
        final double up = y + 1.0 - yOffset;
        final double north = z;
        final double northThin = z + 0.5 - thickness;
        final double southThin = z + 0.5 + thickness;
        final double south = z + 1.0;

        u1Scaled = 8.0 - uOffset;
        u2Scaled = 8.0 + uOffset;

        if (!connectNorth && !connectSouth && !connectWest && !connectEast) {
            connectNorth = connectSouth = connectWest = connectEast = true;

            if (edges) {
                // east pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.EAST_FACE);
                drawFace(east, up, southThin, uLeftThin, vTop, east, down, northThin, uRightThin, vBottom);

                // west pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.WEST_FACE);
                drawFace(west, up, northThin, uLeftThin, vTop, west, down, southThin, uRightThin, vBottom);

                // south pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.SOUTH_FACE);
                drawFace(westThin, up, south, uLeftThin, vTop, eastThin, down, south, uRightThin, vBottom);

                // north pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.NORTH_FACE);
                drawFace(eastThin, up, north, uLeftThin, vTop, westThin, down, north, uRightThin, vBottom);
            }
        }

        if (connectEast && connectWest) {
            // full west-east pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            drawFace(west, up, southThin, uLeft, vTop, east, down, southThin, uRight, vBottom);

            setupTileCoords(BlockOrientation.NORTH_FACE);
            drawFace(east, up, northThin, uLeft, vTop, west, down, northThin, uRight, vBottom);
        } else if (connectWest) {
            // west half-pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            if (connectSouth) {
                // inner corner: 7/16 wide
                drawFace(west, up, southThin, uRightThin, vTop, westThin, down, southThin, uRight, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(west, up, southThin, uLeftThin, vTop, eastThin, down, southThin, uRight, vBottom);
            }

            setupTileCoords(BlockOrientation.NORTH_FACE);
            if (connectNorth) {
                // inner corner: 7/16 wide
                drawFace(westThin, up, northThin, uLeft, vTop, west, down, northThin, uLeftThin, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(eastThin, up, northThin, uLeft, vTop, west, down, northThin, uRightThin, vBottom);
            }

            if (edges && !connectNorth && !connectSouth) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.EAST_FACE);
                drawFace(eastThin, up, southThin, uLeftThin, vTop, eastThin, down, northThin, uRightThin, vBottom);
            }
        } else if (connectEast) {
            // east half-pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            if (connectSouth) {
                // inner corner: 7/16 wide
                drawFace(eastThin, up, southThin, uLeft, vTop, east, down, southThin, uLeftThin, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(westThin, up, southThin, uLeft, vTop, east, down, southThin, uRightThin, vBottom);
            }

            setupTileCoords(BlockOrientation.NORTH_FACE);
            if (connectNorth) {
                // inner corner: 7/16 wide
                drawFace(east, up, northThin, uRightThin, vTop, eastThin, down, northThin, uRight, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(east, up, northThin, uLeftThin, vTop, westThin, down, northThin, uRight, vBottom);
            }

            if (edges && !connectNorth && !connectSouth) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.WEST_FACE);
                drawFace(westThin, up, northThin, uLeftThin, vTop, westThin, down, southThin, uRightThin, vBottom);
            }
        }

        if (connectNorth && connectSouth) {
            // full north-south pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            drawFace(westThin, up, north, uLeft, vTop, westThin, down, south, uRight, vBottom);

            setupTileCoords(BlockOrientation.EAST_FACE);
            drawFace(eastThin, up, south, uLeft, vTop, eastThin, down, north, uRight, vBottom);
        } else if (connectNorth) {
            // north half-pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            if (connectWest) {
                // inner corner: 7/16 wide
                drawFace(westThin, up, north, uRightThin, vTop, westThin, down, northThin, uRight, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(westThin, up, north, uLeftThin, vTop, westThin, down, southThin, uRight, vBottom);
            }

            setupTileCoords(BlockOrientation.EAST_FACE);
            if (connectEast) {
                // inner corner: 7/16 wide
                drawFace(eastThin, up, northThin, uLeft, vTop, eastThin, down, north, uLeftThin, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(eastThin, up, southThin, uLeft, vTop, eastThin, down, north, uRightThin, vBottom);
            }

            if (edges && !connectWest && !connectEast) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.SOUTH_FACE);
                drawFace(westThin, up, southThin, uLeftThin, vTop, eastThin, down, southThin, uRightThin, vBottom);
            }
        } else if (connectSouth) {
            // south half-pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            if (connectWest) {
                // inner corner: 7/16 wide
                drawFace(westThin, up, southThin, uLeft, vTop, westThin, down, south, uLeftThin, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(westThin, up, northThin, uLeft, vTop, westThin, down, south, uRightThin, vBottom);
            }

            setupTileCoords(BlockOrientation.EAST_FACE);
            if (connectEast) {
                // inner corner: 7/16 wide
                drawFace(eastThin, up, south, uRightThin, vTop, eastThin, down, southThin, uRight, vBottom);
            } else {
                // outer corner: 9/16 wide
                drawFace(eastThin, up, south, uLeftThin, vTop, eastThin, down, northThin, uRight, vBottom);
            }

            if (edges && !connectWest && !connectEast) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.NORTH_FACE);
                drawFace(eastThin, up, northThin, uLeftThin, vTop, westThin, down, northThin, uRightThin, vBottom);
            }
        }
    }

    private static void setupTileCoords(int face) {
        final IIcon icon = icons[face];
        uLeft = icon.getMinU();
        uLeftThin = icon.getInterpolatedU(u1Scaled);
        uRightThin = icon.getInterpolatedU(u2Scaled);
        uRight = icon.getMaxU();
        vTop = icon.getMinV();
        vBottom = icon.getMaxV();
    }

    private static void drawFace(double x0, double y0, double z0, double u0, double v0, // top left
        double x1, double y1, double z1, double u1, double v1) { // lower right
        Tessellator tessellator = Tessellator.instance;
        tessellator.addVertexWithUV(x0, y0, z0, u0, v0);
        tessellator.addVertexWithUV(x0, y1, z0, u0, v1);
        tessellator.addVertexWithUV(x1, y1, z1, u1, v1);
        tessellator.addVertexWithUV(x1, y0, z1, u1, v0);
    }

    protected static void clear() {
        Arrays.fill(icons, null);
        skipPaneRendering = false;
    }

}
