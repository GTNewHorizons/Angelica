package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import com.gtnewhorizons.angelica.sdlgpu.shader.TextureDeclarations.Declaration;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SamplerStripper {

    private static final Logger LOGGER = LogManager.getLogger("Angelica-SDLGPU");

    private SamplerStripper() {}

    static Set<String> collectEdits(ShaderIndex index, List<Declaration> declarations, List<Edit> edits) {
        Set<String> directiveRefs = null;
        final Set<String> removed = new HashSet<>();
        for (Declaration s : declarations) {
            if (!s.sampler() || !allUnreferenced(s, declarations, index)) continue;
            if (directiveRefs == null) directiveRefs = index.directives().identifiers();
            if (anyIn(s, directiveRefs)) {
                LOGGER.debug("Keeping sampler {}: referenced only inside a preprocessor block", s.names());
                continue;
            }
            edits.add(new Edit(s.start(), s.stop(), ""));
            removed.addAll(s.names());
        }
        return removed;
    }

    private static boolean insideAnySampler(List<Declaration> declarations, int pos) {
        for (Declaration s : declarations) {
            if (s.sampler() && pos >= s.start() && pos <= s.stop()) return true;
        }
        return false;
    }

    private static boolean allUnreferenced(Declaration s, List<Declaration> declarations, ShaderIndex index) {
        for (String name : s.names()) {
            for (Token tok : index.occurrences(name)) {
                if (!insideAnySampler(declarations, tok.getStartIndex())) return false;
            }
        }
        return true;
    }

    private static boolean anyIn(Declaration s, Set<String> names) {
        for (String name : s.names()) {
            if (names.contains(name)) return true;
        }
        return false;
    }
}
