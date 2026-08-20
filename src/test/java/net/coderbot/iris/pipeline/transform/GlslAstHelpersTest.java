package net.coderbot.iris.pipeline.transform;

import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.grammar.GLSLParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlslAstHelpersTest {

    @Test void zeroLiteralFor_basicTypes() {
        assertEquals("vec4(0.0)", GlslAstHelpers.zeroLiteralFor("image2D"));
        assertEquals("ivec4(0)", GlslAstHelpers.zeroLiteralFor("iimage2D"));
        assertEquals("uvec4(0u)", GlslAstHelpers.zeroLiteralFor("uimage3D"));
        assertEquals("vec4(0.0)", GlslAstHelpers.zeroLiteralFor("image1D"));
        assertEquals("vec4(0.0)", GlslAstHelpers.zeroLiteralFor(null));
    }

    @Test void componentTypeFor_basicTypes() {
        assertEquals("vec4", GlslAstHelpers.componentTypeFor("image3D"));
        assertEquals("ivec4", GlslAstHelpers.componentTypeFor("iimage1D"));
        assertEquals("uvec4", GlslAstHelpers.componentTypeFor("uimage2D"));
    }

    @Test void firstArgIdentifier_returnsCalleeFirstArg() {
        final var snippet = ShaderParser.parseSnippet("imageStore(target_img, ivec2(0), uvec4(0u))",
            GLSLParser::postfix_expression);
        assertEquals("target_img", GlslAstHelpers.firstArgIdentifier(snippet));
    }

    @Test void deleteStatementContaining_unlinksStmt() {
        final String src = "#version 460 core\nvoid main(){ int x = 1; imageStore(t, 0, 0); int y = 2; }";
        final var parsed = ShaderParser.parseShader(src);
        final var calls = GlslAstHelpers.collectAll(parsed.full(), GLSLParser.Postfix_expressionContext.class);
        GLSLParser.Postfix_expressionContext target = null;
        for (var c : calls) {
            if ("imageStore".equals(GlslAstHelpers.extractCallName(c))) { target = c; break; }
        }
        assertTrue(target != null, "expected to find imageStore call in synthetic source");
        final boolean removed = GlslAstHelpers.deleteStatementContaining(target);
        assertTrue(removed);
        final var remaining = GlslAstHelpers.collectAll(parsed.full(), GLSLParser.Postfix_expressionContext.class);
        for (var c : remaining) {
            assertFalse("imageStore".equals(GlslAstHelpers.extractCallName(c)), "imageStore call should no longer be reachable in the tree");
        }
    }
}
