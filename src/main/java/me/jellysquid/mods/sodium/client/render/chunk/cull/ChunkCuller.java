package me.jellysquid.mods.sodium.client.render.chunk.cull;

import com.gtnewhorizons.angelica.compat.Camera;
import com.gtnewhorizons.angelica.compat.mojang.ChunkOcclusionData;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;


public interface ChunkCuller {
    IntArrayList computeVisible(Camera camera, FrustumExtended frustum, int frame, boolean spectator);

    void onSectionStateChanged(int x, int y, int z, ChunkOcclusionData occlusionData);
    void onSectionLoaded(int x, int y, int z, int id);
    void onSectionUnloaded(int x, int y, int z);

    boolean isSectionVisible(int x, int y, int z);
}
