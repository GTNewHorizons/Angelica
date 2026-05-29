package com.gtnewhorizons.angelica.rendering.celeritas;

import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;

import com.cardinalstar.cubicchunks.util.HashMap3D;
import com.cardinalstar.cubicchunks.util.HashSet3D;

public class CubeStatusTracker implements ChunkTracker {

    /// Cubes that have been synced to the client and are currently loaded.
    private final HashSet3D loadedCubes = new HashSet3D();
    /// Cubes that have their neighbours loaded (as needed) and are ready to be rendered.
    private final HashSet3D validCubes = new HashSet3D();

    private final HashMap3D<PendingState> changedCubes = new HashMap3D<>();

    private enum PendingState {
        Add,
        Remove
    }

    /// When true, adjacent cubes will not be checked.
    private final boolean fastMode;

    public CubeStatusTracker(boolean fastMode) {
        this.fastMode = fastMode;
    }

    public void onCubeLoaded(int cubeX, int cubeY, int cubeZ) {
        if (loadedCubes.add(cubeX, cubeY, cubeZ)) {
            onCubeChanged(cubeX, cubeY, cubeZ);
        }
    }

    public void onCubeUnloaded(int cubeX, int cubeY, int cubeZ) {
        if (loadedCubes.remove(cubeX, cubeY, cubeZ)) {
            onCubeChanged(cubeX, cubeY, cubeZ);
        }
    }

    private void onCubeChanged(int cubeX, int cubeY, int cubeZ) {
        pollCube(cubeX, cubeY, cubeZ);

        if (!fastMode) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        pollCube(cubeX + dx, cubeY + dy, cubeZ + dz);
                    }
                }
            }
        }
    }

    private void pollCube(int cubeX, int cubeY, int cubeZ) {
        boolean renderable = canRender(cubeX, cubeY, cubeZ);
        boolean isRendering = validCubes.contains(cubeX, cubeY, cubeZ);

        if (renderable != isRendering) {
            changedCubes.put(cubeX, cubeY, cubeZ, renderable ? PendingState.Add : PendingState.Remove);
        }
    }

    private boolean canRender(int cubeX, int cubeY, int cubeZ) {
        if (!loadedCubes.contains(cubeX, cubeY, cubeZ)) return false;

        if (!fastMode) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        if (!loadedCubes.contains(cubeX + dx, cubeY + dy, cubeZ + dz)) return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void onChunkStatusAdded(int i, int i1, int i2) {
        // do nothing
    }

    @Override
    public void onChunkStatusRemoved(int i, int i1, int i2) {
        // do nothing
    }

    @Override
    public void forEachReady(RenderSectionManager renderSectionManager) {
        for (var pos : validCubes.fastEntryIterable()) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            renderSectionManager.onSectionRemoved(x, y, z);
        }
    }

    @Override
    public void forEachEvent(RenderSectionManager renderSectionManager) {
        changedCubes.forEach((x, y, z, state) -> {
            boolean ready = state == PendingState.Add;

            if (ready) {
                validCubes.add(x, y, z);
                renderSectionManager.onSectionAdded(x, y, z);
            } else {
                validCubes.remove(x, y, z);
                renderSectionManager.onSectionRemoved(x, y, z);
            }
        });

        changedCubes.clear();
    }
}
