package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.List;
import java.util.Map;

final class SamplerAliasDeduplicator {

    private record AliasGroup(String canonical, List<String> aliases) {}

    private static final List<AliasGroup> GROUPS = List.of(
        new AliasGroup("tex", List.of("texture", "gtexture", "textureAtlas")),
        new AliasGroup("depthtex0", List.of("gdepthtex")),
        new AliasGroup("dhDepthTex0", List.of("dhDepthTex"))
    );

    private SamplerAliasDeduplicator() {}

    static void transformGrouped(Map<PatchShaderType, Transformer> trees, Parameters parameters) {
        for (AliasGroup group : GROUPS) {
            applyGroup(trees, group);
        }
    }

    private static void applyGroup(Map<PatchShaderType, Transformer> trees, AliasGroup group) {
        String canonical = group.canonical();
        for (String alias : group.aliases()) {
            for (Transformer t : trees.values()) {
                if (t == null || t.findType(alias) == 0) continue;
                if (t.findType(canonical) != 0) {
                    t.removeVariable(alias);
                    t.rename(alias, canonical);
                } else {
                    t.rename(alias, canonical);
                }
            }
        }
    }
}
