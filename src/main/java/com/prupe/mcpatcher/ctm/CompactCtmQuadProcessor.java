package com.prupe.mcpatcher.ctm;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public final class CompactCtmQuadProcessor {

    private static final int LEFT = 0;
    private static final int UP_LEFT = 1;
    private static final int UP = 2;
    private static final int UP_RIGHT = 3;
    private static final int RIGHT = 4;
    private static final int DOWN_RIGHT = 5;
    private static final int DOWN = 6;
    private static final int DOWN_LEFT = 7;

    private static final int SPRITE_DEFAULT = 0;
    private static final int SPRITE_ALL = 1;
    private static final int SPRITE_HORIZONTAL = 2;
    private static final int SPRITE_VERTICAL = 3;
    private static final int SPRITE_MIXED = 4;

    private static final int[] CTM_47_BY_CONNECTIONS = {
        0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,
        1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,
        0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,
        1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,
        36,17,36,17,24,19,24,43,36,17,36,17,24,19,24,43,
        16,18,16,18,6,46,6,21,16,18,16,18,28,9,28,22,
        36,17,36,17,24,19,24,43,36,17,36,17,24,19,24,43,
        37,40,37,40,30,8,30,34,37,40,37,40,25,23,25,45,
        0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,
        1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,
        0,3,0,3,12,5,12,15,0,3,0,3,12,5,12,15,
        1,2,1,2,4,7,4,29,1,2,1,2,13,31,13,14,
        36,39,36,39,24,41,24,27,36,39,36,39,24,41,24,27,
        16,42,16,42,6,20,6,10,16,42,16,42,28,35,28,44,
        36,39,36,39,24,41,24,27,36,39,36,39,24,41,24,27,
        37,38,37,38,30,11,30,32,37,38,37,38,25,33,25,26
    };

    /**
     * Face-local basis:
     * right = increasing U on the face
     * down  = increasing V on the face
     */
    private static final FaceBasis[] FACE_BASIS = {
        new FaceBasis( 1,  0,  0,  0,  0,  1), // 0: Y-
        new FaceBasis( 1,  0,  0,  0,  0,  1), // 1: Y+
        new FaceBasis(-1,  0,  0,  0, -1,  0), // 2: Z-
        new FaceBasis( 1,  0,  0,  0, -1,  0), // 3: Z+
        new FaceBasis( 0,  0,  1,  0, -1,  0), // 4: X-
        new FaceBasis( 0,  0, -1,  0, -1,  0)  // 5: X+
    };

    /**
     * Neighbor offsets in face-local coordinates:
     * 0 = left, 1 = up-left, 2 = up, 3 = up-right,
     * 4 = right, 5 = down-right, 6 = down, 7 = down-left
     */
    private static final int[] RIGHT_FACTOR = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] DOWN_FACTOR  = { 0, -1, -1, -1, 0, 1, 1, 1};

    /**
     * Quadrant side references:
     * 0 = top-left
     * 1 = top-right
     * 2 = bottom-right
     * 3 = bottom-left
     */
    private static final int[] QUAD_A = {LEFT, UP, RIGHT, DOWN};
    private static final int[] QUAD_B = {UP, RIGHT, DOWN, LEFT};
    private static final int[] QUAD_DIAG = {UP_LEFT, UP_RIGHT, DOWN_RIGHT, DOWN_LEFT};

    private final IIcon[] sprites;
    private final CompactConnectingCtmProperties props;

    public CompactCtmQuadProcessor(IIcon[] sprites, CompactConnectingCtmProperties props) {
        this.sprites = new IIcon[5];
        if (sprites != null) {
            System.arraycopy(sprites, 0, this.sprites, 0, Math.min(sprites.length, this.sprites.length));
        }
        this.props = props;
    }

    public boolean processFace(RenderBlocks rb, IBlockAccess world, int x, int y, int z, int face, IIcon origIcon) {
        int connections = CtmConnectionHelper.getConnections(world, x, y, z, face, props.getInnerSeams());

        int ctmIndex = CTM_47_BY_CONNECTIONS[connections & 0xFF];
        IIcon replacement = getReplacementIcon(ctmIndex);
        if (replacement != null) {
            rb.overrideBlockTexture = replacement;
            return false;
        }

        if (connections == 0 || connections == 0xFF) {
            IIcon sprite = sprites[getCompactSpriteIndex(connections)];
            if (sprite != null) {
                rb.overrideBlockTexture = sprite;
            }
            return false;
        }

        renderSplitFace(rb, world, x, y, z, face, connections);
        return true;
    }

    private IIcon getReplacementIcon(int ctmIndex) {
        Int2IntMap map = props.getTileReplacementMap();
        if (map == null || !map.containsKey(ctmIndex)) {
            return null;
        }

        int spriteIndex = map.get(ctmIndex);
        if (spriteIndex < 0 || spriteIndex >= sprites.length) {
            return null;
        }

        return sprites[spriteIndex];
    }

    /**
     * Compact 5-sprite variant:
     * 0 = default / isolated / mixed
     * 1 = all neighbors
     * 2 = horizontal line
     * 3 = vertical line
     */
    private int getCompactSpriteIndex(int connections) {
        if (connections == 0xFF) {
            return SPRITE_ALL;
        }

        if ((connections & ((1 << LEFT) | (1 << RIGHT))) == ((1 << LEFT) | (1 << RIGHT))) {
            return SPRITE_HORIZONTAL;
        }

        if ((connections & ((1 << UP) | (1 << DOWN))) == ((1 << UP) | (1 << DOWN))) {
            return SPRITE_VERTICAL;
        }

        return SPRITE_DEFAULT;
    }

    private void renderSplitFace(RenderBlocks rb, IBlockAccess world, int x, int y, int z, int face, int connections) {
        Block block = world.getBlock(x, y, z);

        double minX = rb.renderMinX;
        double minY = rb.renderMinY;
        double minZ = rb.renderMinZ;
        double maxX = rb.renderMaxX;
        double maxY = rb.renderMaxY;
        double maxZ = rb.renderMaxZ;

        for (int q = 0; q < 4; q++) {
            IIcon icon = sprites[getQuadrantSprite(q, connections)];
            if (icon == null) {
                continue;
            }

            setQuadrantBounds(rb, face, q, minX, minY, minZ, maxX, maxY, maxZ);
            rb.overrideBlockTexture = icon;
            renderFace(rb, block, x, y, z, face, icon);
        }

        rb.overrideBlockTexture = null;
        rb.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void renderFace(RenderBlocks rb, Block block, int x, int y, int z, int face, IIcon icon) {
        switch (face) {
            case 0: rb.renderFaceYNeg(block, x, y, z, icon); break;
            case 1: rb.renderFaceYPos(block, x, y, z, icon); break;
            case 2: rb.renderFaceZNeg(block, x, y, z, icon); break;
            case 3: rb.renderFaceZPos(block, x, y, z, icon); break;
            case 4: rb.renderFaceXNeg(block, x, y, z, icon); break;
            case 5: rb.renderFaceXPos(block, x, y, z, icon); break;
            default: break;
        }
    }

    /**
     * Quadrants are always interpreted as:
     * 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left
     * in the face-local view.
     */
    private void setQuadrantBounds(RenderBlocks rb, int face, int quadrant,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        double midX = (minX + maxX) * 0.5;
        double midY = (minY + maxY) * 0.5;
        double midZ = (minZ + maxZ) * 0.5;

        switch (face) {
            case 0:
            case 1:
                if (quadrant == 0) rb.setRenderBounds(minX, minY, minZ, midX, maxY, midZ);
                if (quadrant == 1) rb.setRenderBounds(midX, minY, minZ, maxX, maxY, midZ);
                if (quadrant == 2) rb.setRenderBounds(midX, minY, midZ, maxX, maxY, maxZ);
                if (quadrant == 3) rb.setRenderBounds(minX, minY, midZ, midX, maxY, maxZ);
                break;

            case 2:
                if (quadrant == 0) rb.setRenderBounds(midX, midY, minZ, maxX, maxY, maxZ);
                if (quadrant == 1) rb.setRenderBounds(minX, midY, minZ, midX, maxY, maxZ);
                if (quadrant == 2) rb.setRenderBounds(minX, minY, minZ, midX, midY, maxZ);
                if (quadrant == 3) rb.setRenderBounds(midX, minY, minZ, maxX, midY, maxZ);
                break;

            case 3:
                if (quadrant == 0) rb.setRenderBounds(minX, midY, minZ, midX, maxY, maxZ);
                if (quadrant == 1) rb.setRenderBounds(midX, midY, minZ, maxX, maxY, maxZ);
                if (quadrant == 2) rb.setRenderBounds(midX, minY, minZ, maxX, midY, maxZ);
                if (quadrant == 3) rb.setRenderBounds(minX, minY, minZ, midX, midY, maxZ);
                break;

            case 4:
                if (quadrant == 0) rb.setRenderBounds(minX, midY, minZ, maxX, maxY, midZ);
                if (quadrant == 1) rb.setRenderBounds(minX, midY, midZ, maxX, maxY, maxZ);
                if (quadrant == 2) rb.setRenderBounds(minX, minY, midZ, maxX, midY, maxZ);
                if (quadrant == 3) rb.setRenderBounds(minX, minY, minZ, maxX, midY, midZ);
                break;

            case 5:
                if (quadrant == 0) rb.setRenderBounds(minX, midY, midZ, maxX, maxY, maxZ);
                if (quadrant == 1) rb.setRenderBounds(minX, midY, minZ, maxX, maxY, midZ);
                if (quadrant == 2) rb.setRenderBounds(minX, minY, minZ, maxX, midY, midZ);
                if (quadrant == 3) rb.setRenderBounds(minX, minY, midZ, maxX, midY, maxZ);
                break;

            default:
                break;
        }
    }

    /**
     * A quadrant is built from two cardinal sides and the diagonal between them.
     */
    private int getQuadrantSprite(int quadrant, int connections) {
        int sideA = QUAD_A[quadrant];
        int sideB = QUAD_B[quadrant];
        int diag = QUAD_DIAG[quadrant];

        boolean connA = (connections & (1 << sideA)) != 0;
        boolean connB = (connections & (1 << sideB)) != 0;

        if (connA && connB) {
            return (connections & (1 << diag)) != 0 ? SPRITE_ALL : SPRITE_MIXED;
        }

        if (connA ^ connB) {
            int connectedBit = connA ? sideA : sideB;
            return (connectedBit == LEFT || connectedBit == RIGHT) ? SPRITE_VERTICAL : SPRITE_HORIZONTAL;
        }

        return SPRITE_DEFAULT;
    }

    @Desugar
    private record FaceBasis(int rightX, int rightY, int rightZ, int downX, int downY, int downZ) { }

    private static final class CtmConnectionHelper {

        public static int getConnections(IBlockAccess world, int x, int y, int z, int face, boolean innerSeams) {
            Block self = world.getBlock(x, y, z);
            FaceBasis basis = FACE_BASIS[face];

            int bits = 0;

            for (int i = 0; i < 8; i++) {
                int dx = RIGHT_FACTOR[i] * basis.rightX + DOWN_FACTOR[i] * basis.downX;
                int dy = RIGHT_FACTOR[i] * basis.rightY + DOWN_FACTOR[i] * basis.downY;
                int dz = RIGHT_FACTOR[i] * basis.rightZ + DOWN_FACTOR[i] * basis.downZ;

                int nx = x + dx;
                int ny = y + dy;
                int nz = z + dz;

                if (world.getBlock(nx, ny, nz) != self) {
                    continue;
                }

                if (innerSeams && !shouldRenderInnerSeam(world, nx, ny, nz, face)) {
                    continue;
                }

                bits |= (1 << i);
            }

            return bits;
        }

        private static boolean shouldRenderInnerSeam(IBlockAccess world, int x, int y, int z, int face) {
            int[] normal = RenderBlockState.NORMALS[face];
            int sx = x + normal[0];
            int sy = y + normal[1];
            int sz = z + normal[2];
            return world.getBlock(sx, sy, sz).shouldSideBeRendered(world, sx, sy, sz, face);
        }
    }
}
