package org.embeddedt.embeddium.impl.render.chunk;

/**
 * Represents the type of chunk update task.
 */
public enum ChunkUpdateType {
    /**
     * Chunk is being built for the first time.
     */
    INITIAL_BUILD(128),
    /**
     * Chunk geometry is being sorted based on camera position change.
     */
    SORT(Integer.MAX_VALUE),
    /**
     * Like {@link ChunkUpdateType#SORT}, but will block the main thread if the camera is near enough to guarantee
     * the sort results are reflected quickly.
     */
    IMPORTANT_SORT(Integer.MAX_VALUE),
    /**
     * Chunk data has changed and remeshing is required.
     */
    REBUILD(Integer.MAX_VALUE),
    /**
     * Like {@link ChunkUpdateType#REBUILD}, but will block the main thread if the camera is near enough to guarantee
     * the rebuild is seen quickly.
     */
    IMPORTANT_REBUILD(Integer.MAX_VALUE);

    private final int maximumQueueSize;

    ChunkUpdateType(int maximumQueueSize) {
        this.maximumQueueSize = maximumQueueSize;
    }

    @Deprecated
    public static boolean canPromote(ChunkUpdateType prev, ChunkUpdateType next) {
        return prev == null || (prev == REBUILD && next == IMPORTANT_REBUILD);
    }

    // borrowed from PR #2016
    public static ChunkUpdateType getPromotionUpdateType(ChunkUpdateType prev, ChunkUpdateType next) {
        if (prev == next)
            return null; // No point submitting the same update twice

        if (prev == null || prev == SORT) {
            return next;
        }
        if (next == IMPORTANT_REBUILD
                || (prev == IMPORTANT_SORT && next == REBUILD)
                || (prev == REBUILD && next == IMPORTANT_SORT)) {
            return IMPORTANT_REBUILD;
        }
        return null;
    }

    /**
     * {@return the maximum size the rebuild queue should be allowed to grow to for this update type}
     */
    public int getMaximumQueueSize() {
        return this.maximumQueueSize;
    }

    /**
     * {@return true if the task is "important" and should block the main thread if the camera is near enough}
     */
    public boolean isImportant() {
        return this == IMPORTANT_REBUILD || this == IMPORTANT_SORT;
    }

    /**
     * {@return true if the task only sorts rather than performing a full chunk rebuild}
     */
    public boolean isSort() {
        return this == SORT || this == IMPORTANT_SORT;
    }
}
