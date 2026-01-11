package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import com.gtnewhorizons.angelica.mixins.interfaces.PrimedEntityAccessor;
import net.minecraft.entity.item.EntityTNTPrimed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTNTPrimed.class)
public abstract class MixinEntityTNTPrimed implements PrimedEntityAccessor {


    @Shadow
    public int fuse;

    @Override
    public int angelica$getLuminance() {
        // 80 is the default fuse length
        int lightLevel = (int) (7.0f * (1.0f - ((float) fuse / 80)));

        return Math.max(0, Math.min(7, lightLevel));
    }

    @Inject(method = "onUpdate", at =@At("RETURN"))
    private void angelica$onUpdate(CallbackInfo ci) {
        ((IDynamicLightSource)(Object)this).angelica$updateLights();
    }

}
