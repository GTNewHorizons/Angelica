package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.potion;

import net.minecraft.potion.Potion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.prupe.mcpatcher.cc.ColorizeItem;

import com.gtnewhorizons.angelica.mixins.interfaces.PotionExpansion;

@Mixin(Potion.class)
public abstract class MixinPotion implements PotionExpansion {

    @Unique
    private int mcpatcher_forge$origColor;

    @Inject(method = "setPotionName(Ljava/lang/String;)Lnet/minecraft/potion/Potion;", at = @At("RETURN"))
    private void modifySetPotionName(String name, CallbackInfoReturnable<Potion> cir) {
        ColorizeItem.setupPotion((Potion) (Object) this);
    }

    public void setOrigColor(int color) {
        this.mcpatcher_forge$origColor = color;
    }

    public int getOrigColor() {
        return this.mcpatcher_forge$origColor;
    }
}
