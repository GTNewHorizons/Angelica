package com.gtnewhorizons.angelica.mixins.early.angelica.textures.ic2;

import com.gtnewhorizons.angelica.mixins.interfaces.IPatchedTextureAtlasSprite;
import ic2.core.item.ItemFluidCell;
import ic2.core.item.RenderLiquidCell;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RenderLiquidCell.class)
public class MixinRenderLiquidCell {

    @Inject(at = @At(ordinal = 0, remap = false, shift = Shift.AFTER, target = "Lorg/lwjgl/opengl/GL11;glColor3ub(BBB)V", value = "INVOKE"), locals = LocalCapture.CAPTURE_FAILSOFT, method = "renderItem", remap = false)
    private void angelica$markNeedsAnimationUpdate(ItemRenderType type, ItemStack item, Object[] data, CallbackInfo ci, ItemFluidCell cell, IIcon icon, FluidStack fs, IIcon windowIcon, IIcon fluidicon) {
        if (fluidicon instanceof TextureAtlasSprite) {
            ((IPatchedTextureAtlasSprite) fluidicon).markNeedsAnimationUpdate();
        }
    }
}
