package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mirror of the {@code drawScreen} mouseX doubling in {@link MixinEntityRenderer_Stereo} for the
 * vanilla click/release path. Under SBS_HALF + DUPLICATE the GUI renders into a half-width
 * viewport, so the locally-computed mouseX must be doubled for clicks/drags to hit the slot the
 * player sees under their cursor.
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreen_Stereo {

    @ModifyVariable(method = "handleMouseInput", at = @At("STORE"), ordinal = 0)
    private int angelica$stereoRemapClickX(int x) {
        final StereoMode mode = AngelicaConfig.stereoscopicMode;
        if (mode == null || !mode.isActive()) return x;
        if (AngelicaConfig.stereoHudMode != StereoHudMode.DUPLICATE) return x;
        if (mode.isSideBySide() && mode.isHalf()) return x * 2;
        return x;
    }
}
