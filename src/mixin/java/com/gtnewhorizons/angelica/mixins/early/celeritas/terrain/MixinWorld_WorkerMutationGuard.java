package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import com.gtnewhorizons.angelica.rendering.WorkerWorldAccess;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

@Mixin(World.class)
public abstract class MixinWorld_WorkerMutationGuard {

    @Inject(method = "setTileEntity", at = @At("HEAD"), cancellable = true)
    private void angelica$blockWorkerSetTileEntity(int x, int y, int z, TileEntity tileEntity, CallbackInfo ci) {
        final WorldSlice slice = RenderThreadContext.workerSlice();
        if (slice == null) return;
        WorkerWorldAccess.blockedWrite("setTileEntity", x, y, z, slice.getRenderingBlock());
        ci.cancel();
    }
}
