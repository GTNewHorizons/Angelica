package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import org.jetbrains.annotations.NotNull;

public class VisibilityEncoding {
    public static final long NULL = 0L;
    public static final long EVERYTHING = everything();

    public static <T> long encode(@NotNull DataHolder holder) {
        long visibilityData = 0;

        for (int from = 0; from < GraphDirection.COUNT; from++) {
            for (int to = 0; to < GraphDirection.COUNT; to++) {
                if (holder.canFaceSeeFace(from, to)) {
                    visibilityData |= 1L << bit(from, to);
                }
            }
        }

        return visibilityData;
    }

    public interface DataHolder {
        /**
         * {@return true if looking through the given GraphDirection allows seeing out to the other GraphDirection}
         */
        boolean canFaceSeeFace(int fromDir, int toDir);
    }

    private static int bit(int from, int to) {
        return (from * 8) + to;
    }

    private static long everything() {
        long visibilityData = 0;

        for (int from = 0; from < GraphDirection.COUNT; from++) {
            for (int to = 0; to < GraphDirection.COUNT; to++) {
                visibilityData |= 1L << bit(from, to);
            }
        }

        return visibilityData;
    }

    // Returns a merged bit-field of the outgoing directions for each incoming direction
    public static int getConnections(long visibilityData, int incoming) {
        return foldOutgoingDirections(visibilityData & createMask(incoming));
    }

    // Returns a merged bit-field of any possible outgoing directions
    public static int getConnections(long visibilityData) {
        return foldOutgoingDirections(visibilityData);
    }

    private static long createMask(int incoming) {
        var expanded = (0b0000001_0000001_0000001_0000001_0000001_0000001L * Integer.toUnsignedLong(incoming));
        return (expanded & 0b00000001_00000001_00000001_00000001_00000001_00000001L) * 0xFF;
    }

    private static int foldOutgoingDirections(long data) {
        long folded = data;
        folded |= folded >> 32; // fold top 32 bits onto bottom 32 bits
        folded |= folded >> 16; // fold top 16 bits onto bottom 16 bits
        folded |= folded >> 8; // fold top 8 bits onto bottom 8 bits

        return (int) (folded & GraphDirectionSet.ALL);
    }
}