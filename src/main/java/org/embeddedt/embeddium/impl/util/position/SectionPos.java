package org.embeddedt.embeddium.impl.util.position;

import org.joml.Vector3i;

public final class SectionPos extends Vector3i {
    public SectionPos(int x, int y, int z) {
        super(x, y, z);
    }

    public int minX() {
        return x() * 16;
    }

    public int minY() {
        return y() * 16;
    }

    public int minZ() {
        return z() * 16;
    }

    public int maxX() {
        return minX() + 15;
    }

    public int maxY() {
        return minY() + 15;
    }

    public int maxZ() {
        return minZ() + 15;
    }
}
