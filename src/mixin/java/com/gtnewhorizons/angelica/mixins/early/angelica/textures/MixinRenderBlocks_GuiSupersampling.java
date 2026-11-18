package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.GuiBlockSupersampling;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_GuiSupersampling {
    @Surround(method = "renderBlockAsItem(Lnet/minecraft/block/Block;IF)V")
    private void angelica$beginGuiBlock() {
        @Surround.Carry final int previous = GuiBlockSupersampling.begin();
    }

    @Surround.Finally
    private void angelica$endGuiBlock(@Surround.Carry int previous) {
        GuiBlockSupersampling.end(previous);
    }
}
