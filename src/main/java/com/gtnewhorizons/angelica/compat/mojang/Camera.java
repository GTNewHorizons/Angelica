package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

/**
 * Singleton camera instance that tracks the current camera position and orientation.
 */
@Getter
public class Camera {
    public static final Camera INSTANCE = new Camera();

    private final Vector3d pos = new Vector3d();
    private final BlockPos blockPos = new BlockPos();
    private final Vector4f offset = new Vector4f();
    private final Matrix4f inverseModelView = new Matrix4f();

    private float pitch;
    private float yaw;
    private EntityLivingBase entity;
    private boolean thirdPerson;
    private float partialTicks;

    private Camera() {
    }

    /**
     * Updates the camera position and orientation based on the current entity and partial ticks.
     *
     * @param entity The entity to track (usually the render view entity)
     * @param partialTicks The partial tick time for interpolation
     */
    public void update(EntityLivingBase entity, float partialTicks) {
        this.partialTicks = partialTicks;
        this.entity = entity;

        inverseModelView.set(RenderingState.INSTANCE.getModelViewMatrix()).invert();
        offset.set(0, 0, 0, 1);
        inverseModelView.transform(offset);

        // Entity position (interpolated, at feet level)
        final double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        final double eyeHeight = entity.getEyeHeight();

        // Camera position in world space at eye level
        final double camX = entityX + offset.x;
        final double camY = entityY + eyeHeight + offset.y;
        final double camZ = entityZ + offset.z;

        pos.set(camX, camY, camZ);
        blockPos.set(MathHelper.floor_double(camX), MathHelper.floor_double(camY), MathHelper.floor_double(camZ));
        pitch = entity.cameraPitch;
        yaw = entity.rotationYaw;
        thirdPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView > 0;
    }
}
