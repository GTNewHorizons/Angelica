package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;

public final class GameModeUtil {
    private GameModeUtil() {
    }

    public static boolean isSpectator() {
        final PlayerControllerMP controller = Minecraft.getMinecraft().playerController;
        if (controller == null) {
            return false;
        }
        return controller.currentGameType.getID() == 3;
    }
}
