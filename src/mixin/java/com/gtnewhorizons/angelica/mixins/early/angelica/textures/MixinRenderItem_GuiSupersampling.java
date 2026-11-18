package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.renderer.entity.RenderItem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderItem.class)
public class MixinRenderItem_GuiSupersampling {

    @Surround(id = "withEffects", method = "renderItemAndEffectIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;II)V")
    private void angelica$beginGuiEffects() {
        @Surround.Carry final boolean previous = RenderingState.INSTANCE.isRenderingGuiItem();
        RenderingState.INSTANCE.setRenderingGuiItem(true);
    }

    @Surround.Finally("withEffects")
    private void angelica$endGuiEffects(@Surround.Carry boolean previous) {
        RenderingState.INSTANCE.setRenderingGuiItem(previous);
    }

    @Surround(id = "base", method = "renderItemIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V", remap = false)
    private void angelica$beginGuiBase() {
        @Surround.Carry final boolean previous = RenderingState.INSTANCE.isRenderingGuiItem();
        RenderingState.INSTANCE.setRenderingGuiItem(true);
    }

    @Surround.Finally("base")
    private void angelica$endGuiBase(@Surround.Carry boolean previous) {
        RenderingState.INSTANCE.setRenderingGuiItem(previous);
    }
}
