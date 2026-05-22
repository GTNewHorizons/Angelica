package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tessellator.class)
public class MixinTessellator implements StateAwareTessellator {
    @Unique
    private final IntArrayList vertexStates = new IntArrayList();

    @Unique
    private final IntArrayList shaderOverrideBlockIds = new IntArrayList();

    @Unique
    private short currentShaderOverrideBlockId = -1;

    @Unique
    private boolean appliedAo;

    @Unique
    private boolean celeritasMeshing;

    @Override
    public void angelica$setCeleritasMeshing(boolean active) {
        this.celeritasMeshing = active;
    }

    @Inject(method = "addVertex", at = @At("RETURN"))
    private void addElementState(CallbackInfo ci) {
        if (!celeritasMeshing) return;
        int state = 0;
        if (appliedAo) {
            state |= StateAwareTessellator.RENDERED_WITH_VANILLA_AO;
        }
        this.vertexStates.add(state);
        this.shaderOverrideBlockIds.add(currentShaderOverrideBlockId);
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void resetVertexStates(CallbackInfo ci) {
        this.vertexStates.clear();
        this.shaderOverrideBlockIds.clear();
        this.currentShaderOverrideBlockId = -1;
    }

    @Override
    public void angelica$setAppliedAo(boolean flag) {
        this.appliedAo = flag;
    }

    @Override
    public int[] angelica$getVertexStates() {
        return this.vertexStates.elements();
    }

    @Override
    public int[] angelica$getShaderOverrideBlockIds() {
        return this.shaderOverrideBlockIds.elements();
    }

    @Override
    public void angelica$setShaderOverrideBlockId(short blockId) {
        this.currentShaderOverrideBlockId = blockId;
    }
}
