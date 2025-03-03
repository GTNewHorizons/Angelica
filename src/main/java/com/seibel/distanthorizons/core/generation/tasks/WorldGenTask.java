/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.generation.tasks;

import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;

import java.util.concurrent.CompletableFuture;

/**
 * @author Leetom
 * @version 2022-11-25
 */
public final class WorldGenTask
{
	public final long pos;
	public final byte dataDetailLevel;
	public final IWorldGenTaskTracker taskTracker;
	public final CompletableFuture<WorldGenResult> future;
	
	
	
	public WorldGenTask(long pos, byte dataDetail, IWorldGenTaskTracker taskTracker, CompletableFuture<WorldGenResult> future)
	{
		this.dataDetailLevel = dataDetail;
		this.pos = pos;
		this.taskTracker = taskTracker;
		this.future = future;
	}
	
}
