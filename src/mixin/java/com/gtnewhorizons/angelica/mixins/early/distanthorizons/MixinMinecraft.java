package com.gtnewhorizons.angelica.mixins.early.distanthorizons;

import com.seibel.distanthorizons.interfaces.IMixinMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IMixinMinecraft {
    @Override
    @Accessor("timer")
    public abstract Timer getTimer();
}
