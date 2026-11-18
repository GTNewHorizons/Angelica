package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.GuiBlockSupersampling;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher_GuiSupersampling {
    @Surround(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDF)V")
    private void angelica$beginGuiTileEntity() {
        @Surround.Carry final int previous = GuiBlockSupersampling.begin();
    }

    @Surround.Finally
    private void angelica$endGuiTileEntity(@Surround.Carry int previous) {
        GuiBlockSupersampling.end(previous);
    }
}
