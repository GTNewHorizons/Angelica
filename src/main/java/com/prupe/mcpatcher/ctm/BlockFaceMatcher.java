package com.prupe.mcpatcher.ctm;

import static com.prupe.mcpatcher.ctm.RenderBlockState.BOTTOM_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.EAST_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.NORTH_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.SOUTH_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.TOP_FACE;
import static com.prupe.mcpatcher.ctm.RenderBlockState.WEST_FACE;

import com.prupe.mcpatcher.MCPatcherUtils;

public class BlockFaceMatcher {

    private final int faces;

    protected BlockFaceMatcher(String[] values) {
        int flags = 0;
        for (String face : values) {
            switch (face) {
                case "bottom", "down" -> flags |= (1 << BOTTOM_FACE);
                case "top", "up" -> flags |= (1 << TOP_FACE);
                case "north" -> flags |= (1 << NORTH_FACE);
                case "south" -> flags |= (1 << SOUTH_FACE);
                case "east" -> flags |= (1 << EAST_FACE);
                case "west" -> flags |= (1 << WEST_FACE);
                case "side", "sides" -> flags |= (1 << NORTH_FACE) | (1 << SOUTH_FACE)
                    | (1 << EAST_FACE)
                    | (1 << WEST_FACE);
                case "all" -> flags = -1;
            }
        }
        faces = flags;
    }

    public boolean match(RenderBlockState renderBlockState) {
        int face = renderBlockState.getTextureFace();
        return face >= 0 && (faces & (1 << face)) != 0;
    }

    protected boolean isAll() {
        return faces == -1;
    }

    public static BlockFaceMatcher create(String propertyValue) {
        if (!MCPatcherUtils.isNullOrEmpty(propertyValue)) {
            String[] values = propertyValue.toLowerCase()
                .split("\\s+");
            try {
                BlockFaceMatcher matcher = new BlockFaceMatcher(values);
                if (!matcher.isAll()) {
                    return matcher;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
