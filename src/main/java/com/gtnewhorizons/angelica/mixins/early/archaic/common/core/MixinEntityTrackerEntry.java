package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S11PacketSpawnExperienceOrb;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {
    @Shadow public int lastScaledXPosition, lastScaledYPosition, lastScaledZPosition;

    @Shadow public int lastYaw;

    @Shadow public int lastPitch;

    @Shadow public Entity myEntity;

    @Shadow public int ticks;

    @Inject(method = "sendLocationToAllClients", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityTrackerEntry;sendMetadataToAllAssociatedPlayers()V", ordinal = 1, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void saveIfTeleported(List<EntityPlayer> clientList, CallbackInfo ci, int i, int j, int k, int l, int i1, int j1, int k1, int l1, Object object) {
        if(object instanceof S18PacketEntityTeleport) {
            this.lastScaledXPosition = i;
            this.lastScaledYPosition = j;
            this.lastScaledZPosition = k;
            this.lastYaw = l;
            this.lastPitch = i1;
        }
    }

    @Inject(method = "func_151260_c", at = @At("RETURN"), cancellable = true)
    private void useCorrectSpawnPosition(CallbackInfoReturnable<Packet> cir) {
        if (!(this.myEntity instanceof EntityItemFrame) && !(this.myEntity instanceof EntityLeashKnot)) {
            Packet packet = cir.getReturnValue();
            if(packet instanceof S0EPacketSpawnObject) {
                S0EPacketSpawnObject spawnObject = (S0EPacketSpawnObject)packet;
                spawnObject.func_148996_a(this.lastScaledXPosition);
                spawnObject.func_148995_b(this.lastScaledYPosition);
                spawnObject.func_149005_c(this.lastScaledZPosition);
            } else if(packet instanceof S0CPacketSpawnPlayer) {
                S0CPacketSpawnPlayer spawnPlayer = (S0CPacketSpawnPlayer)packet;
                spawnPlayer.field_148956_c = this.lastScaledXPosition;
                spawnPlayer.field_148953_d = this.lastScaledYPosition;
                spawnPlayer.field_148954_e = this.lastScaledZPosition;
            } else if(packet instanceof S11PacketSpawnExperienceOrb) {
                S11PacketSpawnExperienceOrb spawnXpOrb = (S11PacketSpawnExperienceOrb)packet;
                spawnXpOrb.field_148990_b = this.lastScaledXPosition;
                spawnXpOrb.field_148991_c = this.lastScaledYPosition;
                spawnXpOrb.field_148988_d = this.lastScaledZPosition;
            }
        }
    }

    private boolean arch$wouldSendPacket() {
        return (this.ticks > 0 || this.myEntity instanceof EntityArrow);
    }

    @ModifyVariable(method = "sendLocationToAllClients", at = @At("STORE"), index = 11, name = "flag")
    private boolean avoidSavingIfPacketNotSent1(boolean incoming) {
        return incoming && arch$wouldSendPacket();
    }

    @ModifyVariable(method = "sendLocationToAllClients", at = @At("STORE"), index = 12, name = "flag1")
    private boolean avoidSavingIfPacketNotSent2(boolean incoming) {
        return incoming && arch$wouldSendPacket();
    }
}
