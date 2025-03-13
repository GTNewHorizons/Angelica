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

package com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public abstract class AbstractBatchGenerationEnvironmentWrapper
{
	public AbstractBatchGenerationEnvironmentWrapper(IDhLevel level) { }
	
	public abstract void updateAllFutures();
	
	public abstract int getEventCount();
	
	public abstract void stop();
	
	public abstract CompletableFuture<Void> generateChunks(
			int minX, int minZ, int genSize, EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetStep,
			ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer);
	
}
