package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorld {

    @Inject(method = "onEntityRemoved", at = @At("HEAD"))
    private void angelica$removeEntity(Entity entity, CallbackInfo ci) {
        if (entity.worldObj.isRemote && entity instanceof IDynamicLightSource lightSource) {
            lightSource.angelica$setDynamicLightEnabled(false);
        }
    }

    @ModifyReturnValue(method = "getLightBrightnessForSkyBlocks", at = @At(value = "RETURN"))
    private int angelica$dynamiclights_getLightBrightnessForSkyBlocks(int lightmap, int x, int y, int z, int p_72802_4_){
        return DynamicLights.addDynamicLight((World) (Object) this, x, y, z, lightmap);
    }

}
