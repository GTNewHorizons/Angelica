package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.world;

import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.prupe.mcpatcher.cc.ColorizeWorld;
import com.prupe.mcpatcher.cc.Colorizer;

@Mixin(World.class)
public abstract class MixinWorld {

    @Unique
    private boolean mcpatcher_forge$computeSkyColor;

    @Inject(
        method = "getSkyColorBody(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;",
        at = @At("HEAD"),
        remap = false)
    private void modifyGetSkyColorBody1(Entity entity, float p_72833_2_, CallbackInfoReturnable<Vec3> cir) {
        this.mcpatcher_forge$computeSkyColor = ColorizeWorld.computeSkyColor((World) (Object) this, p_72833_2_);
    }

    @Inject(
        method = "getSkyColorBody(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;getSkyBlendColour(Lnet/minecraft/world/World;III)I",
            remap = false),
        remap = false)
    private void modifyGetSkyColorBody2(Entity entity, float p_72833_2_, CallbackInfoReturnable<Vec3> cir) {
        ColorizeWorld.setupForFog(entity);
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
        method = "getSkyColorBody(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 3,
        remap = false)
    private float modifyGetSkyColorBody3(float input) {
        if (this.mcpatcher_forge$computeSkyColor) {
            return Colorizer.setColor[0];
        }
        return input;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
        method = "getSkyColorBody(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 4,
        remap = false)
    private float modifyGetSkyColorBody4(float input) {
        if (this.mcpatcher_forge$computeSkyColor) {
            return Colorizer.setColor[1];
        }
        return input;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
        method = "getSkyColorBody(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 5,
        remap = false)
    private float modifyGetSkyColorBody5(float input) {
        if (this.mcpatcher_forge$computeSkyColor) {
            return Colorizer.setColor[2];
        }
        return input;
    }
}
