package me.jellysquid.mods.sodium.client.render.chunk;

import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraftforge.common.util.ForgeDirection;

public class ChunkRenderColumn<T extends ChunkGraphicsState> {
    @SuppressWarnings("unchecked")
    private final ChunkRenderContainer<T>[] renders = new ChunkRenderContainer[16];

    @SuppressWarnings("unchecked")
    private final ChunkRenderColumn<T>[] adjacent = new ChunkRenderColumn[6];

    private final int x, z;

    public ChunkRenderColumn(int x, int z) {
        this.x = x;
        this.z = z;

        this.setAdjacentColumn(ForgeDirection.UP, this);
        this.setAdjacentColumn(ForgeDirection.DOWN, this);
    }

    public void setAdjacentColumn(ForgeDirection dir, ChunkRenderColumn<T> column) {
        this.adjacent[dir.ordinal()] = column;
    }

    public ChunkRenderColumn<T> getAdjacentColumn(ForgeDirection dir) {
        return this.adjacent[dir.ordinal()];
    }

    public void setRender(int y, ChunkRenderContainer<T> render) {
        this.renders[y] = render;
    }

    public ChunkRenderContainer<T> getRender(int y) {
        if (y < 0 || y >= this.renders.length) {
            return null;
        }
        return this.renders[y];
    }

    public int getX() {
        return this.x;
    }

    public int getZ() {
        return this.z;
    }

    public boolean areNeighborsPresent() {
        for (ForgeDirection dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            ChunkRenderColumn<T> adj = this.adjacent[dir.ordinal()];

            if (adj == null) {
                return false;
            }

            ForgeDirection corner;

            // Access the adjacent corner chunk from the neighbor in this direction
            if (dir == ForgeDirection.NORTH) {
                corner = ForgeDirection.EAST;
            } else if (dir == ForgeDirection.SOUTH) {
                corner = ForgeDirection.WEST;
            } else if (dir == ForgeDirection.WEST) {
                corner = ForgeDirection.NORTH;
            } else if (dir == ForgeDirection.EAST) {
                corner = ForgeDirection.SOUTH;
            } else {
                continue;
            }

            // If no neighbor has been attached, the chunk is not present
            if (adj.getAdjacentColumn(corner) == null) {
                return false;
            }
        }

        return true;
    }
}
