package net.coderbot.iris.layer;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.pipeline.WorldRenderingPhase;

import java.util.Locale;

public record PassOverride(SpecialCondition special, Boolean translucent, WorldRenderingPhase phase) {

    public static final PassOverride NONE = new PassOverride(null, null, null);

    public PassOverride(SpecialCondition special, Boolean translucent) {
        this(special, translucent, null);
    }

    public static PassOverride capture() {
        final SpecialCondition special = GbufferPrograms.getSpecialCondition();
        final Boolean translucent = GbufferPrograms.getDeclaredTranslucency();
        final WorldRenderingPhase phase = GbufferPrograms.getOverridePhase();
        return special == null && translucent == null && phase == null
            ? NONE : new PassOverride(special, translucent, phase);
    }

    public void apply() {
        GbufferPrograms.setupSpecialRenderCondition(special);
        GbufferPrograms.setTranslucencyDeclaration(translucent);
        GbufferPrograms.setOverridePhase(phase);
    }

    public void clear() {
        GbufferPrograms.setupSpecialRenderCondition(null);
        GbufferPrograms.setTranslucencyDeclaration(null);
        GbufferPrograms.setOverridePhase(null);
    }

    public boolean isEntityPhase() {
        return phase == WorldRenderingPhase.ENTITIES;
    }

    public String nameSuffix() {
        if (special == null && translucent == null && phase == null) {
            return "";
        }
        return "_" + (special == null ? "plain" : special.name().toLowerCase(Locale.ROOT))
            + (translucent == null ? "" : (translucent ? "_translucent" : "_opaque"))
            + (phase == null ? "" : "_" + phase.name().toLowerCase(Locale.ROOT));
    }
}
