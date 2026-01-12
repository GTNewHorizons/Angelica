package me.jellysquid.mods.sodium.client.render.chunk;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.HORIZONTAL_DIRECTIONS;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.NEG_X;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.NEG_Z;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.POS_X;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.POS_Z;
import java.util.Collection;
import net.minecraftforge.common.util.ForgeDirection;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;

public class ChunkRenderColumn<T extends ChunkGraphicsState> {
    private final Int2ObjectMap<ChunkRenderContainer<T>> renders = new Int2ObjectOpenHashMap<>();

    @SuppressWarnings("unchecked")
    private final ChunkRenderColumn<T>[] adjacent = new ChunkRenderColumn[6];

    @Getter
    private final int x;
    @Getter
    private final int z;

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
        if (render == null) {
            this.renders.remove(y);
        } else {
            this.renders.put(y, render);
        }
    }

    public ChunkRenderContainer<T> getRender(int y) {
        return this.renders.get(y);
    }

    public Collection<ChunkRenderContainer<T>> getAllRenders() {
        return this.renders.values();
    }

    public boolean areNeighborsPresent() {
        for (var dir : HORIZONTAL_DIRECTIONS) {
            ChunkRenderColumn<T> adj = this.adjacent[dir.ordinal()];

            if (adj == null) {
                return false;
            }

            ForgeDirection corner;

            // Access the adjacent corner chunk from the neighbor in this direction
            if (dir == NEG_Z) {
                corner = ForgeDirection.EAST;
            } else if (dir == POS_Z) {
                corner = ForgeDirection.WEST;
            } else if (dir == NEG_X) {
                corner = ForgeDirection.NORTH;
            } else if (dir == POS_X) {
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
