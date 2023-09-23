package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.renderer.ActiveRenderInfo;

public class PreviousActiveRenderInfo {
    public static float objectX = Float.NaN, objectY, objectZ;
    public static float rotationX, rotationZ, rotationYZ;

    public static boolean changed() {
        return PreviousActiveRenderInfo.objectX != ActiveRenderInfo.objectX ||
            PreviousActiveRenderInfo.objectY != ActiveRenderInfo.objectY ||
            PreviousActiveRenderInfo.objectZ != ActiveRenderInfo.objectZ ||
            PreviousActiveRenderInfo.rotationX != ActiveRenderInfo.rotationX ||
            PreviousActiveRenderInfo.rotationYZ != ActiveRenderInfo.rotationYZ ||
            PreviousActiveRenderInfo.rotationZ != ActiveRenderInfo.rotationZ;
    }

    public static void update() {
        PreviousActiveRenderInfo.objectX = ActiveRenderInfo.objectX;
        PreviousActiveRenderInfo.objectY = ActiveRenderInfo.objectY;
        PreviousActiveRenderInfo.objectZ = ActiveRenderInfo.objectZ;
        PreviousActiveRenderInfo.rotationX = ActiveRenderInfo.rotationX;
        PreviousActiveRenderInfo.rotationYZ = ActiveRenderInfo.rotationYZ;
        PreviousActiveRenderInfo.rotationZ = ActiveRenderInfo.rotationZ;
    }
}
