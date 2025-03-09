package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.mixins.interfaces.RenderGameOverlayEventAccessor;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGameOverlayEvent.class)
public abstract class MixinRenderGameOverlayEvent implements RenderGameOverlayEventAccessor {

    @Mutable
    @Accessor(value = "partialTicks", remap = false)
    public abstract void setPartialTicks(float value);

    @Mutable
    @Accessor(value = "resolution", remap = false)
    public abstract void setResolution(ScaledResolution resolution);

    @Mutable
    @Accessor(value = "mouseX", remap = false)
    public abstract void setMouseX(int mouseX);

    @Mutable
    @Accessor(value = "mouseY", remap = false)
    public abstract void setMouseY(int mouseY);
}
