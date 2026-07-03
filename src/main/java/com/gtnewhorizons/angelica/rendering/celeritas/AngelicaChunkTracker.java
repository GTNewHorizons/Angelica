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

    @Override
    public void forEachReady(RenderSectionManager sectionManager) {
        if (this.isFastModeEnabled() != AngelicaConfig.useVanillaChunkTracking) {
            setFastMode(AngelicaConfig.useVanillaChunkTracking);
        }

        super.forEachReady(sectionManager);
    }

    @Override
    public void forEachEvent(RenderSectionManager sectionManager) {
        if (this.isFastModeEnabled() != AngelicaConfig.useVanillaChunkTracking) {
            setFastMode(AngelicaConfig.useVanillaChunkTracking);
        }

        super.forEachEvent(sectionManager);
    }
}
