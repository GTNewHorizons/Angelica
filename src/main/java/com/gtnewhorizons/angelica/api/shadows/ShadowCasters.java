package com.gtnewhorizons.angelica.api.shadows;

import com.gtnewhorizons.angelica.AngelicaMod;
import net.coderbot.iris.pipeline.ShadowRenderer;

import java.util.concurrent.CopyOnWriteArrayList;

/** Where {@link ShadowCaster}s are registered. Main thread only. */
public final class ShadowCasters {

    private static final CopyOnWriteArrayList<ShadowCaster> CASTERS = new CopyOnWriteArrayList<>();

    private ShadowCasters() {}

    public static void register(ShadowCaster caster) {
        CASTERS.addIfAbsent(caster);
    }

    public static void unregister(ShadowCaster caster) {
        CASTERS.remove(caster);
    }

    /** True while the shaderpack shadow pass is drawing, e.g. to skip work in a TESR that also feeds a caster. */
    public static boolean isRenderingShadows() {
        return ShadowRenderer.ACTIVE;
    }

    public static boolean hasCasters() {
        return !CASTERS.isEmpty();
    }

    /** Internal, called by the shadow renderer with its state set up. A caster that throws is dropped. */
    public static void renderAll(ShadowStage stage, double cameraX, double cameraY, double cameraZ,
        float partialTicks) {
        for (ShadowCaster caster : CASTERS) {
            try {
                caster.renderShadow(stage, cameraX, cameraY, cameraZ, partialTicks);
            } catch (Throwable t) {
                AngelicaMod.LOGGER.error("Shadow caster {} threw, removing it", caster, t);
                CASTERS.remove(caster);
            }
        }
    }
}
