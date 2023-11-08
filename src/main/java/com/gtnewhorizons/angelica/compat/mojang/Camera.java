package com.gtnewhorizons.angelica.compat.mojang;

import org.joml.Vector3d;

public class Camera {

    public boolean isDetached() {
        return true;
    }

    public Object getEntity() {
        return null;
    }

    public BlockPos getBlockPos() {
        return new BlockPos();
    }

    public Vector3d getPos() {
        return new Vector3d();
    }

    public float getPitch() {
        return 0;
    }

    public float getYaw() {
        return 0;
    }
}
