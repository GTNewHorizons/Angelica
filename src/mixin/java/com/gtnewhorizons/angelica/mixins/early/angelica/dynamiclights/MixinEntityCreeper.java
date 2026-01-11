package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.mixins.interfaces.PrimedEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityCreeper.class)
public abstract class MixinEntityCreeper implements PrimedEntityAccessor {

    @Shadow
    public abstract float getCreeperFlashIntensity(float p_70831_1_);

    @Shadow
    public abstract int getCreeperState();

    @Override
    public int angelica$getLuminance() {
        // ignore if not fused
        if (getCreeperState() != 1) return 0;

        float intensity = this.getCreeperFlashIntensity(Minecraft.getMinecraft().timer.renderPartialTicks);
        return (int) (intensity * 7);
    }
}
