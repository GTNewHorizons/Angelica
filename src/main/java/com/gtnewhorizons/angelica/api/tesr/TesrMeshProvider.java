package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.tileentity.TileEntity;

/**
 * Implemented by a TileEntitySpecialRenderer to opt into Angelica's mesh cache/batch
 *
 * Use a compile-only dependency and guard with {@code @Optional.Interface(iface = "com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider", modid = "angelica|tesr")}
 * and mark {@link #angelica$build} {@code @Optional.Method(modid = "angelica|tesr")}
 */
public interface TesrMeshProvider {

    /** Stable identity key for the mesh variant, null falls back to the vanilla render path this frame */
    Object angelica$meshKey(TileEntity te);

    /** True if the cached mesh must be rebuilt */
    default boolean angelica$meshDirty(TileEntity te) {
        return false;
    }

    /** Per-instance transform, defaults to TE position */
    default void angelica$transform(TileEntity te, double x, double y, double z) {
        GLStateManager.glTranslated(x, y, z);
    }

    /** Build and cache the mesh */
    void angelica$build(TesrMeshSink sink, TileEntity te);
}
