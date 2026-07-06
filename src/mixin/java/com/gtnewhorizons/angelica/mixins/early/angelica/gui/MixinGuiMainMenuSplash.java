package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenuSplash {

    @Unique
    private static final String VANILLA_GL_SPLASH = "OpenGL 1.2!";

    @Shadow private String splashText;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void angelica$rewriteGlSplash(CallbackInfo ci) {
        if (!VANILLA_GL_SPLASH.equals(this.splashText)) return;

        final int major = RenderSystem.getGlMajor();
        final int minor = RenderSystem.getGlMinor();
        if (major == 0) return;

        this.splashText = "OpenGL " + major + "." + minor + (RenderSystem.isCoreProfile() ? " core!" : "!");
    }
}
