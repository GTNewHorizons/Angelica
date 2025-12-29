package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ChunkRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSectionCache;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.joml.Vector3d;

public final class ThreadedChunkTaskProvider implements ChunkTaskProvider {
    public static final ThreadedChunkTaskProvider INSTANCE = new ThreadedChunkTaskProvider();

    private ThreadedChunkTaskProvider() {}

    @Override
    public ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame, Vector3d cameraPosition) {
        return new ThreadedAngelicaChunkBuilderMeshingTask(render, null, frame, cameraPosition);
    }

    @Override
    public ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame, Vector3d cameraPosition, ClonedChunkSectionCache sectionCache) {
        final ChunkSectionPos origin = ChunkSectionPos.from(render.getChunkX(), render.getChunkY(), render.getChunkZ());
        final ChunkRenderContext context = WorldSlice.prepare(Minecraft.getMinecraft().theWorld, origin, sectionCache);
        return context == null ? null : new ThreadedAngelicaChunkBuilderMeshingTask(render, context, frame, cameraPosition);
    }

    @Override
    public int threadCount() {
        return AngelicaConfig.chunkBuilderThreadCount;
    }

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public boolean isEnabled() {
        return AngelicaConfig.enableThreadedChunkBuilding;
    }
}
