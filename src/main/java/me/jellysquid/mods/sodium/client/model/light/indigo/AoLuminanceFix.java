/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.jellysquid.mods.sodium.client.model.light.indigo;

import com.gtnewhorizons.angelica.api.BlockPos;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.Block;

/**
 * Implements a fix to prevent luminous blocks from casting AO shade.
 * Will give normal result if fix is disabled.
 */
@FunctionalInterface
public interface AoLuminanceFix {
	float apply(WorldSlice view, BlockPos pos, Block state);

	AoLuminanceFix INSTANCE = (WorldSlice view, BlockPos pos, Block state) ->
        state.getLightValue() == 0 ? (state.isOpaqueCube() ? 0.2f : 0.1f) : 1f;
}
