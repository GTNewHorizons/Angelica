package com.gtnewhorizons.angelica.mixins.early.celeritas.threading;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.ThreadedAngelicaChunkBuilderMeshingTask;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobTyped;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ChunkJobTyped.class, remap = false)
public abstract class MixinChunkJobTyped {
    @Shadow @Final private ChunkBuilderTask<?> task;
    @Shadow private volatile boolean started;

    @ModifyArg(method = "execute", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private Object angelica$releaseUnstartedContext(Object result) {
        if (!this.started && this.task instanceof ThreadedAngelicaChunkBuilderMeshingTask t) t.releaseUnstartedContext();
        return result;
    }
}
