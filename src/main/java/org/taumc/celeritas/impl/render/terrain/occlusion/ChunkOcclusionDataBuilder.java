package org.taumc.celeritas.impl.render.terrain.occlusion;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;

/**
 * WARNING: Minecraft 1.16 code rip!!
 */

public class ChunkOcclusionDataBuilder {
    private static final int STEP_X = (int)Math.pow(16.0, 0.0);
    private static final int STEP_Z = (int)Math.pow(16.0, 1.0);
    private static final int STEP_Y = (int)Math.pow(16.0, 2.0);
    private static final ForgeDirection[] DIRECTIONS = ForgeDirection.VALID_DIRECTIONS;
    private final BitSet closed = new BitSet(4096);
    private static final int[] EDGE_POINTS = new int[1352];

    static {
        int k = 0;

        for(int l = 0; l < 16; ++l) {
            for(int m = 0; m < 16; ++m) {
                for(int n = 0; n < 16; ++n) {
                    if (l == 0 || l == 15 || m == 0 || m == 15 || n == 0 || n == 15) {
                        EDGE_POINTS[k++] = pack(l, m, n);
                    }
                }
            }
        }
    }
    private int openCount = 4096;

    public void markClosed(BlockPos pos) {
        this.closed.set(pack(pos), true);
        --this.openCount;
    }

    private static int pack(BlockPos pos) {
        return pack(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private static int pack(int x, int y, int z) {
        return x << 0 | y << 8 | z << 4;
    }

    public ChunkOcclusionData build() {
        final ChunkOcclusionData lv = new ChunkOcclusionData();
        if (4096 - this.openCount < 256) {
            lv.fill(true);
        } else if (this.openCount == 0) {
            lv.fill(false);
        } else {
            for(int i : EDGE_POINTS) {
                if (!this.closed.get(i)) {
                    lv.addOpenEdgeFaces(this.getOpenFaces(i));
                }
            }
        }

        return lv;
    }

    private Set<ForgeDirection> getOpenFaces(int pos) {
        final Set<ForgeDirection> set = EnumSet.noneOf(ForgeDirection.class);
        final IntPriorityQueue intPriorityQueue = new IntArrayFIFOQueue();
        intPriorityQueue.enqueue(pos);
        this.closed.set(pos, true);

        while(!intPriorityQueue.isEmpty()) {
            final int j = intPriorityQueue.dequeueInt();
            this.addEdgeFaces(j, set);

            for(ForgeDirection lv : DIRECTIONS) {
                final int k = this.offset(j, lv);
                if (k >= 0 && !this.closed.get(k)) {
                    this.closed.set(k, true);
                    intPriorityQueue.enqueue(k);
                }
            }
        }

        return set;
    }

    private void addEdgeFaces(int pos, Set<ForgeDirection> openFaces) {
        final int j = pos >> 0 & 15;
        if (j == 0) {
            openFaces.add(ForgeDirection.WEST);
        } else if (j == 15) {
            openFaces.add(ForgeDirection.EAST);
        }

        final int k = pos >> 8 & 15;
        if (k == 0) {
            openFaces.add(ForgeDirection.DOWN);
        } else if (k == 15) {
            openFaces.add(ForgeDirection.UP);
        }

        final int l = pos >> 4 & 15;
        if (l == 0) {
            openFaces.add(ForgeDirection.NORTH);
        } else if (l == 15) {
            openFaces.add(ForgeDirection.SOUTH);
        }
    }

    private int offset(int pos, ForgeDirection arg) {
        return switch (arg) {
            case DOWN -> {
                if ((pos >> 8 & 15) == 0) {
                    yield -1;
                }
                yield pos - STEP_Y;
            }
            case UP -> {
                if ((pos >> 8 & 15) == 15) {
                    yield -1;
                }
                yield pos + STEP_Y;
            }
            case NORTH -> {
                if ((pos >> 4 & 15) == 0) {
                    yield -1;
                }
                yield pos - STEP_Z;
            }
            case SOUTH -> {
                if ((pos >> 4 & 15) == 15) {
                    yield -1;
                }
                yield pos + STEP_Z;
            }
            case WEST -> {
                if ((pos >> 0 & 15) == 0) {
                    yield -1;
                }
                yield pos - STEP_X;
            }
            case EAST -> {
                if ((pos >> 0 & 15) == 15) {
                    yield -1;
                }
                yield pos + STEP_X;
            }
            default -> -1;
        };
    }
}
