package net.coderbot.iris.layer;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;

import java.util.Locale;

public record PassOverride(SpecialCondition special, Boolean translucent) {

    public static final PassOverride NONE = new PassOverride(null, null);

    public static PassOverride capture() {
        final SpecialCondition special = GbufferPrograms.getSpecialCondition();
        final Boolean translucent = GbufferPrograms.getDeclaredTranslucency();
        return special == null && translucent == null ? NONE : new PassOverride(special, translucent);
    }

    public void apply() {
        GbufferPrograms.setupSpecialRenderCondition(special);
        GbufferPrograms.setTranslucencyDeclaration(translucent);
    }

    public void clear() {
        GbufferPrograms.setupSpecialRenderCondition(null);
        GbufferPrograms.setTranslucencyDeclaration(null);
    }

    public String nameSuffix() {
        if (special == null && translucent == null) {
            return "";
        }
        return "_" + (special == null ? "plain" : special.name().toLowerCase(Locale.ROOT))
            + (translucent == null ? "" : (translucent ? "_translucent" : "_opaque"));
    }
}
