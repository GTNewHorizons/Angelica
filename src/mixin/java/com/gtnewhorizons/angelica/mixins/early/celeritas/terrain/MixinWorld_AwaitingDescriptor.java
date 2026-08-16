package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.utils.AwaitingDescriptor;

@Mixin(World.class)
public class MixinWorld_AwaitingDescriptor {

    @Redirect(method = "updateEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;updateEntity()V"))
    private void angelica$skipUntilDescriptorArrives(TileEntity te) {
        if (!AwaitingDescriptor.defersUpdate(te)) {
            te.updateEntity();
        }
    }
}
