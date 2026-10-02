package com.gtnewhorizons.angelica.mixins.early.angelica.lighting;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * This mixin is a backport of a Forge fix https://github.com/MinecraftForge/MinecraftForge/pull/4729
 */
@Mixin(World.class)
public abstract class MixinWorld_FixLightUpdateLag {

    @Shadow
    public abstract boolean doChunksNearChunkExist(int p_72873_1_, int p_72873_2_, int p_72873_3_, int p_72873_4_);

    @Unique
    private int angelica$updateRange;

    @ModifyConstant(method = "updateLightByType", constant = @Constant(intValue = 17, ordinal = 0))
    public int angelica$modifyRangeCheck1(int cst) {
        return 16;
    }

    @Surround(method = "updateLightByType", id = "range",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/Profiler;startSection(Ljava/lang/String;)V", ordinal = 0))
    private void angelica$modifyUpdateRange(@Surround.Local(argsOnly = true, ordinal = 0) int x, @Surround.Local(argsOnly = true, ordinal = 1) int y,
            @Surround.Local(argsOnly = true, ordinal = 2) int z) {
        this.angelica$updateRange = this.doChunksNearChunkExist(x, y, z, 18) ? 17 : 15;
    }

    @ModifyConstant(method = "updateLightByType", constant = { @Constant(intValue = 17, ordinal = 1), @Constant(intValue = 17, ordinal = 2) })
    public int angelica$modifyRangeCheck2(int cst) {
        return this.angelica$updateRange;
    }
}
