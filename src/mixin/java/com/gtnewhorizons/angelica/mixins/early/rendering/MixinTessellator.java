package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tessellator.class)
public class MixinTessellator implements StateAwareTessellator, BiomeBlendTessellator {
    @Unique
    private BiomeVertexBlender angelica$biomeBlender;

    @Unique
    private BiomeVertexBlender angelica$liquidBlender;

    @Override
    public BiomeVertexBlender angelica$getBiomeBlender() {
        return angelica$biomeBlender;
    }

    @Override
    public void angelica$setBiomeBlender(BiomeVertexBlender blender) {
        angelica$biomeBlender = blender;
        angelica$liquidBlender = blender != null && blender.isWater() ? blender : null;
    }

    @ModifyExpressionValue(method = "addVertex", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;color:I"))
    private int angelica$blendVertexColor(int original, @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y, @Local(argsOnly = true, ordinal = 2) double z) {
        return angelica$liquidBlender == null ? original : angelica$liquidBlender.tint(original, x, y, z);
    }

    @Unique
    private final IntArrayList vertexStates = new IntArrayList();

    @Unique
    private final IntArrayList shaderOverrideBlockIds = new IntArrayList();

    @Unique
    private short currentShaderOverrideBlockId = -1;

    @Unique
    private boolean appliedAo;
    @Unique
    private boolean angelica$noDirectionalShading;

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

        if (appliedAo) state |= StateAwareTessellator.RENDERED_WITH_VANILLA_AO;
        if (angelica$noDirectionalShading) state |= StateAwareTessellator.NO_DIRECTIONAL_SHADING;

        this.vertexStates.add(state);
        this.shaderOverrideBlockIds.add(currentShaderOverrideBlockId);
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void resetVertexStates(CallbackInfo ci) {
        this.vertexStates.clear();
        this.shaderOverrideBlockIds.clear();
        this.currentShaderOverrideBlockId = -1;
        this.appliedAo = false;
        this.angelica$noDirectionalShading = false;
    }

    @Override
    public void angelica$setAppliedAo(boolean flag) {
        this.appliedAo = flag;
    }

    @Override
    public void angelica$setNoDirectionalShading(boolean flag) {
        this.angelica$noDirectionalShading = flag;
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
