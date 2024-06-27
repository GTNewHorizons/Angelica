package com.gtnewhorizons.angelica.mixins.early.angelica.archaic;

import cpw.mods.fml.common.ModContainer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.GuiIngameForge;
import com.gtnewhorizons.angelica.helpers.LoadControllerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=GuiIngameForge.class, remap = false)
public class MixinGuiIngameForge {
    @Redirect(method = "renderHUDText", at = @At(value = "FIELD", target = "Lnet/minecraft/world/biome/BiomeGenBase;biomeName:Ljava/lang/String;"))
    private String getModNameWithBiome(BiomeGenBase biome) {
        ModContainer theMod = LoadControllerHelper.getOwningMod(biome.getClass());
        if(theMod == null)
            return biome.biomeName;
        return biome.biomeName + " [" + theMod.getName() + "]";
    }
}
