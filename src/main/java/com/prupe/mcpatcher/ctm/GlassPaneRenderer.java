package com.prupe.mcpatcher.ctm;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.mal.block.BlockAPI;

import mist475.mcpatcherforge.config.MCPatcherForgeConfig;

public class GlassPaneRenderer {

    private static final boolean enable = MCPatcherForgeConfig.instance().ctmGlassPane;

    public static boolean skipPaneRendering;
    public static boolean skipTopEdgeRendering;
    public static boolean skipBottomEdgeRendering;

    private static final IIcon[] icons = new IIcon[6];

    private static double u0; // left edge
    private static double u1; // 7/16 point
    private static double u2; // 9/16 point
    private static double u3; // right edge
    private static double v0; // top edge
    private static double v1; // bottom edge

    private static double u1Scaled;
    private static double u2Scaled;

    public static void renderThin(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int i, int j, int k,
        boolean connectNorth, boolean connectSouth, boolean connectWest, boolean connectEast) {
        if (setupIcons(renderBlocks, blockPane, origIcon, i, j, k)) {
            render(i, j, k, connectNorth, connectSouth, connectWest, connectEast, 0.0, 0.0, 0.0, false);
        }
    }

    public static void renderThick(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int i, int j, int k,
        boolean connectNorth, boolean connectSouth, boolean connectWest, boolean connectEast) {
        if (setupIcons(renderBlocks, blockPane, origIcon, i, j, k)) {
            setupPaneEdges(renderBlocks, blockPane, i, j, k);
            render(i, j, k, connectNorth, connectSouth, connectWest, connectEast, 0.0625, 1.0, 0.001, true);
        }
    }

    private static boolean setupIcons(RenderBlocks renderBlocks, Block blockPane, IIcon origIcon, int i, int j, int k) {
        skipPaneRendering = skipBottomEdgeRendering = skipTopEdgeRendering = false;
        if (!enable) {
            return false;
        }
        for (int face = BlockOrientation.NORTH_FACE; face <= BlockOrientation.EAST_FACE; face++) {
            icons[face] = CTMUtils
                .getBlockIcon(origIcon, renderBlocks, blockPane, renderBlocks.blockAccess, i, j, k, face);
            if (icons[face] == null) {
                skipPaneRendering = false;
                return false;
            } else if (icons[face] != origIcon) {
                skipPaneRendering = true;
            }
        }
        return skipPaneRendering;
    }

    private static void setupPaneEdges(RenderBlocks renderBlocks, Block blockPane, int i, int j, int k) {
        IBlockAccess blockAccess = renderBlocks.blockAccess;
        int metadata = BlockAPI.getMetadataAt(blockAccess, i, j, k);
        skipBottomEdgeRendering = BlockAPI.getBlockAt(blockAccess, i, j - 1, k) == blockPane
            && BlockAPI.getMetadataAt(blockAccess, i, j - 1, k) == metadata;
        skipTopEdgeRendering = BlockAPI.getBlockAt(blockAccess, i, j + 1, k) == blockPane
            && BlockAPI.getMetadataAt(blockAccess, i, j + 1, k) == metadata;
    }

    private static void render(int i, int j, int k, boolean connectNorth, boolean connectSouth, boolean connectWest,
        boolean connectEast, double thickness, double uOffset, double yOffset, boolean edges) {
        final double i0 = i;
        final double i1 = i0 + 0.5 - thickness;
        final double i2 = i0 + 0.5 + thickness;
        final double i3 = i0 + 1.0;
        final double j0 = j + yOffset;
        final double j1 = j + 1.0 - yOffset;
        final double k0 = k;
        final double k1 = k0 + 0.5 - thickness;
        final double k2 = k0 + 0.5 + thickness;
        final double k3 = k0 + 1.0;

        u1Scaled = 8.0 - uOffset;
        u2Scaled = 8.0 + uOffset;

        if (!connectNorth && !connectSouth && !connectWest && !connectEast) {
            connectNorth = connectSouth = connectWest = connectEast = true;

            if (edges) {
                // east pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.EAST_FACE);
                drawFace(i3, j1, k2, u1, v0, i3, j0, k1, u2, v1);

                // west pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.WEST_FACE);
                drawFace(i0, j1, k1, u1, v0, i0, j0, k2, u2, v1);

                // south pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.SOUTH_FACE);
                drawFace(i1, j1, k3, u1, v0, i2, j0, k3, u2, v1);

                // north pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.NORTH_FACE);
                drawFace(i2, j1, k0, u1, v0, i1, j0, k0, u2, v1);
            }
        }

        if (connectEast && connectWest) {
            // full west-east pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            drawFace(i0, j1, k2, u0, v0, i3, j0, k2, u3, v1);

            setupTileCoords(BlockOrientation.NORTH_FACE);
            drawFace(i3, j1, k1, u0, v0, i0, j0, k1, u3, v1);
        } else if (connectWest) {
            // west half-pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            if (connectSouth) {
                // inner corner: 7/16 wide
                drawFace(i0, j1, k2, u2, v0, i1, j0, k2, u3, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i0, j1, k2, u1, v0, i2, j0, k2, u3, v1);
            }

            setupTileCoords(BlockOrientation.NORTH_FACE);
            if (connectNorth) {
                // inner corner: 7/16 wide
                drawFace(i1, j1, k1, u0, v0, i0, j0, k1, u1, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i2, j1, k1, u0, v0, i0, j0, k1, u2, v1);
            }

            if (edges && !connectNorth && !connectSouth) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.EAST_FACE);
                drawFace(i2, j1, k2, u1, v0, i2, j0, k1, u2, v1);
            }
        } else if (connectEast) {
            // east half-pane
            setupTileCoords(BlockOrientation.SOUTH_FACE);
            if (connectSouth) {
                // inner corner: 7/16 wide
                drawFace(i2, j1, k2, u0, v0, i3, j0, k2, u1, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i1, j1, k2, u0, v0, i3, j0, k2, u2, v1);
            }

            setupTileCoords(BlockOrientation.NORTH_FACE);
            if (connectNorth) {
                // inner corner: 7/16 wide
                drawFace(i3, j1, k1, u2, v0, i2, j0, k1, u3, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i3, j1, k1, u1, v0, i1, j0, k1, u3, v1);
            }

            if (edges && !connectNorth && !connectSouth) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.WEST_FACE);
                drawFace(i1, j1, k1, u1, v0, i1, j0, k2, u2, v1);
            }
        }

        if (connectNorth && connectSouth) {
            // full north-south pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            drawFace(i1, j1, k0, u0, v0, i1, j0, k3, u3, v1);

            setupTileCoords(BlockOrientation.EAST_FACE);
            drawFace(i2, j1, k3, u0, v0, i2, j0, k0, u3, v1);
        } else if (connectNorth) {
            // north half-pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            if (connectWest) {
                // inner corner: 7/16 wide
                drawFace(i1, j1, k0, u2, v0, i1, j0, k1, u3, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i1, j1, k0, u1, v0, i1, j0, k2, u3, v1);
            }

            setupTileCoords(BlockOrientation.EAST_FACE);
            if (connectEast) {
                // inner corner: 7/16 wide
                drawFace(i2, j1, k1, u0, v0, i2, j0, k0, u1, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i2, j1, k2, u0, v0, i2, j0, k0, u2, v1);
            }

            if (edges && !connectWest && !connectEast) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.SOUTH_FACE);
                drawFace(i1, j1, k2, u1, v0, i2, j0, k2, u2, v1);
            }
        } else if (connectSouth) {
            // south half-pane
            setupTileCoords(BlockOrientation.WEST_FACE);
            if (connectWest) {
                // inner corner: 7/16 wide
                drawFace(i1, j1, k2, u0, v0, i1, j0, k3, u1, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i1, j1, k1, u0, v0, i1, j0, k3, u2, v1);
            }

            setupTileCoords(BlockOrientation.EAST_FACE);
            if (connectEast) {
                // inner corner: 7/16 wide
                drawFace(i2, j1, k3, u2, v0, i2, j0, k2, u3, v1);
            } else {
                // outer corner: 9/16 wide
                drawFace(i2, j1, k3, u1, v0, i2, j0, k1, u3, v1);
            }

            if (edges && !connectWest && !connectEast) {
                // pane edge: 1/8 wide
                setupTileCoords(BlockOrientation.NORTH_FACE);
                drawFace(i2, j1, k1, u1, v0, i1, j0, k1, u2, v1);
            }
        }
    }

    private static void setupTileCoords(int face) {
        IIcon icon = icons[face];
        u0 = icon.getMinU();
        u1 = icon.getInterpolatedU(u1Scaled);
        u2 = icon.getInterpolatedU(u2Scaled);
        u3 = icon.getMaxU();
        v0 = icon.getMinV();
        v1 = icon.getMaxV();
    }

    private static void drawFace(double x0, double y0, double z0, double u0, double v0, // top left
        double x1, double y1, double z1, double u1, double v1) { // lower right
        Tessellator tessellator = Tessellator.instance;
        tessellator.addVertexWithUV(x0, y0, z0, u0, v0);
        tessellator.addVertexWithUV(x0, y1, z0, u0, v1);
        tessellator.addVertexWithUV(x1, y1, z1, u1, v1);
        tessellator.addVertexWithUV(x1, y0, z1, u1, v0);
    }

    static void clear() {
        Arrays.fill(icons, null);
        skipPaneRendering = false;
    }
}
