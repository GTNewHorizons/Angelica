package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.TextureSamplers;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderItem.class)
public class MixinRenderItem_GuiSampler {

    @Surround(
        method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V",
        remap = false,
        at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V", remap = true),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderIcon(IILnet/minecraft/util/IIcon;II)V", remap = true)
        }
    )
    private void angelica$beginGuiSampling() {
        @Surround.Carry("gui") final boolean gui = !RenderingState.INSTANCE.isRenderingWorld();
        @Surround.Carry("sampler") final int sampler = gui ? GLStateManager.getSamplerBinding(0) : 0;
        if (gui) RenderSystem.bindSamplerToUnit(0, TextureSamplers.getGuiSampler().getId());
    }

    @Surround.Finally
    private void angelica$endGuiSampling(@Surround.Carry("gui") boolean gui, @Surround.Carry("sampler") int sampler) {
        if (gui) RenderSystem.bindSamplerToUnit(0, sampler);
    }
}
