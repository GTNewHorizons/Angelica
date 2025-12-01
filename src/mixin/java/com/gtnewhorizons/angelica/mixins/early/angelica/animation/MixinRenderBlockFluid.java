package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.utils.AnimationsRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.RenderBlockFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderBlockFluid.class, remap = false)
public abstract class MixinRenderBlockFluid {

    /**
     * @author laetansky
     * @reason mark texture for update
     */
    @Inject(method = "getIcon", at = @At(value = "RETURN", ordinal = 0))
    private void angelica$updateTexture(IIcon icon, CallbackInfoReturnable<IIcon> cir) {
        AnimationsRenderUtils.markBlockTextureForUpdate(icon, Minecraft.getMinecraft().theWorld);
    }
}
