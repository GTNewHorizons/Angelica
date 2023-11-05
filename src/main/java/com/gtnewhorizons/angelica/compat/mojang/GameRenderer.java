package com.gtnewhorizons.angelica.compat.mojang;

public class GameRenderer {

    public void invokeBobHurt(MatrixStack poseStack, float tickDelta) {}

    public void invokeBobView(MatrixStack poseStack, float tickDelta) {}

    public boolean getRenderHand() {
        return true;
    }

    public boolean getPanoramicMode() {
        return true;
    }
}
