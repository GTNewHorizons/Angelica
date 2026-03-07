package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

import java.util.HashMap;
import java.util.Map;

class CeleritasTransformer {
    public static void transform(Transformer transformer, Parameters parameters, int glslVersion) {
        // Always core profile — minimum GLSL version is 330 (see ShaderTransformer.getStageMinimumVersion)
        CommonTransformer.transform(transformer, parameters, true, glslVersion);

        switch (parameters.type) {
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
        transformer.injectVariable("uniform vec3 u_RegionOffset;");
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
        transformShared(transformer, parameters);
    }

    private static void transformShared(Transformer transformer, Parameters parameters) {
        CoreTransformHelper.injectMatrixUniforms(transformer);
    }
}
