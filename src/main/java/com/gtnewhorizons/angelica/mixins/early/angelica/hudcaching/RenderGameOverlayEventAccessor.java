package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.terraingen.BiomeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGameOverlayEvent.class)
public interface RenderGameOverlayEventAccessor {
    @Accessor(value = "partialTicks", remap = false)
    void setPartialTicks(float value);

    @Accessor(value = "resolution", remap = false)
    void setResolution(ScaledResolution resolution);

    @Accessor(value = "mouseX", remap = false)
    void setMouseX(int mouseX);

    @Accessor(value = "mouseY", remap = false)
    void setMouseY(int mouseY);
}
