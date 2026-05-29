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

import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerImpl;
import org.embeddedt.embeddium.impl.util.PositionUtil;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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
public class AngelicaChunkTracker extends ChunkTrackerImpl {
    private final LongOpenHashSet chunkReadyForced = new LongOpenHashSet();

    @Override
    protected void updateNeighbors(int x, int z) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                this.updateMerged(ox + x, oz + z);
            }
        }
    }

    @Override
    protected void updateMerged(int x, int z) {
        final long key = PositionUtil.packChunk(x, z);
        final int selfFlags = chunkStatus.get(key);

        // If self doesn't have FLAG_ALL, can't be ready at all
        if (selfFlags != ChunkStatus.FLAG_ALL) {
            // Remove from ready if present
            final boolean wasReady = chunkReady.remove(key) | this.chunkReadyForced.remove(key);
            if (wasReady && !loadQueue.remove(key)) {
                unloadQueue.add(key);
            }
            return;
        }

        // Check if all neighbors also have FLAG_ALL
        int mergedFlags = selfFlags;

        outer:
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                if (ox == 0 && oz == 0) continue;

                mergedFlags &= chunkStatus.get(PositionUtil.packChunk(ox + x, oz + z));

                if (mergedFlags != ChunkStatus.FLAG_ALL) break outer;
            }
        }

        if (mergedFlags == ChunkStatus.FLAG_ALL) {
            // All neighbors ready - add to both sets -- Trigger load if newly added to chunkReadyForced (first time ready)
            if ((this.chunkReadyForced.add(key) || chunkReady.add(key)) && !unloadQueue.remove(key)) {
                loadQueue.add(key);
            }
        } else {
            // Self ready but neighbors not - add to forced, remove from proper
            // Trigger load if state changed (moved from proper to forced, or newly forced)
            if ((this.chunkReadyForced.add(key) || chunkReady.remove(key)) && !unloadQueue.remove(key)) {
                loadQueue.add(key);
            }
        }
    }

    @Override
    public void forEachReady(RenderSectionManager sectionManager) {
        int min = sectionManager.getMinSection();
        int max = sectionManager.getMaxSection();


        if (AngelicaConfig.useVanillaChunkTracking) {
            // Aggressive mode: return both ready sets
            final LongOpenHashSet combined = new LongOpenHashSet(chunkReady);
            combined.addAll(this.chunkReadyForced);

            forEachChunk(combined, (x, z) -> {
                for(int y = min; y < max; ++y) {
                    sectionManager.onSectionAdded(x, y, z);
                }
            });
        } else {
            // Default mode: only return chunks with all neighbors ready
            forEachChunk(this.chunkReady, (x, z) -> {
                for(int y = min; y < max; ++y) {
                    sectionManager.onSectionAdded(x, y, z);
                }
            });
        }
    }
}
