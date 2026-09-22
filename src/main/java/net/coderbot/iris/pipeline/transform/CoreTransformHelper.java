package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedGlslHelpers;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared core-profile transformation logic for matrix uniforms, texture matrices, and vertex attribute replacement.
 */
class CoreTransformHelper {

    /**
     * Inject matrix uniforms and rename FFP matrix builtins to iris_* equivalents.
     * Handles: ModelView, ModelViewInverse, Projection, ProjectionInverse, NormalMatrix, ModelViewProjectionMatrix, TextureMatrix[0..1], and LightmapTextureMatrix.
     */
    static void injectMatrixUniforms(Transformer transformer) {
        injectMatrixUniforms(transformer, Instancing.NONE);
    }

    static void injectMatrixUniforms(Transformer transformer, Instancing instancing) {
        switch (instancing) {
            case PARTICLE -> {
                transformer.injectVariable("layout(location = " + VertexFormatElement.Usage.POSITION.getAttributeLocation() + ") in vec3 iris_ParticleOffset;");
                transformer.injectVariable("layout(location = " + VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation() + ") in vec2 iris_ParticleCorner;");
                injectAttributeDecls(transformer, instancing);
                transformer.injectVariable("vec4 iris_Vertex;");
                transformer.injectVariable("vec4 iris_Color;");
                transformer.injectVariable("vec4 iris_MultiTexCoord0;");
                transformer.injectVariable("vec4 iris_MultiTexCoord1;");
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
                transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
            }
            case WEATHER -> {
                transformer.injectVariable("layout(location = " + VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation() + ") in vec2 iris_WeatherCorner;");
                injectAttributeDecls(transformer, instancing);
                transformer.injectVariable("uniform vec4 iris_WeatherParams0;");
                transformer.injectVariable("uniform vec4 iris_WeatherParams1;");
                transformer.injectVariable("uniform vec4 iris_WeatherParams2;");
                transformer.injectVariable("vec4 iris_Vertex;");
                transformer.injectVariable("vec4 iris_Color;");
                transformer.injectVariable("vec4 iris_MultiTexCoord0;");
                transformer.injectVariable("vec4 iris_MultiTexCoord1;");
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
                transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
            }
            case TEMPLATE, CUBE -> {
                injectAttributeDecls(transformer, instancing);
                transformer.injectVariable("mat4 iris_ModelViewMatrix;");
                transformer.injectVariable("mat4 iris_ModelViewMatrixInverse;");
                transformer.injectVariable("mat3 iris_NormalMatrix;");
                if (!declaresInvariantPosition(transformer)) {
                    transformer.injectVariable("invariant gl_Position;");
                }
            }
            case NONE -> {
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
                transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
                transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
            }
        }
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrix;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrixInverse;");
        transformer.injectVariable("uniform mat4 iris_LightmapTextureMatrix;");
        transformer.injectVariable("uniform mat4 iris_TextureMatrix;");

        final Map<String, String> renames = new HashMap<>();
        renames.put("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        renames.put("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        renames.put("gl_ProjectionMatrix", "iris_ProjectionMatrix");
        renames.put("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");
        renames.put("gl_NormalMatrix", "iris_NormalMatrix");
        transformer.rename(renames);

        final Map<String, String> replacements = new HashMap<>();
        replacements.put("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        replacements.put("gl_TextureMatrix[0]", "iris_TextureMatrix");
        replacements.put("gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
        replacements.forEach(transformer::replaceExpression);

        // Catch any remaining gl_TextureMatrix references (e.g. [2]-[7])
        transformer.replaceExpression("gl_TextureMatrix", "mat4[8](iris_TextureMatrix, iris_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))");
    }

    private static void injectAttributeDecls(Transformer transformer, Instancing instancing) {
        for (String decl : InstancedGlslHelpers.attributeDecls("iris_", instancing)) {
            transformer.injectVariable(decl);
        }
    }

    private static boolean declaresInvariantPosition(Transformer transformer) {
        final boolean[] found = new boolean[1];
        transformer.mutateTree(root -> {
            for (int i = 0; i < root.getChildCount(); i++) {
                if ("invariantgl_Position;".equals(root.getChild(i).getText())) {
                    found[0] = true;
                    return;
                }
            }
        });
        return found[0];
    }

    /**
     * Inject vertex attributes and ftransform() replacement for composite/depth shaders. Uses locations matching FullScreenQuadRenderer's POSITION_TEXTURE VAO layout.
     */
    static void injectCompositeVertexAttributes(Transformer transformer) {
        transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
        transformer.injectVariable("layout(location = 2) in vec4 iris_MultiTexCoord0;");

        transformer.rename("gl_Vertex", "iris_Vertex");
        transformer.rename("gl_MultiTexCoord0", "iris_MultiTexCoord0");

        for (int i = 1; i <= 7; i++) {
            transformer.replaceExpression("gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
        }

        transformer.replaceExpression("gl_Color", "vec4(1.0, 1.0, 1.0, 1.0)");
        transformer.replaceExpression("gl_Normal", "vec3(0.0, 0.0, 1.0)");

        transformer.renameFunctionCall("ftransform", "iris_ftransform");
        transformer.injectFunction("vec4 iris_ftransform() { return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex; }");

        ShaderTransformer.applyIntelHd4000Workaround(transformer);
    }
}
