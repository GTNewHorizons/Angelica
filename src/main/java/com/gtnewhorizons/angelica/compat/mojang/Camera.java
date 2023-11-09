package com.gtnewhorizons.angelica.compat.mojang;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.joml.Vector3d;

@Getter
public class Camera {
    Vector3d pos = new Vector3d();
    BlockPos.Mutable blockPos = new BlockPos.Mutable();
    float pitch;
    float yaw;
    EntityLivingBase entity;
    boolean thirdPerson;

    public Camera(EntityLivingBase entity, float partialTicks) {
        final double camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        pos.set(camX, camY, camZ);
        blockPos.set((int)entity.posX, (int)entity.posY, (int)entity.posZ);
        pitch = entity.cameraPitch;
        yaw = entity.rotationYaw;
        thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView == 1;

    }

}
