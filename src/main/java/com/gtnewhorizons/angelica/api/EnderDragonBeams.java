package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;

public final class EnderDragonBeams {

    private static final NamespacedId CRYSTAL_BEAM = new NamespacedId("minecraft", "end_crystal_beam");
    private static final NamespacedId DEATH_RAYS = new NamespacedId("minecraft", "dragon_death_rays");

    private EnderDragonBeams() {}

    public static void beginCrystalBeam() {
        if (!AngelicaConfig.enableIris) return;
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
        CapturedRenderingState.INSTANCE.setCurrentNamedEntity(CRYSTAL_BEAM);
    }

    public static void endCrystalBeam() {
        if (!AngelicaConfig.enableIris) return;
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
    }

    public static void beginDeathRays() {
        if (!AngelicaConfig.enableIris) return;
        GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.LIGHTNING);
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
        CapturedRenderingState.INSTANCE.setCurrentNamedEntity(DEATH_RAYS);
    }

    public static void endDeathRays() {
        if (!AngelicaConfig.enableIris) return;
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
        GbufferPrograms.teardownSpecialRenderCondition();
    }
}
