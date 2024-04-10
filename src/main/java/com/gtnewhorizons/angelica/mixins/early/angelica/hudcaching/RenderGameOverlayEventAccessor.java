package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.terraingen.BiomeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGameOverlayEvent.class)
public interface RenderGameOverlayEventAccessor {
    @Accessor(value = "partialTicks", remap = false)
    void setPartialTicks(float value);
}
