package com.gtnewhorizons.angelica.compat;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import com.gtnewhorizons.angelica.rendering.RenderingState;

@Getter
public class Camera {
    final Vector3d pos = new Vector3d();
    final BlockPos.Mutable blockPos = new BlockPos.Mutable();
    float pitch;
    float yaw;
    EntityLivingBase entity;
    boolean thirdPerson;
    final float partialTicks;

    public Camera(EntityLivingBase entity, float partialTicks) {
        this.partialTicks = partialTicks;
        Vector4f offset = new Vector4f(); // third person offset
        final Matrix4f inverseModelView = new Matrix4f(RenderingState.INSTANCE.getModelViewMatrix()).invert();
        inverseModelView.transform(offset);

        final double camX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks + offset.x;
        final double camY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks + offset.y;
        final double camZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks + offset.z;
        this.entity = entity;

        pos.set(camX, camY, camZ);
        blockPos.set((int)entity.posX, (int)entity.posY, (int)entity.posZ);
        pitch = entity.cameraPitch;
        yaw = entity.rotationYaw;
        thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView == 1;

    }

}
