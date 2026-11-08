package com.gtnewhorizons.angelica.mixins.late.spark;

import com.gtnewhorizons.angelica.compat.spark.SparkKnownSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collection;

@Pseudo
@Mixin(targets = "me.lucko.spark.forge.plugin.Forge1710SparkPlugin", remap = false)
public abstract class MixinForge1710SparkPlugin {
    @Unique
    public Collection<Object> getKnownSources() {
        return SparkKnownSources.gather(getClass().getClassLoader());
    }
}
