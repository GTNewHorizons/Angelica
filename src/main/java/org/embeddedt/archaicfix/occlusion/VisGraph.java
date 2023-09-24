package org.embeddedt.archaicfix.occlusion;

import lombok.Getter;
import net.minecraft.util.EnumFacing;
import org.embeddedt.archaicfix.occlusion.util.IntStack;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;

public class VisGraph {

	private static final int X_OFFSET = (int) Math.pow(16.0D, 0.0D);
	private static final int Z_OFFSET = (int) Math.pow(16.0D, 1.0D);
	private static final int Y_OFFSET = (int) Math.pow(16.0D, 2.0D);
	private static final int[] EDGES = new int[1352];
	public static final long ALL_VIS = 0xFFFFFFFFFFFFFFFFL;

	static {
		int i = 0;

		for (int x = 0; x < 16; ++x) {
			for (int y = 0; y < 16; ++y) {
				for (int z = 0; z < 16; ++z) {
					if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
						EDGES[i++] = getIndex(x, y, z);
					}
				}
			}
		}
	}

	/*
	 * This is a pretty hefty structure: 1340 bytes per 16^3 (40+bytes per object, and the array of long[] in BitSet)
	 * weighing in around 190 bytes for BitSets, 40 bytes for SetVisibility, and 50 bytes for this.
	 * ~4,824,000 bytes at view distance 7; This could be halved if it were not reusable, but reusability is part
	 * of what makes it speedy when recalculating the viewable area.
	 */
	private final BitSet opaqueBlocks = new BitSet(4096);
	private final BitSet visibleBlocks = new BitSet(4096);
	private short transparentBlocks = 4096;
	@Getter
    private boolean dirty = true, computedVis = true;

	/** Accessing this class's fields is slow, so we let the visibility value be referenced directly. */
	private final long[] visibility = new long[]{ALL_VIS};

	private static int getIndex(int x, int y, int z) {

		return x << 0 | y << 8 | z << 4;
	}

    public boolean isRenderDirty() {

		if (isDirty()) {
			return true;
		}
		boolean r = computedVis;
		computedVis = false;
		return r;
	}

	public void setOpaque(int x, int y, int z, boolean opaque) {

		boolean prev = opaqueBlocks.get(getIndex(x, y, z));
		if (prev != opaque) {
			opaqueBlocks.set(getIndex(x, y, z), opaque);
			transparentBlocks += opaque ? -1 : 1;
			dirty = true;
		}
	}

	public long getVisibility() {
		return visibility[0];
	}

	public long[] getVisibilityArray() {
		return visibility;
	}

	@SuppressWarnings("unchecked")
	public void computeVisibility() {

		dirty = false;
		long setvisibility = 0;

		if (4096 - transparentBlocks < 256) {
			setvisibility = ALL_VIS;
		} else if (transparentBlocks == 0) {
			setvisibility = 0;
		} else {
			int[] edges = EDGES;
			int i = edges.length;

			visibleBlocks.andNot(visibleBlocks);
			visibleBlocks.or(opaqueBlocks);
			IntStack linkedlist = new IntStack(1024, 512);
            for (int k : edges) {
                if (!opaqueBlocks.get(k)) {
                    setvisibility = SetVisibility.setManyVisible(setvisibility, computeVisibleFacingsFrom(k, linkedlist));
                }
                linkedlist.setSize(0);
            }
		}

		visibility[0] = setvisibility;
		computedVis = true;
	}

	@SuppressWarnings("unchecked")
	public Set<EnumFacing> getVisibleFacingsFrom(int x, int y, int z) {

		visibleBlocks.andNot(visibleBlocks);
		visibleBlocks.or(opaqueBlocks);
		return computeVisibleFacingsFrom(getIndex(x & 15, y & 15, z & 15), new IntStack(256, 512));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private EnumSet computeVisibleFacingsFrom(int index, IntStack linkedlist) {

		EnumSet enumset = EnumSet.noneOf((Class)EnumFacing.class);
		linkedlist.add(index);
		BitSet blocks = this.visibleBlocks;
		blocks.set(index, true);

		EnumFacing[] facings = EnumFacing.values();
		final int k = facings.length;
		while (!linkedlist.isEmpty()) {
			int j = linkedlist.poll();
			addSides(j, enumset);

            for (EnumFacing face : facings) {
                int i1 = stepTo(j, face);

                if (i1 >= 0 && !blocks.get(i1)) {
                    blocks.set(i1, true);
                    linkedlist.add(i1);
                }
            }
		}

		return enumset;
	}

	private void addSides(int index, Set<EnumFacing> set) {

		int j = index >> 0 & 15;

		if (j == 0) {
			set.add(EnumFacing.EAST /* WEST */);
		} else if (j == 15) {
			set.add(EnumFacing.WEST /* EAST */);
		}

		int k = index >> 8 & 15;

		if (k == 0) {
			set.add(EnumFacing.DOWN);
		} else if (k == 15) {
			set.add(EnumFacing.UP);
		}

		int l = index >> 4 & 15;

		if (l == 0) {
			set.add(EnumFacing.NORTH);
		} else if (l == 15) {
			set.add(EnumFacing.SOUTH);
		}
	}

	private int stepTo(int index, EnumFacing side) {

        return switch (side) {
            case DOWN -> {
                if ((index >> 8 & 15) == 0) {
                    yield -1;
                }
                yield index - Y_OFFSET;
            }
            case UP -> {
                if ((index >> 8 & 15) == 15) {
                    yield -1;
                }
                yield index + Y_OFFSET;
            }
            case NORTH -> {
                if ((index >> 4 & 15) == 0) {
                    yield -1;
                }
                yield index - Z_OFFSET;
            }
            case SOUTH -> {
                if ((index >> 4 & 15) == 15) {
                    yield -1;
                }
                yield index + Z_OFFSET;
            }
            case EAST -> {
                if ((index >> 0 & 15) == 0) {
                    yield -1;
                }
                yield index - X_OFFSET;
            }
            case WEST -> {
                if ((index >> 0 & 15) == 15) {
                    yield -1;
                }
                yield index + X_OFFSET;
            }
            default -> -1;
        };
	}

}
