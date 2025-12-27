package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;

class SodiumTransformer {
    public static void transform(Transformer transformer, Parameters parameters) {
        switch (parameters.type) {
            // For Sodium patching, treat fragment and geometry the same
            case FRAGMENT:
            case GEOMETRY:
                transformFragment(transformer, parameters);
                break;
            case VERTEX:
                transformVertex(transformer, parameters);
                break;
            default:
                throw new IllegalStateException("Unexpected Sodium terrain patching shader type: " + parameters.type);
        }
    }

    /**
     * Transforms vertex shaders.
     */
    public static void transformVertex(Transformer transformer, Parameters parameters) {
        transformer.injectVariable("attribute vec3 iris_Pos;");
        transformer.injectVariable("attribute vec4 iris_Color;");
        transformer.injectVariable("attribute vec2 iris_TexCoord;");
        transformer.injectVariable("attribute vec2 iris_LightCoord;");
        transformer.injectVariable("attribute vec3 iris_Normal;");
        transformer.injectVariable("uniform vec3 u_ModelScale;");
        transformer.injectVariable("uniform vec2 u_TextureScale;");
        transformer.injectVariable("attribute vec4 iris_ModelOffset;");

        transformer.injectFunction("vec4 iris_LightTexCoord = vec4(iris_LightCoord, 0, 1);");
        transformer.injectFunction("vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");

        transformShared(transformer, parameters);

        Map<String, String> vertexReplacements = new HashMap<>();
        vertexReplacements.put("gl_Vertex", "vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0)");
        vertexReplacements.put("gl_MultiTexCoord0", "vec4(iris_TexCoord * u_TextureScale, 0.0, 1.0)");
        vertexReplacements.put("gl_MultiTexCoord1", "iris_LightTexCoord");
        vertexReplacements.put("gl_MultiTexCoord2", "iris_LightTexCoord");
        vertexReplacements.forEach(transformer::replaceExpression);

        Map<String, String> vertexRenames = new HashMap<>();
        vertexRenames.put("gl_Color", "iris_Color");
        vertexRenames.put("gl_Normal", "iris_Normal");
        vertexRenames.put("ftransform", "iris_ftransform");
        transformer.rename(vertexRenames);
    }

    /**
     * Transforms fragment shaders. The fragment shader does only the shared things
     * from the vertex shader.
     */
    public static void transformFragment(Transformer transformer, Parameters parameters) {
        // interestingly there is nothing that isn't shared
        transformShared(transformer, parameters);
    }

    /**
     * Does the things that transformVertex and transformFragment have in common.
     */
    private static void transformShared(Transformer transformer, Parameters parameters) {
        transformer.injectVariable("uniform mat4 iris_ModelViewMatrix;");
        transformer.injectVariable("uniform mat4 u_ModelViewProjectionMatrix;");
        transformer.injectVariable("uniform mat4 iris_NormalMatrix;");
        transformer.injectVariable("uniform mat4 iris_LightmapTextureMatrix;");

        Map<String, String> renames = new HashMap<>();
        renames.put("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        renames.put("gl_ModelViewProjectionMatrix", "u_ModelViewProjectionMatrix");
        transformer.rename(renames);

        Map<String, String> sharedReplacements = new HashMap<>();
        sharedReplacements.put("gl_NormalMatrix", "mat3(iris_NormalMatrix)");
        sharedReplacements.put("gl_TextureMatrix[0]", "mat4(1.0)");
        sharedReplacements.put("gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
        sharedReplacements.forEach(transformer::replaceExpression);
    }
}
