package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.applyEdits;

public final class UscaledRetype {

    static final String SUFFIX = "_i";

    public record Attrib(String name, int location, int declVecSize, int boundVecSize, boolean signed) {}

    private record RetypeKey(String source, List<Attrib> attribs) {}

    private static final int CACHE_MAX = 64;
    private static final Object2ObjectLinkedOpenHashMap<RetypeKey, String> CACHE = new Object2ObjectLinkedOpenHashMap<>();

    private UscaledRetype() {}

    public static String retype(String source, List<Attrib> attribs) {
        if (attribs.isEmpty()) return source;

        final RetypeKey key = new RetypeKey(source, List.copyOf(attribs));
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.getAndMoveToFirst(key);
        }
        final String result = retypeUncached(source, attribs);
        synchronized (CACHE) {
            CACHE.putAndMoveToFirst(key, result);
            while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
        }
        return result;
    }

    private static String retypeUncached(String source, List<Attrib> attribs) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            return null;
        }

        final List<Edit> edits = new ArrayList<>();
        final boolean[] found = new boolean[attribs.size()];

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterDeclaration(GLSLParser.DeclarationContext ctx) {
                final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
                if (idl == null) return;
                final GLSLParser.Single_declarationContext single = idl.single_declaration();
                if (single == null || single.fully_specified_type() == null) return;
                final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
                if (fst.type_qualifier() == null) return;

                boolean isIn = false;
                for (GLSLParser.Single_type_qualifierContext stq : fst.type_qualifier().single_type_qualifier()) {
                    if (stq.storage_qualifier() != null && "in".equals(stq.storage_qualifier().getText())) {
                        isIn = true;
                        break;
                    }
                }
                if (!isIn) return;
                if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return;
                if (single.typeless_declaration() == null || single.typeless_declaration().IDENTIFIER() == null) return;

                final String name = single.typeless_declaration().IDENTIFIER().getText();
                int idx = -1;
                for (int i = 0; i < attribs.size(); i++) {
                    if (attribs.get(i).name().equals(name)) { idx = i; break; }
                }
                if (idx < 0 || found[idx]) return;
                found[idx] = true;
                final Attrib a = attribs.get(idx);

                final GLSLParser.Type_specifier_nonarrayContext typeCtx = fst.type_specifier().type_specifier_nonarray();
                edits.add(new Edit(typeCtx.getStart().getStartIndex(), typeCtx.getStop().getStopIndex(), intTypeName(a.boundVecSize(), a.signed())));

                final Token nameTok = single.typeless_declaration().IDENTIFIER().getSymbol();
                edits.add(new Edit(nameTok.getStartIndex(), nameTok.getStopIndex(), name + SUFFIX));

                final int declEnd = ctx.getStop().getStopIndex();
                edits.add(new Edit(declEnd + 1, declEnd, "\n" + floatTypeName(a.declVecSize()) + " " + name + " = " + conversion(a) + ";"));
            }
        }, root);

        for (int i = 0; i < found.length; i++) {
            if (!found[i]) return null;
        }

        return applyEdits(source, edits);
    }

    private static String conversion(Attrib a) {
        final int decl = a.declVecSize();
        final int bound = a.boundVecSize();
        final String src = a.name() + SUFFIX;
        if (bound >= decl) {
            return floatTypeName(decl) + "(" + src + swizzle(decl, bound) + ")";
        }
        final StringBuilder sb = new StringBuilder(floatTypeName(decl)).append('(').append(floatTypeName(bound)).append('(').append(src).append(')');
        for (int i = bound; i < decl; i++) {
            sb.append(i == 3 ? ", 1.0" : ", 0.0");
        }
        return sb.append(')').toString();
    }

    private static String intTypeName(int size, boolean signed) {
        if (size <= 1) return signed ? "int" : "uint";
        return (signed ? "ivec" : "uvec") + size;
    }

    private static String floatTypeName(int size) {
        return size <= 1 ? "float" : "vec" + size;
    }

    private static String swizzle(int declSize, int boundSize) {
        if (boundSize <= declSize) return "";
        return switch (declSize) {
            case 1 -> ".x";
            case 2 -> ".xy";
            case 3 -> ".xyz";
            default -> "";
        };
    }
}
