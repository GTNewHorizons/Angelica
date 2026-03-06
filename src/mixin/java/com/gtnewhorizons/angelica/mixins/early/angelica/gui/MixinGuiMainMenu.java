package com.gtnewhorizons.angelica.mixins.early.angelica.gui;

import com.gtnewhorizons.angelica.render.PanoramaRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Shadow private int panoramaTimer;
    @Shadow private static ResourceLocation[] titlePanoramaPaths;

    /**
     * @author Angelica
     * @reason Replace panorama with modern equivalent
     */
    @Overwrite
    private void renderSkybox(int mouseX, int mouseY, float partialTicks) {
        PanoramaRenderer.getInstance().renderSkybox(this.panoramaTimer, partialTicks, titlePanoramaPaths, this.mc, this.width, this.height, this.zLevel);
    }
}
