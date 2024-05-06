package com.gtnewhorizons.angelica.mixins.early.angelica.dynamiclights;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorld {

    @Shadow
    protected abstract void initialize(WorldSettings p_72963_1_);
    @Shadow
    public abstract Block getBlock(int p_147439_1_, int p_147439_2_, int p_147439_3_);

    @Inject(method = "onEntityRemoved", at = @At("HEAD"))
    private void angelica$removeEntity(Entity entity, CallbackInfo ci){
        if (entity instanceof IDynamicLightSource lightSource){
            lightSource.angelica$setDynamicLightEnabled(false);
        }
    }

    @ModifyReturnValue(method = "getLightBrightnessForSkyBlocks", at = @At(value = "RETURN"))
    private int angelica$dynamiclights_getLightBrightnessForSkyBlocks(int lightmap, int p_72802_1_, int p_72802_2_, int p_72802_3_, int p_72802_4_){
        if (DynamicLights.isEnabled()){
            return DynamicLights.get().getLightmapWithDynamicLight(p_72802_1_, p_72802_2_, p_72802_3_, lightmap);
        }
        return lightmap;
    }

}
