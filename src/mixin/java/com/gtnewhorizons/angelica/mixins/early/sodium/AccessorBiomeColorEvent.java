package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraftforge.event.terraingen.BiomeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeEvent.BiomeColor.class)
public interface AccessorBiomeColorEvent {

    @Mutable
    @Accessor(value = "originalColor", remap = false)
    @Mutable
    void setOriginalColor(int value);
}
