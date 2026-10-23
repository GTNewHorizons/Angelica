package com.gtnewhorizons.angelica.mixins.early.angelica.blur;

import com.gtnewhorizons.angelica.blur.MenuBlur;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GuiScreen.class)
public class MixinGuiScreen_MenuBlur {

    @ModifyConstant(method = "drawWorldBackground", constant = @Constant(intValue = 0xC0101010))
    private int angelica$blurGradientTop(int original) {
        return MenuBlur.getBackgroundColor(original, AngelicaConfig.menuBlurGradientTopColor);
    }

    @ModifyConstant(method = "drawWorldBackground", constant = @Constant(intValue = 0xD0101010))
    private int angelica$blurGradientBottom(int original) {
        return MenuBlur.getBackgroundColor(original, AngelicaConfig.menuBlurGradientBottomColor);
    }
}
