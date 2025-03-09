package com.gtnewhorizons.angelica.mixins.early.celeritas.core.terrain;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.taumc.celeritas.impl.extensions.TessellatorExtension;

@Mixin(Tessellator.class)
public abstract class TessellatorMixin implements TessellatorExtension {

    @Shadow
    protected abstract void reset();

    @Shadow
    private int[] rawBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private boolean isDrawing;

    @Override
    public int[] celeritas$getRawBuffer() {
        return rawBuffer;
    }

    @Override
    public int celeritas$getVertexCount() {
        return vertexCount;
    }

    @Override
    public void celeritas$reset() {
        this.isDrawing = false;
        this.reset();
    }
}
