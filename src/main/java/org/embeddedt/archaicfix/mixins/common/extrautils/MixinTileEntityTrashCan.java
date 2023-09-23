package org.embeddedt.archaicfix.mixins.common.extrautils;

import com.rwtema.extrautils.tileentity.TileEntityTrashCan;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityTrashCan.class)
public class MixinTileEntityTrashCan {
    @Shadow(remap = false) private boolean added;

    @Inject(method = "readFromNBT", at = @At("TAIL"), remap = false)
    private void forceRescan(NBTTagCompound par1NBTTagCompound, CallbackInfo ci) {
        this.added = true;
    }
}
