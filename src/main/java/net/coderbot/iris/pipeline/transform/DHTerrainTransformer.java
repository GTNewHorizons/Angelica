package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.gl.shader.ShaderType;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

public class DHTerrainTransformer {
    public static void transform(Transformer transformer, Parameters parameters, int glslVersion) {
        CommonTransformer.transform(transformer, parameters, true, glslVersion);

        transformer.replaceExpression("gl_TextureMatrix[0]", "mat4(1.0)");
        transformer.replaceExpression("gl_TextureMatrix[1]", "mat4(1.0)");
        transformer.rename("gl_ProjectionMatrix", "iris_ProjectionMatrix");

        if (parameters.type == ShaderType.VERTEX) {
            // Alias of gl_MultiTexCoord1 on 1.15+ for OptiFine
            // See https://github.com/IrisShaders/Iris/issues/1149
            transformer.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");

            transformer.replaceExpression("gl_MultiTexCoord0", "vec4(0.0, 0.0, 0.0, 1.0)");
            transformer.replaceExpression("gl_MultiTexCoord1", "vec4(_vert_tex_light_coord, 0.0, 1.0)");

            replaceGlMultiTexCoordBounded(transformer, 4, 7);
        }

        transformer.rename("gl_Color", "_vert_color");

        if (parameters.type == ShaderType.VERTEX) {
            transformer.replaceExpression("gl_Normal", "_vert_normal");
        }

        transformer.replaceExpression("gl_NormalMatrix", "iris_NormalMatrix");
        addIfNotExists(transformer, "iris_NormalMatrix", "uniform mat3 iris_NormalMatrix;");
        addIfNotExists(transformer, "iris_ModelViewMatrixInverse", "uniform mat4 iris_ModelViewMatrixInverse;");
        addIfNotExists(transformer, "iris_ProjectionMatrixInverse", "uniform mat4 iris_ProjectionMatrixInverse;");

        transformer.rename("gl_ModelViewMatrix", "iris_ModelViewMatrix");
        transformer.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatrixInverse");
        transformer.rename("gl_ProjectionMatrixInverse", "iris_ProjectionMatrixInverse");

        if (parameters.type == ShaderType.VERTEX) {
            if (transformer.containsCall("ftransform")) {
                transformer.injectFunction("vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
            }

            addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
            addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            transformer.injectFunction("vec4 getVertexPosition() { return vec4(modelOffset + _vert_position, 1.0); }");
            transformer.replaceExpression("gl_Vertex", "getVertexPosition()");

            injectVertInit(transformer);
        } else {
            addIfNotExists(transformer, "iris_ModelViewMatrix", "uniform mat4 iris_ModelViewMatrix;");
            addIfNotExists(transformer, "iris_ProjectionMatrix", "uniform mat4 iris_ProjectionMatrix;");
        }

        transformer.replaceExpression("gl_ModelViewProjectionMatrix", "(iris_ProjectionMatrix * iris_ModelViewMatrix)");
        ShaderTransformer.applyIntelHd4000Workaround(transformer);
    }

    public static void injectVertInit(Transformer transformer) {
        addIfNotExists(transformer, "_vert_position", "vec3 _vert_position;");
        addIfNotExists(transformer, "_vert_tex_light_coord", "vec2 _vert_tex_light_coord;");
        addIfNotExists(transformer, "dhMaterialId", "int dhMaterialId;");
        addIfNotExists(transformer, "_vert_color", "vec4 _vert_color;");
        addIfNotExists(transformer, "_vert_normal", "vec3 _vert_normal;");
        addIfNotExists(transformer, "mircoOffset", "uniform float mircoOffset;");
        addIfNotExists(transformer, "modelOffset", "uniform vec3 modelOffset;");
        addIfNotExists(transformer, "iris_color", "in vec4 iris_color;");
        addIfNotExists(transformer, "vPosition", "in uvec4 vPosition;");
        addIfNotExists(transformer, "irisExtra", "in uvec4 irisExtra;");

        transformer.injectFunction("const vec3 irisNormals[6] = vec3[](vec3(0,-1,0), vec3(0,1,0), vec3(0,0,-1), vec3(0,0,1), vec3(-1,0,0), vec3(1,0,0));");
        transformer.injectFunction(
            "void _vert_init() {"
                + " uint meta = vPosition.a;"
                + " uint mirco = (meta & 0xFF00u) >> 8u;"
                + " float mx = (mirco & 1u) != 0u ? mircoOffset : 0.0;"
                + " mx = (mirco & 2u) != 0u ? -mx : mx;"
                + " float my = (mirco & 4u) != 0u ? mircoOffset : 0.0;"
                + " my = (mirco & 8u) != 0u ? -my : my;"
                + " float mz = (mirco & 16u) != 0u ? mircoOffset : 0.0;"
                + " mz = (mirco & 32u) != 0u ? -mz : mz;"
                + " uint lights = meta & 0xFFu;"
                + " _vert_position = (vPosition.xyz + vec3(mx, my, mz));"
                + " _vert_normal = irisNormals[int(irisExtra.y)];"
                + " dhMaterialId = int(irisExtra.x);"
                + " _vert_tex_light_coord = vec2((float(lights / 16u) + 0.5) / 16.0, (mod(float(lights), 16.0) + 0.5) / 16.0);"
                + " _vert_color = iris_color;"
                + " }"
        );
        transformer.prependMain("_vert_init();");
    }

    private static void addIfNotExists(Transformer transformer, String name, String code) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(code);
        }
    }

    private static void replaceGlMultiTexCoordBounded(Transformer transformer, int from, int to) {
        for (int i = from; i <= to; i++) {
            transformer.replaceExpression("gl_MultiTexCoord" + i, "vec4(0.0, 0.0, 0.0, 1.0)");
        }
    }
}
