package com.gtnewhorizons.angelica.mixins.early.angelica.textures;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.GuiBlockSupersampling;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ForgeHooksClient.class, remap = false)
public class MixinForgeHooksClient_GuiSupersampling {
    @Surround(method = "renderInventoryItem", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraftforge/client/IItemRenderer;renderItem(Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V"))
    private static void angelica$beginGuiBlock() {
        @Surround.Carry final int previous = GuiBlockSupersampling.begin();
    }

    @Surround.Finally
    private static void angelica$endGuiBlock(@Surround.Carry int previous) {
        GuiBlockSupersampling.end(previous);
    }
}
