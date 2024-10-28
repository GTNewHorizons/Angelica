package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Util;
import org.taumc.glsl.grammar.GLSLParser;

class SodiumTransformer {
    public static void transform(GLSLParser.Translation_unitContext translationUnit, Parameters parameters) {
        switch (parameters.type) {
            // For Sodium patching, treat fragment and geometry the same
            case FRAGMENT:
            case GEOMETRY:
                transformFragment(translationUnit, parameters);
                break;
            case VERTEX:
                transformVertex(translationUnit, parameters);
                break;
            default:
                throw new IllegalStateException("Unexpected Sodium terrain patching shader type: " + parameters.type);
        }
    }

    /**
     * Transforms vertex shaders.
     */
    public static void transformVertex(GLSLParser.Translation_unitContext translationUnit, Parameters parameters) {

        Util.injectVariable(translationUnit, "attribute vec3 iris_Pos;");
        Util.injectVariable(translationUnit, "attribute vec4 iris_Color;");
        Util.injectVariable(translationUnit, "attribute vec2 iris_TexCoord;");
        Util.injectVariable(translationUnit, "attribute vec2 iris_LightCoord;");
        Util.injectVariable(translationUnit, "attribute vec3 iris_Normal;");
        Util.injectVariable(translationUnit, "uniform vec3 u_ModelScale;");
        Util.injectVariable(translationUnit, "uniform vec2 u_TextureScale;");
        Util.injectVariable(translationUnit, "attribute vec4 iris_ModelOffset;");
        Util.injectFunction(translationUnit, "vec4 iris_LightTexCoord = vec4(iris_LightCoord, 0, 1);");
        Util.injectFunction(translationUnit, "vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");

        transformShared(translationUnit, parameters);

        Util.replaceExpression(translationUnit, "gl_Vertex", "vec4((iris_Pos * u_ModelScale) + iris_ModelOffset.xyz, 1.0)");
        Util.replaceExpression(translationUnit, "gl_MultiTexCoord0", "vec4(iris_TexCoord * u_TextureScale, 0.0, 1.0)");
        Util.replaceExpression(translationUnit, "gl_MultiTexCoord1", "iris_LightTexCoord");
        Util.replaceExpression(translationUnit, "gl_MultiTexCoord2", "iris_LightTexCoord");
        Util.rename(translationUnit, "gl_Color", "iris_Color");
        Util.rename(translationUnit, "gl_Normal", "iris_Normal");
        Util.rename(translationUnit, "ftransform", "iris_ftransform");
    }

    /**
     * Transforms fragment shaders. The fragment shader does only the shared things
     * from the vertex shader.
     */
    public static void transformFragment(GLSLParser.Translation_unitContext translationUnit, Parameters parameters) {
        // interestingly there is nothing that isn't shared
        transformShared(translationUnit, parameters);
    }

    /**
     * Does the things that transformVertex and transformFragment have in common.
     */
    private static void transformShared(GLSLParser.Translation_unitContext translationUnit, Parameters parameters) {
        Util.injectVariable(translationUnit, "uniform mat4 iris_ModelViewMatrix;");
        Util.injectVariable(translationUnit, "uniform mat4 u_ModelViewProjectionMatrix;");
        Util.injectVariable(translationUnit, "uniform mat4 iris_NormalMatrix;");
        Util.injectVariable(translationUnit, "uniform mat4 iris_LightmapTextureMatrix;");

        Util.rename(translationUnit, "gl_ModelViewMatrix", "iris_ModelViewMatrix");
        Util.rename(translationUnit, "gl_ModelViewProjectionMatrix", "u_ModelViewProjectionMatrix");
        Util.replaceExpression(translationUnit, "gl_NormalMatrix", "mat3(iris_NormalMatrix)");

        Util.replaceExpression(translationUnit, "gl_TextureMatrix[0]", "mat4(1.0)");
        Util.replaceExpression(translationUnit, "gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
    }
}
