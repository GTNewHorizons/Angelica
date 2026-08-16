package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.angelica.mixins.interfaces.AwaitingDescriptorTE;
import com.gtnewhorizons.angelica.utils.AwaitingDescriptor;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient_DescriptorRepair {

    @Redirect(method = "handleUpdateTileEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getTileEntity(III)Lnet/minecraft/tileentity/TileEntity;"))
    private TileEntity angelica$remeshOnFirstDescriptor(WorldClient world, int x, int y, int z) {
        final TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof AwaitingDescriptorTE awaiting && awaiting.angelica$isAwaitingDescriptor()) {
            AwaitingDescriptor.end(awaiting);
            world.markBlockForUpdate(x, y, z);
        }
        return te;
    }
}
