/*
 * Adapated from: Beddium for usage in Angelica
 *
 * Copyright (C) 2025 Ven, FalsePattern
 * All Rights Reserved
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.ChunkTrackerAccessor;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.util.PositionUtil;

/**
 * Configurable chunk tracker supporting "fast" and "aggressive" loading modes.
 * Based on Beddium's SafeChunkTracker by Ven and FalsePattern.
 *
 * Modes (controlled by {@link AngelicaConfig#useVanillaChunkTracking}):
 * - Fast (default): Only renders chunks when all 8 neighbors are loaded (better lighting/AO)
 * - Aggressive: Renders chunks immediately when loaded (faster perceived loading)
 *
 * Mode can be switched at runtime via options menu - triggers renderer reload.
 */
public class AngelicaChunkTracker extends ChunkTracker {
    private final LongOpenHashSet chunkReadyForced = new LongOpenHashSet();

    private ChunkTrackerAccessor self() {
        return (ChunkTrackerAccessor) this;
    }

    @Override
    public void onChunkStatusAdded(int x, int z, int flags) {
        final var key = PositionUtil.packChunk(x, z);
        final var prev = self().angelica$getChunkStatus().get(key);
        final var cur = prev | flags;

        if (prev == cur) {
            return;
        }

        self().angelica$getChunkStatus().put(key, cur);
        this.updateNeighbors(x, z);
    }

    @Override
    public void onChunkStatusRemoved(int x, int z, int flags) {
        final var key = PositionUtil.packChunk(x, z);
        final var prev = self().angelica$getChunkStatus().get(key);
        final int cur = prev & ~flags;

        if (prev == cur) {
            return;
        }

        if (cur == self().angelica$getChunkStatus().defaultReturnValue()) {
            self().angelica$getChunkStatus().remove(key);
        } else {
            self().angelica$getChunkStatus().put(key, cur);
        }

        this.updateNeighbors(x, z);
    }

    private void updateNeighbors(int x, int z) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                this.updateMerged(ox + x, oz + z);
            }
        }
    }

    private void updateMerged(int x, int z) {
        final long key = PositionUtil.packChunk(x, z);
        final int selfFlags = self().angelica$getChunkStatus().get(key);

        // If self doesn't have FLAG_ALL, can't be ready at all
        if (selfFlags != ChunkStatus.FLAG_ALL) {
            // Remove from ready if present
            final boolean wasReady = self().angelica$getChunkReady().remove(key) | this.chunkReadyForced.remove(key);
            if (wasReady && !self().angelica$getLoadQueue().remove(key)) {
                self().angelica$getUnloadQueue().add(key);
            }
            return;
        }

        // Check if all neighbors also have FLAG_ALL
        int mergedFlags = selfFlags;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                mergedFlags &= self().angelica$getChunkStatus().get(PositionUtil.packChunk(ox + x, oz + z));
            }
        }

        if (mergedFlags == ChunkStatus.FLAG_ALL) {
            // All neighbors ready - add to both sets -- Trigger load if newly added to chunkReadyForced (first time ready)
            if ((this.chunkReadyForced.add(key) || self().angelica$getChunkReady().add(key)) && !self().angelica$getUnloadQueue().remove(key)) {
                self().angelica$getLoadQueue().add(key);
            }
        } else {
            // Self ready but neighbors not - add to forced, remove from proper
            // Trigger load if state changed (moved from proper to forced, or newly forced)
            if ((this.chunkReadyForced.add(key) || self().angelica$getChunkReady().remove(key)) && !self().angelica$getUnloadQueue().remove(key)) {
                self().angelica$getLoadQueue().add(key);
            }
        }
    }

    @Override
    public LongCollection getReadyChunks() {
        if (AngelicaConfig.useVanillaChunkTracking) {
            // Aggressive mode: return both ready sets
            final LongOpenHashSet combined = new LongOpenHashSet(self().angelica$getChunkReady());
            combined.addAll(this.chunkReadyForced);
            return LongSets.unmodifiable(combined);
        } else {
            // Default mode: only return chunks with all neighbors ready
            return LongSets.unmodifiable(self().angelica$getChunkReady());
        }
    }

    @Override
    public void forEachEvent(ChunkEventHandler loadEventHandler, ChunkEventHandler unloadEventHandler) {
        ChunkTracker.forEachChunk(self().angelica$getUnloadQueue(), unloadEventHandler);
        self().angelica$getUnloadQueue().clear();

        ChunkTracker.forEachChunk(self().angelica$getLoadQueue(), loadEventHandler);
        self().angelica$getLoadQueue().clear();
    }
}
