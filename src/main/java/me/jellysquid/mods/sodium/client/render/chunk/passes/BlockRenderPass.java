package me.jellysquid.mods.sodium.client.render.chunk.passes;

/**
 * The order of these enums must match the passes used by vanilla.
 */
public enum BlockRenderPass {
    CUTOUT_MIPPED(false, 131072),
    TRANSLUCENT(true, 262144);

    public static final BlockRenderPass[] VALUES = BlockRenderPass.values();
    public static final int COUNT = VALUES.length;

    private final boolean translucent;
    private final int bufferSize;

    BlockRenderPass(boolean translucent, int bufferSize) {
        this.translucent = translucent;
        this.bufferSize = bufferSize;
    }

    public boolean isTranslucent() {
        return this.translucent;
    }

    public int bufferSize() {
        return this.bufferSize;
    }
}
