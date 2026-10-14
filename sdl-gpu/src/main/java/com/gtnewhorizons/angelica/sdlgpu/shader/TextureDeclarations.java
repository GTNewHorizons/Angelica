package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.List;

final class TextureDeclarations {
    private TextureDeclarations() {}

    record Declaration(List<String> names, List<GLSLParser.Typeless_declarationContext> declarators,
                       GLSLParser.Type_specifier_nonarrayContext type, boolean sampler, boolean array, int start, int stop) {}

    static List<Declaration> collect(ShaderIndex index) {
        final List<Declaration> declarations = new ArrayList<>();
        for (GLSLParser.DeclarationContext ctx : index.globalDeclarations()) {
            final GLSLParser.Init_declarator_listContext list = ctx.init_declarator_list();
            if (list == null || list.single_declaration() == null) continue;
            final GLSLParser.Fully_specified_typeContext fst = list.single_declaration().fully_specified_type();
            if (fst == null || fst.type_specifier() == null || !ShaderIndex.hasStorageQualifier(fst.type_qualifier(), "uniform")) continue;
            final GLSLParser.Type_specifier_nonarrayContext type = fst.type_specifier().type_specifier_nonarray();
            if (type == null) continue;
            final String text = type.getText();
            final boolean sampler = text.startsWith("sampler") || text.startsWith("isampler") || text.startsWith("usampler");
            if (!sampler && !text.startsWith("image") && !text.startsWith("iimage") && !text.startsWith("uimage")
                && !text.startsWith("texture") && !text.startsWith("itexture") && !text.startsWith("utexture")) continue;
            final List<GLSLParser.Typeless_declarationContext> declarators = ShaderIndex.declarators(list);
            final List<String> names = new ArrayList<>(declarators.size());
            for (GLSLParser.Typeless_declarationContext td : declarators) {
                if (td.IDENTIFIER() != null) names.add(td.IDENTIFIER().getText());
            }
            if (!names.isEmpty()) declarations.add(new Declaration(names, declarators, type, sampler,
                fst.type_specifier().array_specifier() != null, ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
        }
        return declarations;
    }
}
