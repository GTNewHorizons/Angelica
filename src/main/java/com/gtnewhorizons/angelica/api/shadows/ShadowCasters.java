package com.gtnewhorizons.angelica.api.shadows;

import com.gtnewhorizons.angelica.AngelicaMod;
import net.coderbot.iris.pipeline.ShadowRenderer;

import java.util.concurrent.CopyOnWriteArrayList;

/** {@link ShadowCaster} registry. Render thread only. */
public final class ShadowCasters {

    private static final CopyOnWriteArrayList<ShadowCaster> CASTERS = new CopyOnWriteArrayList<>();

    private ShadowCasters() {}

    public static void register(ShadowCaster caster) {
        CASTERS.addIfAbsent(caster);
    }

    public static void unregister(ShadowCaster caster) {
        CASTERS.remove(caster);
    }

    /** True while the shaderpack shadow pass is drawing */
    public static boolean isRenderingShadows() {
        return ShadowRenderer.ACTIVE;
    }

    /**
     * The pack's {@code shadowBlockEntities}. A caster drawing its own block entities should skip them when this is
     * false. Only valid during the shadow pass.
     */
    public static boolean shadowsBlockEntities() {
        return ShadowRenderer.BLOCK_ENTITIES;
    }

    /** True when the shadow pass draws light emitting block entities, even with {@link #shadowsBlockEntities} off */
    public static boolean shadowsLightBlockEntities() {
        return ShadowRenderer.LIGHT_BLOCK_ENTITIES;
    }

    public static boolean hasCasters() {
        return !CASTERS.isEmpty();
    }

    /** Called by the shadow renderer. A caster that throws is removed. */
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
