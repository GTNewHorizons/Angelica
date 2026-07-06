package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraftforge.client.ForgeHooksClient;

public final class RenderPassHelper {

    private static final class PassIntHolder {
        int pass = -1;
    }

    private static final ThreadLocal<PassIntHolder> threadLocalPass = ThreadLocal.withInitial(PassIntHolder::new);

    private RenderPassHelper() {}

    public static int getWorldRenderPass() {
        return GLStateManager.isMainThread() ? ForgeHooksClient.worldRenderPass : threadLocalPass.get().pass;
    }

    public static void setWorldRenderPass(int pass) {
        if (GLStateManager.isMainThread()) {
            ForgeHooksClient.worldRenderPass = pass;
        } else {
            threadLocalPass.get().pass = pass;
        }
    }

    public static void resetWorldRenderPass() {
        setWorldRenderPass(-1);
    }
}
