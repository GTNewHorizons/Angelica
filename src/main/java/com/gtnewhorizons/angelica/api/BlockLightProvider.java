package com.gtnewhorizons.angelica.api;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Provides per-block RGB light color data for the rendering pipeline. Mods that replace the lighting engine (e.g. Supernova) implement this
 * and register via {@link #register(BlockLightProvider)}.
 *
 */
public interface BlockLightProvider {

    /**
     * Get RGB block light at the given world coordinates.
     *
     * @param blockAccess the block access context (WorldSlice on builder threads, World on main thread)
     * @return packed RGB (see class Javadoc), or {@code -1} if no data is available
     */
    int getBlockLightRGB(IBlockAccess blockAccess, int x, int y, int z);

    /**
     * Get RGB sky light at the given world coordinates.
     *
     * @param blockAccess the block access context (WorldSlice on builder threads, World on main thread)
     * @return packed RGB (see class Javadoc), or {@code -1} if no data
     */
    default int getSkyLightRGB(IBlockAccess blockAccess, int x, int y, int z) { return -1; }

    /**
     * Capture per-section light data on the main thread for use by builder threads.
     * Called during {@code ClonedChunkSection.init()}.
     *
     * @return a {@link SectionLightData} snapshot, or {@code null} if not supported
     */
    default SectionLightData prepareSectionData(Chunk chunk, int sectionY) {
        return null;
    }

    /** Pack three 0-15 channels into a single int. */
    static int packRGB(int r, int g, int b) {
        return (r << 8) | (g << 4) | b;
    }

    /** Extract the red channel (0-15) from a packed RGB word. */
    static int unpackR(int word) {
        return (word >> 8) & 0xF;
    }

    /** Extract the green channel (0-15) from a packed RGB word. */
    static int unpackG(int word) {
        return (word >> 4) & 0xF;
    }

    /** Extract the blue channel (0-15) from a packed RGB word. */
    static int unpackB(int word) {
        return word & 0xF;
    }

    static BlockLightProvider getInstance() {
        return Holder.INSTANCE;
    }

    static boolean isRegistered() {
        return Holder.INSTANCE != Holder.DEFAULT;
    }

    /**
     * Register the global block light provider. Must be called exactly once.
     *
     * @throws IllegalStateException if a provider has already been registered
     */
    static void register(BlockLightProvider provider) {
        if (Holder.INSTANCE != Holder.DEFAULT) {
            throw new IllegalStateException("BlockLightProvider already registered: " + Holder.INSTANCE.getClass().getName() + ". Cannot register " + provider.getClass().getName());
        }
        Holder.INSTANCE = provider;
    }

    /**
     * Call from your {@code IFMLLoadingPlugin} static initializer to enable colored light support.
     * Must be called before mixin application (i.e. during coremod loading). Calling after mixins are frozen will throw.
     */
    static void enableColoredLight() {
        if (EarlyFlags.MIXINS_FROZEN) {
            throw new IllegalStateException("Too late to enable colored light -- mixin configs have already been evaluated. Call BlockLightProvider.enableColoredLight() from your IFMLLoadingPlugin.");
        }
        EarlyFlags.COLORED_LIGHT = true;
    }

    /**
     * Freeze the mixin config gate. After this call, {@link #enableColoredLight()} will throw.
     * Called by the mixin config evaluation path before reading {@link #coloredLightEnabled()}.
     */
    static void freezeMixinConfig() {
        EarlyFlags.MIXINS_FROZEN = true;
    }

    /**
     * Returns whether colored light support was enabled via {@link #enableColoredLight()}.
     * Call {@link #freezeMixinConfig()} separately to close the enablement window.
     */
    static boolean coloredLightEnabled() {
        return EarlyFlags.COLORED_LIGHT;
    }

    final class EarlyFlags {
        static boolean COLORED_LIGHT = false;
        static boolean MIXINS_FROZEN = false;
    }

    final class Holder {
        static final BlockLightProvider DEFAULT = (_, _, _, _) -> -1;
        static BlockLightProvider INSTANCE = DEFAULT;
    }
}
