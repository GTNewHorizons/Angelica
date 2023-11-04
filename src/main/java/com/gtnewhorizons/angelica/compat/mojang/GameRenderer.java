package com.gtnewhorizons.angelica.compat.mojang;

public class GameRenderer {

    public void invokeBobHurt(PoseStack poseStack, float tickDelta) {}

    public void invokeBobView(PoseStack poseStack, float tickDelta) {}

    public boolean getRenderHand() {
        return true;
    }

    public boolean getPanoramicMode() {
        return true;
    }
}
