package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;

import static net.coderbot.iris.pipeline.transform.PatchShaderType.FRAGMENT;
import static net.coderbot.iris.pipeline.transform.PatchShaderType.VERTEX;

class CeleritasTransformer {
    public static void transform(Transformer transformer, Parameters parameters) {
        switch (parameters.type) {
            // For Celeritas patching, treat fragment and geometry the same
            case FRAGMENT:
            case GEOMETRY:
                transformFragment(transformer, parameters);
                break;
            case VERTEX:
                transformVertex(transformer, parameters);
                break;
            default:
                throw new IllegalStateException("Unexpected Celeritas terrain patching shader type: " + parameters.type);
        }
    }

    public static void transformVertex(Transformer transformer, Parameters parameters) {
        transformer.injectVariable("uniform vec3 u_RegionOffset;"); // Per-region offset from setRegionOffset()
        transformer.injectVariable("vec4 iris_LightTexCoord;");
        transformer.injectFunction("vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * _celeritas_getVertexPosition(); }");
        transformer.injectFunction(
            "vec4 _celeritas_getVertexPosition() { " +
            "return vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0); }");
        transformer.injectFunction(
            "void _celeritas_init() { " +
            "_vert_init(); " +
            "iris_LightTexCoord = vec4(vec2(_vert_tex_light_coord), 0.0, 1.0); }");
        transformer.prependMain("_celeritas_init();");
        transformShared(transformer, parameters);

        final Map<String, String> vertexReplacements = new HashMap<>();
        vertexReplacements.put("gl_Vertex", "_celeritas_getVertexPosition()");
        vertexReplacements.put("gl_MultiTexCoord0", "vec4(_vert_tex_diffuse_coord, 0.0, 1.0)");
        vertexReplacements.put("gl_MultiTexCoord1", "iris_LightTexCoord");
        vertexReplacements.put("gl_MultiTexCoord2", "iris_LightTexCoord");
        vertexReplacements.forEach(transformer::replaceExpression);

        final Map<String, String> vertexRenames = new HashMap<>();
        vertexRenames.put("gl_Color", "_vert_color");
        vertexRenames.put("ftransform", "iris_ftransform");
        vertexRenames.put("gl_Normal", "iris_Normal");
        vertexRenames.put("chunkOffset", "u_RegionOffset");
        transformer.rename(vertexRenames);

        transformer.injectVariable("in vec3 iris_Normal;");
    }

    public static void transformFragment(Transformer transformer, Parameters parameters) {
        // interestingly there is nothing that isn't shared
        transformShared(transformer, parameters);
    }

    private static void transformShared(Transformer transformer, Parameters parameters) {
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

        final Map<String, String> sharedReplacements = new HashMap<>();
        sharedReplacements.put("gl_TextureMatrix[0]", "mat4(1.0)");
        sharedReplacements.put("gl_TextureMatrix[1]", "iris_LightmapTextureMatrix");
        sharedReplacements.put("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        sharedReplacements.forEach(transformer::replaceExpression);

        transformFog(transformer, parameters);
    }

    private static void transformFog(Transformer transformer, Parameters parameters) {
        transformer.injectVariable("uniform float iris_FogDensity;");
        transformer.injectVariable("uniform float iris_FogStart;");
        transformer.injectVariable("uniform float iris_FogEnd;");
        transformer.injectVariable("uniform vec4 iris_FogColor;");

        transformer.injectFunction("struct iris_FogParameters { vec4 color; float density; float start; float end; float scale; };");
        transformer.injectFunction(
            "iris_FogParameters iris_Fog = iris_FogParameters(iris_FogColor, iris_FogDensity, " +
            "iris_FogStart, iris_FogEnd, 1.0 / (iris_FogEnd - iris_FogStart));");

        final Map<String, String> fogRenames = new HashMap<>();
        fogRenames.put("gl_Fog", "iris_Fog");
        fogRenames.put("gl_FogFragCoord", "iris_FogFragCoord");
        transformer.rename(fogRenames);

        if (parameters.type == VERTEX) {
            transformer.injectVariable("out float iris_FogFragCoord;");
            transformer.prependMain("iris_FogFragCoord = 0.0;");
        } else if (parameters.type == FRAGMENT) {
            transformer.injectVariable("in float iris_FogFragCoord;");
        }
    }
}
