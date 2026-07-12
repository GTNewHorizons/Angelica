package com.gtnewhorizons.angelica.mixins.late.client.farseek;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "farseek.world.package$IBlockAccessValue$", remap = false)
public class MixinFarseekIBlockAccessValue {

    @Inject(method = "worldProvider$extension", at = @At("HEAD"), cancellable = true, remap = false)
    private void angelica$worldSliceProvider(IBlockAccess bac, CallbackInfoReturnable<WorldProvider> cir) {
        if (bac instanceof WorldSlice slice) {
            cir.setReturnValue(slice.getWorld().provider);
        }
    }
}
