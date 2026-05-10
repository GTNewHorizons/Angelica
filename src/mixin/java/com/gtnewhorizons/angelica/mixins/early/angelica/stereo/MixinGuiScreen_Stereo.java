package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The HUD/GUI is rendered into a half-width viewport when stereo SBS_HALF + DUPLICATE is
 * active, so the underlying GUI coords need to be doubled in X for hit-testing to line up
 * with what the player visually sees in the left eye.
 *
 * <p>{@link MixinEntityRenderer_Stereo} already passes a doubled {@code mouseX} to
 * {@code drawScreen}; this mixin does the same for the click/release handler so tooltips,
 * clicks, and drags hit the right slots.</p>
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
