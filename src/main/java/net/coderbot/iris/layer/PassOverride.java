package net.coderbot.iris.layer;

import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.pipeline.WorldRenderingPhase;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Locale;
import java.util.Objects;

public final class PassOverride {

    public static final PassOverride NONE = new PassOverride(null, null, null);

    private static final ObjectArrayList<PassOverride> INTERNED = new ObjectArrayList<>();
    private static final ObjectArrayList<Boolean> translucencyStack = new ObjectArrayList<>();

    private final SpecialCondition special;
    private final Boolean translucent;
    private final WorldRenderingPhase phase;

    private PassOverride(SpecialCondition special, Boolean translucent, WorldRenderingPhase phase) {
        this.special = special;
        this.translucent = translucent;
        this.phase = phase;
    }

    public static PassOverride of(SpecialCondition special, Boolean translucent, WorldRenderingPhase phase) {
        if (special == null && translucent == null && phase == null) {
            return NONE;
        }
        for (int i = 0, n = INTERNED.size(); i < n; i++) {
            final PassOverride candidate = INTERNED.get(i);
            if (candidate.special == special && candidate.phase == phase && Objects.equals(candidate.translucent, translucent)) {
                return candidate;
            }
        }
        final PassOverride created = new PassOverride(special, translucent, phase);
        INTERNED.add(created);
        return created;
    }

    public static PassOverride capture() {
        return of(GbufferPrograms.getSpecialCondition(), GbufferPrograms.getDeclaredTranslucency(), GbufferPrograms.getOverridePhase());
    }

    public void apply() {
        GbufferPrograms.setupSpecialRenderCondition(special);
        translucencyStack.add(GbufferPrograms.beginTranslucencyDeclaration(translucent));
        GbufferPrograms.pushOverridePhase(phase);
    }

    public void clear() {
        GbufferPrograms.popOverridePhase();
        GbufferPrograms.endTranslucencyDeclaration(translucencyStack.pop());
        GbufferPrograms.teardownSpecialRenderCondition();
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
