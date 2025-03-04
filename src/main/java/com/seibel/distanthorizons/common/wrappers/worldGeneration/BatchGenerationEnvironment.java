package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class BatchGenerationEnvironment extends AbstractBatchGenerationEnvironmentWrapper {
    public BatchGenerationEnvironment(IDhLevel level) {
        super(level);
    }

    @Override
    public void updateAllFutures() {

    }

    @Override
    public int getEventCount() {
        return 0;
    }

    @Override
    public void stop() {

    }

    @Override
    public CompletableFuture<Void> generateChunks(int minX, int minZ, int genSize, EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetStep, ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer) {
        return null;
    }
}
