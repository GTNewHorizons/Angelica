package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.mixins.interfaces.RenderGameOverlayEventAccessor;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGameOverlayEvent.class)
public abstract class MixinRenderGameOverlayEvent implements RenderGameOverlayEventAccessor {
    @Accessor(value = "partialTicks", remap = false)
    public abstract void setPartialTicks(float value);

    @Accessor(value = "resolution", remap = false)
    public abstract void setResolution(ScaledResolution resolution);

    @Accessor(value = "mouseX", remap = false)
    public abstract void setMouseX(int mouseX);

    @Accessor(value = "mouseY", remap = false)
    public abstract void setMouseY(int mouseY);
}
