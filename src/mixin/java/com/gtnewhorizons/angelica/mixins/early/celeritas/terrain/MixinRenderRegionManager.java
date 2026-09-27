package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.TerrainDrawStats;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(value = RenderRegionManager.class, remap = false)
public abstract class MixinRenderRegionManager {

    @Inject(method = "uploadMeshes", at = @At("HEAD"))
    private void angelica$onUploadMeshes(CommandList commandList, Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger, CallbackInfo ci) {
        for (var holder : results) {
            if (!(holder.output() instanceof ChunkBuildOutput buildOutput)) continue;
            for (BuiltSectionMeshParts mesh : buildOutput.meshes.values()) {
                long bytes = mesh.vertexBuffer().getLength();
                if (mesh.indexBuffer() != null) bytes += mesh.indexBuffer().getLength();
                TerrainDrawStats.recordSectionUpload(bytes);
            }
        }
    }
}
