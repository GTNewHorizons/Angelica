package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.achievement.GuiAchievement;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla {@code Minecraft.runGameLoop} calls {@code guiAchievement.updateAchievementWindow}
 * after the {@code EntityRenderer.updateCameraAndRender} block — outside every stereo
 * redirect. The popup's own setup resets the GL viewport to the full display each call
 * (see {@code GuiAchievement.func_146258_c}), so left alone it draws once at the top-right
 * of the full window and only one eye sees it. Redirect the call and post it per eye, with
 * {@code StereoState.enterGuiPass} active so {@link com.gtnewhorizons.angelica.glsm.GLStateManager}
 * can route the inner full-FB viewport reset to the current eye's region.
 */
@Mixin(value = Minecraft.class, priority = 1100)
public class MixinMinecraft_StereoAchievement {

    @Redirect(
        method = "runGameLoop",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/achievement/GuiAchievement;func_146254_a()V"
        )
    )
    private void angelica$stereoAchievement(GuiAchievement popup) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        final boolean stereoActive = mode != null && mode.isActive()
            && StereoState.INSTANCE.getFrameHudMode() == StereoHudMode.DUPLICATE
            && mode.isSideBySide() && mode.isHalf();
        if (!stereoActive) {
            popup.func_146254_a();
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final int eyeW = fullW / 2;
        final int eyeH = fullH;

        GL11.glViewport(0, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(0, 0, eyeW, eyeH);
        // func_146254_a's final renderItemAndEffectIntoGUI leaves GL_BLEND disabled, so the second
        // pass would render the popup background's transparent regions as opaque black. Set the
        // expected GUI blend state explicitly before each pass.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        popup.func_146254_a();
        StereoState.INSTANCE.exitGuiPass();

        GL11.glViewport(eyeW, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(eyeW, 0, eyeW, eyeH);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        popup.func_146254_a();
        StereoState.INSTANCE.exitGuiPass();

        GL11.glViewport(0, 0, fullW, fullH);
    }
}
