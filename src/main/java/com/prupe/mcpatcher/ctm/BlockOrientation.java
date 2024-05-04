package com.prupe.mcpatcher.ctm;

import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.prupe.mcpatcher.mal.block.BlockStateMatcher;

final class BlockOrientation extends RenderBlockState {

    // NEIGHBOR_OFFSETS[a][b][c] = offset from starting block
    // a: face 0-5
    // b: neighbor 0-7
    // 7 6 5
    // 0 * 4
    // 1 2 3
    // c: coordinate (x,y,z) 0-2
    private static final int[][][] NEIGHBOR_OFFSET = new int[][][] {
        makeNeighborOffset(WEST_FACE, SOUTH_FACE, EAST_FACE, NORTH_FACE), // BOTTOM_FACE
        makeNeighborOffset(WEST_FACE, SOUTH_FACE, EAST_FACE, NORTH_FACE), // TOP_FACE
        makeNeighborOffset(EAST_FACE, BOTTOM_FACE, WEST_FACE, TOP_FACE), // NORTH_FACE
        makeNeighborOffset(WEST_FACE, BOTTOM_FACE, EAST_FACE, TOP_FACE), // SOUTH_FACE
        makeNeighborOffset(NORTH_FACE, BOTTOM_FACE, SOUTH_FACE, TOP_FACE), // WEST_FACE
        makeNeighborOffset(SOUTH_FACE, BOTTOM_FACE, NORTH_FACE, TOP_FACE), // EAST_FACE
    };

    private static final int[][] ROTATE_UV_MAP = new int[][] {
        { WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, 2, -2, 0, 0 },
        { NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, 2 },
        { WEST_FACE, EAST_FACE, NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, 2, -2, -2, -2, 0, 0 },
        { NORTH_FACE, SOUTH_FACE, TOP_FACE, BOTTOM_FACE, WEST_FACE, EAST_FACE, 0, 0, 0, 0, -2, -2 }, };

    private int i;
    private int j;
    private int k;
    private int metadata;
    private int altMetadata;
    private int metadataBits;
    private int renderType;

    private int blockFace;
    private int textureFace;
    private int textureFaceOrig;
    private int rotateUV;

    @Override
    public void clear() {
        super.clear();
        i = j = k = 0;
        renderType = -1;
        metadata = 0;
        blockFace = textureFace = 0;
        rotateUV = 0;
        offsetsComputed = false;
        haveOffsets = false;
        di = dj = dk = 0;
    }

    @Override
    public int getI() {
        return i;
    }

    @Override
    public int getJ() {
        return j;
    }

    @Override
    public int getK() {
        return k;
    }

    @Override
    public int getBlockFace() {
        return blockFace;
    }

    @Override
    public int getTextureFace() {
        return textureFace;
    }

    @Override
    public int getTextureFaceOrig() {
        return textureFaceOrig;
    }

    @Override
    public String getTextureFaceName() {
        throw new UnsupportedOperationException("getTextureName");
    }

    @Override
    public int getFaceForHV() {
        return blockFace;
    }

    @Override
    public boolean match(BlockStateMatcher matcher) {
        return isInWorld() ? matcher.match(blockAccess, i, j, k) : matcher.match(block, metadata);
    }

    @Override
    public int[] getOffset(int blockFace, int relativeDirection) {
        return NEIGHBOR_OFFSET[blockFace][rotateUV(relativeDirection)];
    }

    @Override
    public boolean setCoordOffsetsForRenderType() {
        if (offsetsComputed) {
            return haveOffsets;
        }
        offsetsComputed = true;
        haveOffsets = false;
        di = dj = dk = 0;
        switch (renderType) {
            case 1 -> { // renderCrossedSquares
                while (j + dj > 0 && block == blockAccess.getBlock(i, j + dj - 1, k)) {
                    dj--;
                    haveOffsets = true;
                }
            } // renderBlockDoor
            case 7, 40 -> { // renderBlockDoublePlant
                if ((metadata & 0x8) != 0 && block == blockAccess.getBlock(i, j - 1, k)) {
                    dj--;
                    haveOffsets = true;
                }
            }
            case 14 -> { // renderBlockBed
                metadata = blockAccess.getBlockMetadata(i, j, k);
                switch (metadata) {
                    case 0, 4 -> dk = 1; // head is one block south
                    case 1, 5 -> di = -1; // head is one block west
                    case 2, 6 -> dk = -1; // head is one block north
                    case 3, 7 -> di = 1; // head is one block east
                    default -> {
                        return false; // head itself, no offset
                    }
                }
                haveOffsets = block == blockAccess.getBlock(i + di, j, k + dk);
            }
            default -> {
            }
        }
        return haveOffsets;
    }

    @Override
    public boolean shouldConnectByBlock(Block neighbor, int neighborX, int neighborY, int neighborZ) {
        return block == neighbor
            && (metadataBits & (1 << blockAccess.getBlockMetadata(neighborX, neighborY, neighborZ))) != 0;
    }

    @Override
    public boolean shouldConnectByTile(Block neighbor, IIcon origIcon, int neighborX, int neighborY, int neighborZ) {
        return origIcon == neighbor.getIcon(blockAccess, neighborX, neighborY, neighborZ, getTextureFaceOrig());
    }

    void setBlock(Block block, IBlockAccess blockAccess, int x, int y, int z) {
        this.block = block;
        this.blockAccess = blockAccess;
        inWorld = true;
        this.i = x;
        this.j = y;
        this.k = z;
        renderType = block.getRenderType();
        metadata = altMetadata = blockAccess.getBlockMetadata(x, y, z);
        offsetsComputed = false;
    }

    void setFace(int face) {
        blockFace = getBlockFaceByRenderType(face);
        textureFaceOrig = face;
        rotateUV = 0;
        textureFace = blockFaceToTextureFace(blockFace);
        metadataBits = (1 << metadata) | (1 << altMetadata);
    }

    void setBlockMetadata(Block block, int metadata, int face) {
        this.block = block;
        blockAccess = null;
        inWorld = false;
        i = j = k = 0;
        renderType = block.getRenderType();
        blockFace = textureFace = textureFaceOrig = face;
        this.metadata = metadata;
        metadataBits = 1 << metadata;
        di = dj = dk = 0;
        rotateUV = 0;
    }

    private int getBlockFaceByRenderType(int face) {
        switch (renderType) {
            case 1 -> { // renderCrossedSquares
                return NORTH_FACE;
            }
            case 8 -> { // renderBlockLadder
                switch (metadata) {
                    case 2, 3, 4, 5 -> {
                        return metadata;
                    }
                }
            }
            case 20 -> { // renderBlockVine
                switch (metadata) {
                    case 1 -> {
                        return NORTH_FACE;
                    }
                    case 2 -> {
                        return EAST_FACE;
                    }
                    case 4 -> {
                        return SOUTH_FACE;
                    }
                    case 8 -> {
                        return WEST_FACE;
                    }
                }
            }
        }
        return face;
    }

    private int blockFaceToTextureFace(int face) {
        switch (renderType) {
            case 31 -> { // renderBlockLog (also applies to hay)
                switch (metadata & 0xc) {
                    case 4 -> { // west-east
                        altMetadata &= ~0xc;
                        rotateUV = ROTATE_UV_MAP[0][face + 6];
                        return ROTATE_UV_MAP[0][face];
                    }
                    case 8 -> { // north-south
                        altMetadata &= ~0xc;
                        rotateUV = ROTATE_UV_MAP[1][face + 6];
                        return ROTATE_UV_MAP[1][face];
                    }
                }
            }
            case 39 -> { // renderBlockQuartz
                switch (metadata) {
                    case 3 -> { // north-south
                        altMetadata = 2;
                        rotateUV = ROTATE_UV_MAP[2][face + 6];
                        return ROTATE_UV_MAP[2][face];
                    }
                    case 4 -> { // west-east
                        altMetadata = 2;
                        rotateUV = ROTATE_UV_MAP[3][face + 6];
                        return ROTATE_UV_MAP[3][face];
                    }
                }
            }
        }
        return face;
    }

    private int rotateUV(int neighbor) {
        return (neighbor + rotateUV) & 7;
    }

}
