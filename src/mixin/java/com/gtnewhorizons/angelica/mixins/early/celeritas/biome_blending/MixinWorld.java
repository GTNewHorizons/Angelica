package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.celeritas.SkyColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class MixinWorld {
    @Unique private final SkyColorCache angelica$skyColorCache = new SkyColorCache();

    @Surround(
        method = "getSkyColorBody",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;getSkyBlendColour(Lnet/minecraft/world/World;III)I",
            remap = false),
        remap = false)
    private void angelica$blendSkyColor(World world, @Surround.Local(argsOnly = true) Entity camera, @Surround.Local(argsOnly = true) float tickDelta) {
        @Surround.Carry
        final int color = angelica$skyColorCache.getColor(world,
            camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * tickDelta,
            camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * tickDelta,
            camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * tickDelta,
            SmoothBiomeColorCache.configuredRadius());
        @Surround.Skip
        final boolean skip = true;
    }

    @Surround.Skipped
    private int angelica$blendedSkyColor(@Surround.Carry int color) {
        return color;
    }
}
