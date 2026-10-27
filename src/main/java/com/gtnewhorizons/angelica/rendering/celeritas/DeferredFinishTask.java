package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobCollector;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class DeferredFinishTask extends ChunkBuilderTask<ChunkBuildOutput> implements Consumer<ChunkJobResult<ChunkBuildOutput>> {
    private static final Tracy.ZoneId Z_MESH_DEFERRED_FINISH = Tracy.zoneId("meshDeferredFinish");

    private final DeferredSectionMesh mesh;
    private final DeferredMeshScheduler scheduler;
    private final @Nullable ChunkJobCollector collector;

    public DeferredFinishTask(DeferredSectionMesh mesh, DeferredMeshScheduler scheduler, @Nullable ChunkJobCollector collector) {
        this.mesh = mesh;
        this.scheduler = scheduler;
        this.collector = collector;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        if (mesh.isObsolete()) return null;
        if (Tracy.ENABLED) Tracy.beginZone(Z_MESH_DEFERRED_FINISH);
        try {
            return mesh.finish(context);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    @Override
    public void accept(@Nullable ChunkJobResult<ChunkBuildOutput> result) {
        scheduler.finished(mesh, result, collector);
    }
}
