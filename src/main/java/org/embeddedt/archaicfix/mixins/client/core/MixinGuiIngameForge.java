package org.embeddedt.archaicfix.mixins.client.core;

import cpw.mods.fml.common.ModContainer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.GuiIngameForge;
import org.embeddedt.archaicfix.helpers.LoadControllerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {
    @Redirect(method = "renderHUDText", at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/BiomeGenBase;biomeName:Ljava/lang/String;"))
    private String getModNameWithBiome(BiomeGenBase biome) {
        ModContainer theMod = LoadControllerHelper.getOwningMod(biome.getClass());
        if(theMod == null)
            return biome.biomeName;
        return biome.biomeName + " [" + theMod.getName() + "]";
    }
}
