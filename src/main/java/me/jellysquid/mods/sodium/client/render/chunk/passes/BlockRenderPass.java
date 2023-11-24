package me.jellysquid.mods.sodium.client.render.chunk.passes;


/**
 * The order of these enums must match the passes used by vanilla.
 */
public enum BlockRenderPass {
    CUTOUT_MIPPED(false),
    TRANSLUCENT(true);

    public static final BlockRenderPass[] VALUES = BlockRenderPass.values();
    public static final int COUNT = VALUES.length;

    private final boolean translucent;

    BlockRenderPass(boolean translucent) {
        this.translucent = translucent;
    }

    public boolean isTranslucent() {
        return this.translucent;
    }
}
