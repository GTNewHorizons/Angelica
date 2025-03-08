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

package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.objects.EventTimer;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

import org.apache.logging.log4j.Logger;

public final class GenerationEvent
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
    private static int generationFutureDebugIDs = 0;

    public final int id;
    public final ThreadedParameters threadedParam;
    public final DhChunkPos minPos;
    /** the number of chunks wide this event is */
    public final int size;
    public final EDhApiWorldGenerationStep targetGenerationStep;
    public final EDhApiDistantGeneratorMode generatorMode;
    public EventTimer timer = null;
    public long inQueueTime;
    public long timeoutTime = -1;
    public CompletableFuture<Void> future = null;
    public final Consumer<IChunkWrapper> resultConsumer;



    public GenerationEvent(
        DhChunkPos minPos, int size, BatchGenerationEnvironment generationGroup,
        EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetGenerationStep, Consumer<IChunkWrapper> resultConsumer)
    {
        this.inQueueTime = System.nanoTime();
        this.id = generationFutureDebugIDs++;
        this.minPos = minPos;
        this.size = size;
        this.generatorMode = generatorMode;
        this.targetGenerationStep = targetGenerationStep;
        this.threadedParam = ThreadedParameters.getOrMake(generationGroup.params);
        this.resultConsumer = resultConsumer;
    }



    public static GenerationEvent startEvent(
        DhChunkPos minPos, int size, BatchGenerationEnvironment genEnvironment,
        EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep target, Consumer<IChunkWrapper> resultConsumer,
        ExecutorService worldGeneratorThreadPool)
    {
        GenerationEvent generationEvent = new GenerationEvent(minPos, size, genEnvironment, generatorMode, target, resultConsumer);
        generationEvent.future = CompletableFuture.supplyAsync(() ->
        {
            long runStartTime = System.nanoTime();
            generationEvent.timeoutTime = runStartTime;
            generationEvent.inQueueTime = runStartTime - generationEvent.inQueueTime;
            generationEvent.timer = new EventTimer("setup");

            BatchGenerationEnvironment.isDistantGeneratorThread.set(true);

            try
            {
                genEnvironment.generateLodFromListAsync(generationEvent, (runnable) ->
                {
                    worldGeneratorThreadPool.execute(() ->
                    {
                        boolean alreadyMarked = BatchGenerationEnvironment.isCurrentThreadDistantGeneratorThread();
                        if (!alreadyMarked)
                        {
                            BatchGenerationEnvironment.isDistantGeneratorThread.set(true);
                        }

                        try
                        {
                            runnable.run();
                        }
                        catch (Throwable throwable)
                        {
                            handleWorldGenThrowable(generationEvent, throwable);
                        }
                        finally
                        {
                            if (!alreadyMarked)
                            {
                                BatchGenerationEnvironment.isDistantGeneratorThread.set(false);
                            }
                        }
                    });
                });
            }
            catch (Throwable initialThrowable)
            {
                handleWorldGenThrowable(generationEvent, initialThrowable);
            }
            finally
            {
                BatchGenerationEnvironment.isDistantGeneratorThread.remove();
            }

            return null;
        }, worldGeneratorThreadPool);
        return generationEvent;
    }
    /** There's probably a better way to handle this, but it'll work for now */
    private static void handleWorldGenThrowable(GenerationEvent generationEvent, Throwable initialThrowable)
    {
        Throwable throwable = initialThrowable;
        while (throwable instanceof CompletionException)
        {
            throwable = throwable.getCause();
        }

        if (throwable instanceof InterruptedException
            || throwable instanceof UncheckedInterruptedException
            || throwable instanceof RejectedExecutionException)
        {
            // these exceptions can be ignored, generally they just mean
            // the thread is busy so it'll need to try again later.
            // FIXME this should cause the world gen task to be re-queued so we can try again later
            //  however, currently it can cause large gaps in the world gen instead.
            //  These gaps will generate correctly if the level is reloaded and the world gen is re-queued,
            //  however this is makes it look like the generator isn't working or skipped something.
        }
        else
        {
            generationEvent.future.completeExceptionally(throwable);
        }
    }

    public boolean isComplete() { return this.future.isDone(); }

    public boolean hasTimeout(int duration, TimeUnit unit)
    {
        if (this.timeoutTime == -1)
        {
            return false;
        }

        long currentTime = System.nanoTime();
        long delta = currentTime - this.timeoutTime;
        return (delta > TimeUnit.NANOSECONDS.convert(duration, unit));
    }

    public boolean terminate()
    {
        LOGGER.info("======================DUMPING ALL THREADS FOR WORLD GEN=======================");
        ThreadPoolUtil.WORLD_GEN_THREAD_FACTORY.dumpAllThreadStacks();
        this.future.cancel(true);
        return this.future.isCancelled();
    }

    public void refreshTimeout()
    {
        this.timeoutTime = System.nanoTime();
        UncheckedInterruptedException.throwIfInterrupted();
    }

    @Override
    public String toString() { return this.id + ":" + this.size + "@" + this.minPos + "(" + this.targetGenerationStep + ")"; }

}
