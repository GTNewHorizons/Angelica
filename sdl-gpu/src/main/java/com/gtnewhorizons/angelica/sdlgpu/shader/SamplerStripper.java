package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public final class SamplerStripper {

    private static final Logger LOGGER = LogManager.getLogger("Angelica-SDLGPU");

    private SamplerStripper() {}

    private record SamplerDecl(List<String> names, int start, int stop) {}

    public static void collectEdits(ShaderIndex index, String source, List<Edit> edits) {
        final List<SamplerDecl> samplers = new ArrayList<>();
        for (GLSLParser.DeclarationContext ctx : index.globalDeclarations()) {
            final SamplerDecl decl = samplerDeclaration(ctx);
            if (decl != null) samplers.add(decl);
        }
        if (samplers.isEmpty()) return;

        Set<String> directiveRefs = null;
        for (SamplerDecl s : samplers) {
            if (!allUnreferenced(s, samplers, index)) continue;
            if (directiveRefs == null) directiveRefs = GlslTransformUtils.identifiersInDirectiveText(source);
            if (anyIn(s, directiveRefs)) {
                LOGGER.debug("Keeping sampler {}: referenced only inside a preprocessor block", s.names());
                continue;
            }
            edits.add(new Edit(s.start(), s.stop(), ""));
        }
    }

    private static @Nullable SamplerDecl samplerDeclaration(GLSLParser.DeclarationContext ctx) {
        final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
        if (idl == null) return null;
        final GLSLParser.Single_declarationContext single = idl.single_declaration();
        if (single == null || single.fully_specified_type() == null) return null;
        final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
        if (!ShaderIndex.hasStorageQualifier(fst.type_qualifier(), "uniform")) return null;
        if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return null;
        if (!isSamplerType(fst.type_specifier().type_specifier_nonarray().getText())) return null;

        final List<String> names = new ArrayList<>(1);
        for (GLSLParser.Typeless_declarationContext td : ShaderIndex.declarators(idl)) {
            if (td.IDENTIFIER() != null) names.add(td.IDENTIFIER().getText());
        }
        return names.isEmpty() ? null : new SamplerDecl(names, ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex());
    }

    private static boolean insideAny(List<SamplerDecl> samplers, int pos) {
        for (SamplerDecl s : samplers) {
            if (pos >= s.start() && pos <= s.stop()) return true;
        }
        return false;
    }

    private static boolean allUnreferenced(SamplerDecl s, List<SamplerDecl> samplers, ShaderIndex index) {
        for (String name : s.names()) {
            for (Token tok : index.occurrences(name)) {
                if (!insideAny(samplers, tok.getStartIndex())) return false;
            }
        }
        return true;
    }

    private static boolean anyIn(SamplerDecl s, Set<String> names) {
        for (String name : s.names()) {
            if (names.contains(name)) return true;
        }
        return false;
    }

    private static boolean isSamplerType(String t) {
        return t.startsWith("sampler") || t.startsWith("isampler") || t.startsWith("usampler");
    }
}
