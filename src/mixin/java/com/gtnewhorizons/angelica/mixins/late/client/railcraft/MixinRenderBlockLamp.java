package com.gtnewhorizons.angelica.mixins.late.client.railcraft;

import net.minecraft.world.ChunkCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "mods.railcraft.client.render.RenderBlockLamp", remap = false)
public class MixinRenderBlockLamp {

    @ModifyConstant(
        method = "canConnect(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;)Z",
        constant = @Constant(classValue = ChunkCache.class),
        require = 0,
        expect = 0)
    private boolean angelica$acceptAnyBlockAccess(Object world, Class<?> type) {
        return true;
    }
}
