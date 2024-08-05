package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import com.gtnewhorizons.angelica.mixins.interfaces.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.util.Timer;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements MinecraftAccessor {
    @Accessor("timer")
    public abstract Timer getTimer();
}
