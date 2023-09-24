package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP extends EntityPlayer {

    public MixinEntityPlayerMP(World worldIn, GameProfile gameProfileIn) {super(worldIn, gameProfileIn);}

    /**
     * @reason This is incorrectly set to 1, but not noticable in vanilla since the move logic
     * trusts the client about its y position after a move due to a bug: (y > -0.5 || y
     * < 0.5) rather than &&. If this is fixed, a player standing in moving water at the
     * edge of a block is considered to have "moved wrongly" and teleported onto the block.
     * <p>
     * Leaving this to 1 would also allow hacked clients to step up blocks without having
     * to jump (not increasing hunger).
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void afterInit(MinecraftServer server, WorldServer worldIn, GameProfile profile, ItemInWorldManager interactionManagerIn, CallbackInfo ci) {
        stepHeight = 0.7F;
    }
}
