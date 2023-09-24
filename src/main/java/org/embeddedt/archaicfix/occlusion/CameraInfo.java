package org.embeddedt.archaicfix.occlusion;

import lombok.Getter;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public class CameraInfo {

    @Getter
    private static final CameraInfo instance = new CameraInfo();

    /** The transformed eye position, which takes the third person camera offset into account. */
    @Getter
    private double x, y, z;
    /** The untransformed eye position, which is not affected by the third person camera. It's always at the player character's eyes. */
    @Getter
    private double eyeX, eyeY, eyeZ;
    /** The chunk coordinates of the transformed eye position, which takes the third person camera offset into account. */
    @Getter
    private int chunkCoordX, chunkCoordY, chunkCoordZ;

    public void update(EntityLivingBase view, double tick) {
        eyeX = view.lastTickPosX + (view.posX - view.lastTickPosX) * tick;
        eyeY = view.lastTickPosY + (view.posY - view.lastTickPosY) * tick;
        eyeZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * tick;

        x = eyeX + ActiveRenderInfo.objectX;
        y = eyeY + ActiveRenderInfo.objectY;
        z = eyeZ + ActiveRenderInfo.objectZ;

        chunkCoordX = MathHelper.floor_double(x / 16.0);
        chunkCoordY = MathHelper.floor_double(y / 16.0);
        chunkCoordZ = MathHelper.floor_double(z / 16.0);
    }
}
