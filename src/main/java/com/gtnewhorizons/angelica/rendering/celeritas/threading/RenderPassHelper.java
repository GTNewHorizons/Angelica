package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraftforge.client.ForgeHooksClient;

public final class RenderPassHelper {
    private static final ThreadLocal<Integer> threadLocalPass = ThreadLocal.withInitial(() -> -1);

    private RenderPassHelper() {}

    public static int getWorldRenderPass() {
        return GLStateManager.isMainThread() ? ForgeHooksClient.worldRenderPass : threadLocalPass.get();
    }

    public static void setWorldRenderPass(int pass) {
        if (GLStateManager.isMainThread()) {
            ForgeHooksClient.worldRenderPass = pass;
        } else {
            threadLocalPass.set(pass);
        }
    }

    public static void resetWorldRenderPass() {
        setWorldRenderPass(-1);
    }
}
