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
import com.gtnewhorizons.angelica.api.MutableBlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockPosImpl;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.Block;

/**
 * Holds, manages, and provides access to the block/world related state
 * needed to render quads.
 *
 * <p>Exception: per-block position offsets are tracked in {@link ChunkRenderInfo}
 * so they can be applied together with chunk offsets.
 */
public class BlockRenderInfo {
	private final MutableBlockPos searchPos = new BlockPosImpl();

	public WorldSlice blockView;
	public BlockPos blockPos;
    public Block block;
    public int meta;

	public void release() {
		blockView = null;
		blockPos = null;
		block = null;
        meta = 0;
	}
}
