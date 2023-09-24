package com.gtnewhorizons.angelica.mixins.early.archaic.common.lighting;

import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.embeddedt.archaicfix.lighting.world.lighting.LightingHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Chunk.class)
public abstract class MixinChunkVanilla {
    @Shadow public World worldObj;
    private static final String SET_BLOCK_STATE_VANILLA = "func_150807_a(IIILnet/minecraft/block/Block;I)Z";

    /**
     * Redirects the construction of the ExtendedBlockStorage in setBlockState(BlockPos, IBlockState). We need to initialize
     * the skylight data for the constructed section as soon as possible.
     *
     * @author Angeline
     */
    @Redirect(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "NEW",
                    args = "class=net/minecraft/world/chunk/storage/ExtendedBlockStorage"
            ),
            expect = 0
    )
    private ExtendedBlockStorage setBlockStateCreateSectionVanilla(int y, boolean storeSkylight) {
        return this.initSection(y, storeSkylight);
    }

    private ExtendedBlockStorage initSection(int y, boolean storeSkylight) {
        ExtendedBlockStorage storage = new ExtendedBlockStorage(y, storeSkylight);

        LightingHooks.initSkylightForSection(this.worldObj, (Chunk) (Object) this, storage);

        return storage;
    }

    /**
     * Modifies the flag variable of setBlockState(BlockPos, IBlockState) to always be false after it is set.
     *
     * @author Angeline
     */
    @ModifyVariable(
            method = SET_BLOCK_STATE_VANILLA,
            at = @At(
                    value = "STORE",
                    ordinal = 1
            ),
            name = "flag",
            index = 11,
            allow = 1
    )
    private boolean setBlockStateInjectGenerateSkylightMapVanilla(boolean generateSkylight) {
        return false;
    }

    /**
     * Prevent propagateSkylightOcclusion from being called.
     * @author embeddedt
     */
    @Redirect(method = SET_BLOCK_STATE_VANILLA, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;propagateSkylightOcclusion(II)V"))
    private void doPropagateSkylight(Chunk chunk, int i1, int i2) {
        /* No-op, we don't want skylight propagated */
    }

    /**
     * Prevent getLightFor from being called.
     * @author embeddedt
     */
    @Redirect(method = SET_BLOCK_STATE_VANILLA, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I"))
    private int getFakeLightFor(Chunk chunk, EnumSkyBlock skyBlock, int x, int y, int z) {
        return 0;
    }
}
