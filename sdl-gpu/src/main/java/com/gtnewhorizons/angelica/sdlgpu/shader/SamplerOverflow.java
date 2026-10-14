package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SDL_GPU binds 16 samplers per stage; GL drivers give 32. A sampler read only through texelFetch/textureSize never
 * uses its sampler state, so it can move to one of the 8 read-only texture slots. Moved samplers get {@link #PREFIX};
 * link strips it and binds them from their texture unit (through a copy on Vulkan, see {@code MovedSamplerCopies}).
 */
public final class SamplerOverflow {

    private static final Logger LOGGER = LogManager.getLogger("Angelica-SDLGPU");

    public static final int SAMPLER_SLOTS = ContextState.MAX_SAMPLERS;
    public static final int TEXTURE_SLOTS = ContextState.MAX_IMAGE_UNITS;

    public static final String PREFIX = "angelica_moved_sampler_";

    private static final Set<String> SAMPLERLESS_CALLS = Set.of("texelFetch", "texelFetchOffset", "textureSize", "textureQueryLevels");

    private SamplerOverflow() {}

    public static boolean isMoved(String name) {
        return name.startsWith(PREFIX);
    }

    public static String originalName(String name) {
        return isMoved(name) ? name.substring(PREFIX.length()) : name;
    }

    private record SamplerDecl(String name, GLSLParser.Type_specifier_nonarrayContext type, boolean movable) {}

    /**
     * Returns true when it moved a sampler, which then needs {@link GlslVulkanPreprocess#SAMPLERLESS_EXTENSION}.
     * Every declared texture, image and sampler {@link SamplerStripper} kept reaches SPIR-V and takes a slot, used or not.
     */
    static boolean collectEdits(ShaderIndex index, List<TextureDeclarations.Declaration> declarations, Set<String> strippedSamplers, String debugName, List<Edit> edits) {
        final List<SamplerDecl> samplers = new ArrayList<>();
        int textureSlotsTaken = 0;
        for (TextureDeclarations.Declaration decl : declarations) {
            if (!decl.sampler()) {
                textureSlotsTaken += decl.names().size();
                continue;
            }
            final GLSLParser.Type_specifier_nonarrayContext type = decl.type();
            final List<GLSLParser.Typeless_declarationContext> declarators = decl.declarators();
            // Conversion and the missing-texture fallback both require a float 2D view.
            final boolean movable = declarators.size() == 1 && !decl.array() && "sampler2D".equals(type.getText());
            for (GLSLParser.Typeless_declarationContext td : declarators) {
                if (td.IDENTIFIER() == null) continue;
                final String name = td.IDENTIFIER().getText();
                if (strippedSamplers.contains(name)) continue;
                samplers.add(new SamplerDecl(name, type, movable && td.array_specifier() == null));
            }
        }

        final int excess = samplers.size() - SAMPLER_SLOTS;
        final int freeTextureSlots = TEXTURE_SLOTS - textureSlotsTaken;
        if (excess <= 0 || freeTextureSlots <= 0) return false;

        final int toMove = Math.min(excess, freeTextureSlots);
        final ShaderIndex.Directives directives = index.directives();
        final IntSet samplerlessArguments = samplerlessArgumentStarts(index);
        final List<String> moved = new ArrayList<>();
        for (SamplerDecl decl : samplers) {
            if (moved.size() == toMove) break;
            if (!decl.movable() || namedInDirectives(directives, decl.name()) || GlslVulkanPreprocess.renamesIdentifier(decl.name())
                || !onlyReadWithoutSampler(index, decl.name(), samplerlessArguments)) continue;
            edits.add(new Edit(GlslVulkanPreprocess.startIdx(decl.type()), GlslVulkanPreprocess.stopIdx(decl.type()), "texture2D"));
            for (Token tok : index.occurrences(decl.name())) {
                edits.add(new Edit(tok.getStartIndex(), tok.getStartIndex() - 1, PREFIX));
            }
            moved.add(decl.name());
        }
        if (moved.isEmpty()) return false;
        LOGGER.debug("'{}' uses {} samplers; moved {} that are only read with texelFetch/textureSize to texture slots: {}", debugName, samplers.size(), moved.size(), moved);
        return true;
    }

    private static boolean onlyReadWithoutSampler(ShaderIndex index, String name, IntSet samplerlessArguments) {
        int otherOccurrences = 0;
        for (Token tok : index.occurrences(name)) {
            if (samplerlessArguments.contains(tok.getStartIndex())) continue;
            if (++otherOccurrences > 1) return false;
        }
        return true;
    }

    private static IntSet samplerlessArgumentStarts(ShaderIndex index) {
        final IntSet starts = new IntOpenHashSet();
        final Set<String> overloaded = userFunctionNames(index);
        final ShaderIndex.Directives directives = index.directives();
        for (String fn : SAMPLERLESS_CALLS) {
            if (overloaded.contains(fn) || namedInDirectives(directives, fn)) continue;
            for (GLSLParser.Postfix_expressionContext call : index.callsTo(fn)) {
                final GLSLParser.Function_call_parametersContext params = call.function_call_parameters();
                if (params == null || params.assignment_expression().isEmpty()) continue;
                final GLSLParser.Assignment_expressionContext first = params.assignment_expression(0);
                if (first.getStart() == first.getStop()) starts.add(first.getStart().getStartIndex());
            }
        }
        return starts;
    }

    private static boolean namedInDirectives(ShaderIndex.Directives directives, String name) {
        return directives.identifiers().contains(name) || directives.definedMacros().contains(name);
    }

    private static Set<String> userFunctionNames(ShaderIndex index) {
        final Set<String> names = new HashSet<>();
        for (GLSLParser.External_declarationContext external : index.root().external_declaration()) {
            final GLSLParser.Function_definitionContext function = external.function_definition();
            final GLSLParser.DeclarationContext declaration = function == null ? external.declaration() : null;
            final GLSLParser.Function_prototypeContext prototype = function != null ? function.function_prototype() : declaration != null ? declaration.function_prototype() : null;
            if (prototype != null && prototype.IDENTIFIER() != null) names.add(prototype.IDENTIFIER().getText());
        }
        return names;
    }
}
