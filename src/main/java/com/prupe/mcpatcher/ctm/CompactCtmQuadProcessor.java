package com.prupe.mcpatcher.ctm;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;

public final class CompactCtmQuadProcessor {

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
     * Quadrant side references:
     * 0 = top-left
     * 1 = top-right
     * 2 = bottom-right
     * 3 = bottom-left
     */
    private static final int[] QUAD_A    = {RenderBlockState.REL_L, RenderBlockState.REL_U,
                                            RenderBlockState.REL_R, RenderBlockState.REL_D};
    private static final int[] QUAD_B    = {RenderBlockState.REL_U, RenderBlockState.REL_R,
                                            RenderBlockState.REL_D, RenderBlockState.REL_L};
    private static final int[] QUAD_DIAG = {RenderBlockState.REL_UL, RenderBlockState.REL_UR,
                                            RenderBlockState.REL_DR, RenderBlockState.REL_DL};

    /** Maps split-face quadrant (0=TL,1=TR,2=BR,3=BL face-local) to RenderBlocks per-face TL/TR/BR/BL orientation. */
    private static final int[][] RENDER_FACE_QUADRANT_MAP = {
        {3, 2, 1, 0}, // Y-
        {2, 3, 0, 1}, // Y+
        {3, 0, 1, 2}, // Z-
        {0, 1, 2, 3}, // Z+
        {3, 0, 1, 2}, // X-
        {1, 2, 3, 0}  // X+
    };
    private static final float[] QUADRANT_MIN_U = {0.0f, 0.5f, 0.5f, 0.0f};
    private static final float[] QUADRANT_MIN_V = {0.0f, 0.0f, 0.5f, 0.5f};

    private final IIcon[] sprites;
    private final CompactConnectingCtmProperties props;
    private final TileOverrideImpl.CTMCompact override;

    CompactCtmQuadProcessor(IIcon[] sprites, CompactConnectingCtmProperties props, TileOverrideImpl.CTMCompact compact) {
        this.sprites = new IIcon[5];
        if (sprites != null) {
            System.arraycopy(sprites, 0, this.sprites, 0, Math.min(sprites.length, this.sprites.length));
        }
        this.props = props;
        this.override = compact;
    }

    public boolean processFace(RenderBlocks rb, RenderBlockState renderBlockState, IIcon origIcon) {
        int neighborBits = 0;
        for (int bit = 0; bit < 8; bit++) {
            if (override.shouldConnect(renderBlockState, origIcon, bit)) {
                neighborBits |= (1 << bit);
            }
        }

        int ctmIndex = CTM_47_BY_CONNECTIONS[neighborBits & 0xFF];
        IIcon replacement = getReplacementIcon(ctmIndex);
        if (replacement != null) {
            renderWholeFace(rb, renderBlockState, replacement);
            return true;
        }

        if (neighborBits == 0 || neighborBits == 0xFF) {
            IIcon sprite = sprites[getCompactSpriteIndex(neighborBits)];
            if (sprite != null) {
                renderWholeFace(rb, renderBlockState, sprite);
                return true;
            }
            return false;
        }

        renderSplitFace(rb, renderBlockState, neighborBits);
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

        if ((connections & ((1 << RenderBlockState.REL_L) | (1 << RenderBlockState.REL_R))) ==
            ((1 << RenderBlockState.REL_L) | (1 << RenderBlockState.REL_R))) {
            return SPRITE_HORIZONTAL;
        }

        if ((connections & ((1 << RenderBlockState.REL_U) | (1 << RenderBlockState.REL_D))) ==
            ((1 << RenderBlockState.REL_U) | (1 << RenderBlockState.REL_D))) {
            return SPRITE_VERTICAL;
        }

        return SPRITE_DEFAULT;
    }

    private void renderSplitFace(RenderBlocks rb, RenderBlockState renderBlockState, int connections) {
        final int face = renderBlockState.getBlockFace();

        final double minX = rb.renderMinX, minY = rb.renderMinY, minZ = rb.renderMinZ;
        final double maxX = rb.renderMaxX, maxY = rb.renderMaxY, maxZ = rb.renderMaxZ;

        // Without AO the corner color/brightness fields are unused; skip the lighting dance.
        final boolean ao = rb.enableAO;

        final float rTL, rTR, rBL, rBR, gTL, gTR, gBL, gBR, bTL, bTR, bBL, bBR;
        final int brTL, brTR, brBL, brBR;
        if (ao) {
            rTL = rb.colorRedTopLeft;       rTR = rb.colorRedTopRight;
            rBL = rb.colorRedBottomLeft;    rBR = rb.colorRedBottomRight;
            gTL = rb.colorGreenTopLeft;     gTR = rb.colorGreenTopRight;
            gBL = rb.colorGreenBottomLeft;  gBR = rb.colorGreenBottomRight;
            bTL = rb.colorBlueTopLeft;      bTR = rb.colorBlueTopRight;
            bBL = rb.colorBlueBottomLeft;   bBR = rb.colorBlueBottomRight;
            brTL = rb.brightnessTopLeft;    brTR = rb.brightnessTopRight;
            brBL = rb.brightnessBottomLeft; brBR = rb.brightnessBottomRight;
        } else {
            rTL = rTR = rBL = rBR = 0f;
            gTL = gTR = gBL = gBR = 0f;
            bTL = bTR = bBL = bBR = 0f;
            brTL = brTR = brBL = brBR = 0;
        }

        for (int q = 0; q < 4; q++) {
            final IIcon icon = sprites[getQuadrantSprite(q, connections)];
            if (icon == null) {
                continue;
            }

            if (ao) {
                applyQuadrantLighting(rb, face, q, rTL, rTR, rBL, rBR, gTL, gTR, gBL, gBR, bTL, bTR, bBL, bBR, brTL, brTR, brBL, brBR);
            }
            setQuadrantBounds(rb, face, q, minX, minY, minZ, maxX, maxY, maxZ);
            rb.overrideBlockTexture = icon;
            renderFace(rb, renderBlockState, icon);
        }

        if (ao) {
            rb.colorRedTopLeft = rTL;       rb.colorRedTopRight = rTR;
            rb.colorRedBottomLeft = rBL;    rb.colorRedBottomRight = rBR;
            rb.colorGreenTopLeft = gTL;     rb.colorGreenTopRight = gTR;
            rb.colorGreenBottomLeft = gBL;  rb.colorGreenBottomRight = gBR;
            rb.colorBlueTopLeft = bTL;      rb.colorBlueTopRight = bTR;
            rb.colorBlueBottomLeft = bBL;   rb.colorBlueBottomRight = bBR;
            rb.brightnessTopLeft = brTL;    rb.brightnessTopRight = brTR;
            rb.brightnessBottomLeft = brBL; rb.brightnessBottomRight = brBR;
        }
        rb.overrideBlockTexture = null;
        rb.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void applyQuadrantLighting(RenderBlocks rb, int face, int quadrant, float rTL, float rTR, float rBL, float rBR, float gTL, float gTR, float gBL, float gBR, float bTL, float bTR, float bBL, float bBR, int brTL, int brTR, int brBL, int brBR) {
        final int rq = RENDER_FACE_QUADRANT_MAP[face][quadrant];
        final float u0 = QUADRANT_MIN_U[rq], u1 = u0 + 0.5f;
        final float v0 = QUADRANT_MIN_V[rq], v1 = v0 + 0.5f;

        rb.colorRedTopLeft      = bilerp(rTL, rTR, rBL, rBR, u0, v0);
        rb.colorRedTopRight     = bilerp(rTL, rTR, rBL, rBR, u1, v0);
        rb.colorRedBottomLeft   = bilerp(rTL, rTR, rBL, rBR, u0, v1);
        rb.colorRedBottomRight  = bilerp(rTL, rTR, rBL, rBR, u1, v1);

        rb.colorGreenTopLeft     = bilerp(gTL, gTR, gBL, gBR, u0, v0);
        rb.colorGreenTopRight    = bilerp(gTL, gTR, gBL, gBR, u1, v0);
        rb.colorGreenBottomLeft  = bilerp(gTL, gTR, gBL, gBR, u0, v1);
        rb.colorGreenBottomRight = bilerp(gTL, gTR, gBL, gBR, u1, v1);

        rb.colorBlueTopLeft      = bilerp(bTL, bTR, bBL, bBR, u0, v0);
        rb.colorBlueTopRight     = bilerp(bTL, bTR, bBL, bBR, u1, v0);
        rb.colorBlueBottomLeft   = bilerp(bTL, bTR, bBL, bBR, u0, v1);
        rb.colorBlueBottomRight  = bilerp(bTL, bTR, bBL, bBR, u1, v1);

        rb.brightnessTopLeft     = bilerpLight(brTL, brTR, brBL, brBR, u0, v0);
        rb.brightnessTopRight    = bilerpLight(brTL, brTR, brBL, brBR, u1, v0);
        rb.brightnessBottomLeft  = bilerpLight(brTL, brTR, brBL, brBR, u0, v1);
        rb.brightnessBottomRight = bilerpLight(brTL, brTR, brBL, brBR, u1, v1);
    }

    private static float bilerp(float c00, float c10, float c01, float c11, float u, float v) {
        final float top = c00 + (c10 - c00) * u;
        final float bot = c01 + (c11 - c01) * u;
        return top + (bot - top) * v;
    }

    /** Brightness packs sky in upper 16 bits, block in lower; bilerp each independently then repack. */
    private static int bilerpLight(int c00, int c10, int c01, int c11, float u, float v) {
        final int b00 = c00 & 0xFFFF, b10 = c10 & 0xFFFF, b01 = c01 & 0xFFFF, b11 = c11 & 0xFFFF;
        final int s00 = c00 >>> 16,   s10 = c10 >>> 16,   s01 = c01 >>> 16,   s11 = c11 >>> 16;

        final float bTop = b00 + (b10 - b00) * u;
        final float bBot = b01 + (b11 - b01) * u;
        final float sTop = s00 + (s10 - s00) * u;
        final float sBot = s01 + (s11 - s01) * u;

        final int block = Math.round(bTop + (bBot - bTop) * v);
        final int sky   = Math.round(sTop + (sBot - sTop) * v);
        return (sky << 16) | (block & 0xFFFF);
    }

    private void renderWholeFace(RenderBlocks rb, RenderBlockState renderBlockState, IIcon icon) {
        IIcon saved = rb.overrideBlockTexture;
        rb.overrideBlockTexture = icon;
        try {
            renderFace(rb, renderBlockState, icon);
        } finally {
            rb.overrideBlockTexture = saved;
        }
    }

    private void renderFace(RenderBlocks rb, RenderBlockState renderBlockState, IIcon icon) {
        int face = renderBlockState.getBlockFace();
        Block block = renderBlockState.getBlock();
        int x = renderBlockState.getX();
        int y = renderBlockState.getY();
        int z = renderBlockState.getZ();
        boolean originalFlip = rb.field_152631_f;
        rb.field_152631_f = (face == 2 || face == 5);
        try {
            switch (face) {
                case 0 -> rb.renderFaceYNeg(block, x, y, z, icon);
                case 1 -> rb.renderFaceYPos(block, x, y, z, icon);
                case 2 -> rb.renderFaceZNeg(block, x, y, z, icon);
                case 3 -> rb.renderFaceZPos(block, x, y, z, icon);
                case 4 -> rb.renderFaceXNeg(block, x, y, z, icon);
                case 5 -> rb.renderFaceXPos(block, x, y, z, icon);
            }
        } finally {
            rb.field_152631_f = originalFlip;
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
            return (connectedBit == RenderBlockState.REL_L || connectedBit == RenderBlockState.REL_R) ? SPRITE_VERTICAL
                : SPRITE_HORIZONTAL;
        }

        return SPRITE_DEFAULT;
    }

    @Desugar
    private record FaceBasis(int rightX, int rightY, int rightZ, int downX, int downY, int downZ) { }
}
