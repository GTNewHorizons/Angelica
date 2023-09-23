package org.embeddedt.archaicfix.lighting.world.lighting;

/**
 * Stub for aiding in porting Phosphor. DO NOT USE in other code.
 */
public class BlockPos {
    protected int x, y, z;
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    BlockPos() {

    }

    public static class MutableBlockPos extends BlockPos {
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public MutableBlockPos setPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }
}
