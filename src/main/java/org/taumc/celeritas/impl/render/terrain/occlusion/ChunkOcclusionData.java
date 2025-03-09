package org.taumc.celeritas.impl.render.terrain.occlusion;

import net.minecraftforge.common.util.ForgeDirection;

import java.util.BitSet;
import java.util.Set;

public class ChunkOcclusionData {
    private static final int DIRECTION_COUNT = ForgeDirection.values().length;
    private final BitSet visibility;

    public ChunkOcclusionData() {
        this.visibility = new BitSet(DIRECTION_COUNT * DIRECTION_COUNT);
    }

    public void addOpenEdgeFaces(Set<ForgeDirection> faces) {
        for (ForgeDirection dirFrom : faces) {
            for (ForgeDirection dirTo : faces) {
                this.setVisibleThrough(dirFrom, dirTo, true);
            }
        }

        for (ForgeDirection direction : faces) {
            this.visibility.set(direction.ordinal() * DIRECTION_COUNT + direction.ordinal());
        }
    }
    public void setVisibleThrough(ForgeDirection from, ForgeDirection to, boolean visible) {
        this.visibility.set(from.ordinal() + to.ordinal() * DIRECTION_COUNT, visible);
        this.visibility.set(to.ordinal() + from.ordinal() * DIRECTION_COUNT, visible);
    }

    public boolean isVisibleThrough(ForgeDirection from, ForgeDirection to) {
        return this.visibility.get(from.ordinal() + to.ordinal() * DIRECTION_COUNT);
    }

    public void fill(boolean visible) {
        this.visibility.set(0, this.visibility.size(), visible);
    }
}
