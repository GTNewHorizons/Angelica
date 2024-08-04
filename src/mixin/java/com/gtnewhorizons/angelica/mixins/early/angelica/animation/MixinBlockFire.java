package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import net.minecraft.block.BlockFire;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockFire.class)
public class MixinBlockFire {

    @Shadow
    private IIcon[] field_149850_M;

    @Inject(method = "getFireIcon", at = @At("HEAD"))
    private void angelica$markFireAnimationForUpdate(int p_149840_1_, CallbackInfoReturnable<IIcon> cir) {
        AnimationsRenderUtils.markBlockTextureForUpdate(field_149850_M[p_149840_1_]);
    }
}
