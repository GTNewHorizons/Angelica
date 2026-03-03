package net.coderbot.iris.pipeline.transform;

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
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrixInverse;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrix;");
        transformer.injectVariable("uniform mat4 iris_ProjectionMatrixInverse;");
        transformer.injectVariable("uniform mat3 iris_NormalMatrix;");
        transformer.injectVariable("uniform mat4 iris_LightmapTextureMatrix;");

        final Map<String, String> renames = new HashMap<>();
        renames.put("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        renames.put("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        renames.put("gl_ProjectionMatrix", "iris_ProjectionMatrix");
        renames.put("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");
        renames.put("gl_NormalMatrix", "iris_NormalMatrix");
        transformer.rename(renames);

        final Map<String, String> replacements = new HashMap<>();
        replacements.put("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        replacements.put("gl_TextureMatrix[0]", "mat4(1.0)");
        replacements.put("gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
        replacements.forEach(transformer::replaceExpression);

        // Catch any remaining gl_TextureMatrix references (e.g. [2]-[7])
        transformer.replaceExpression("gl_TextureMatrix", "mat4[8](mat4(1.0), iris_LightmapTextureMatrix, mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0))");
    }

    /**
     * Inject vertex attributes and ftransform() replacement for composite/depth shaders. Uses locations matching FullScreenQuadRenderer's POSITION_TEXTURE VAO layout.
     */
    static void injectCompositeVertexAttributes(Transformer transformer) {
        transformer.injectVariable("layout(location = 0) in vec4 iris_Vertex;");
        transformer.injectVariable("layout(location = 2) in vec4 iris_MultiTexCoord0;");

        transformer.rename("gl_Vertex", "iris_Vertex");
        transformer.rename("gl_MultiTexCoord0", "iris_MultiTexCoord0");

        transformer.renameFunctionCall("ftransform", "iris_ftransform");
        transformer.injectFunction("vec4 iris_ftransform() { return (iris_ProjectionMatrix * iris_ModelViewMatrix) * iris_Vertex; }");

        ShaderTransformer.applyIntelHd4000Workaround(transformer);
    }
}
