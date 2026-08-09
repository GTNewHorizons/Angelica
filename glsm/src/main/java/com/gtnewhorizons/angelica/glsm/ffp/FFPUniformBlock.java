package com.gtnewhorizons.angelica.glsm.ffp;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

public final class FFPUniformBlock {

    public static final String BLOCK_NAME = "FFPUniformBlock";
    public static final int BINDING_POINT = 0;

    private static final StringBuilder DECL = new StringBuilder(2048);
    private static int cursor = 0;
    static final Object2IntMap<String> MEMBER_OFFSETS = new Object2IntLinkedOpenHashMap<>();

    private static int align(int alignment) {
        cursor = (cursor + alignment - 1) & -alignment;
        return cursor;
    }

    private static int member(String type, String name, int alignment, int size) {
        DECL.append("  ").append(type).append(' ').append(name).append(";\n");
        final int offset = align(alignment);
        cursor += size;
        MEMBER_OFFSETS.put(name, offset);
        return offset;
    }

    private static int mat4(String name)  { return member("mat4", name, 16, 64); }
    private static int mat3(String name)  { return member("mat3", name, 16, 48); }
    private static int vec4(String name)  { return member("vec4", name, 16, 16); }
    private static int vec3(String name)  { return member("vec3", name, 16, 12); }
    private static int vec2(String name)  { return member("vec2", name, 8, 8); }
    private static int flt(String name)   { return member("float", name, 4, 4); }
    private static int integer(String name) { return member("int", name, 4, 4); }
    private static int vec4Array(String name, int count) {
        DECL.append("  vec4 ").append(name).append('[').append(count).append("];\n");
        final int offset = align(16);
        cursor += 16 * count;
        MEMBER_OFFSETS.put(name + "[0]", offset);
        return offset;
    }

    static { DECL.append("layout(std140) uniform ").append(BLOCK_NAME).append(" {\n"); }

    public static final int MODEL_VIEW_MATRIX = mat4("u_ModelViewMatrix");
    public static final int PROJECTION_MATRIX = mat4("u_ProjectionMatrix");
    public static final int MVP_MATRIX = mat4("u_MVPMatrix");
    public static final int NORMAL_MATRIX = mat3("u_NormalMatrix");
    public static final int TEXTURE_MATRIX_0 = mat4("u_TextureMatrix0");
    public static final int TEXTURE_MATRIX_2 = mat4("u_TextureMatrix2");
    public static final int TEXTURE_MATRIX_3 = mat4("u_TextureMatrix3");
    public static final int LIGHTMAP_TEXTURE_MATRIX = mat4("u_LightmapTextureMatrix");

    public static final int CURRENT_COLOR = vec4("u_CurrentColor");
    public static final int CURRENT_TEX_COORD_0 = vec4("u_CurrentTexCoord0");
    public static final int CURRENT_TEX_COORD_2 = vec4("u_CurrentTexCoord2");
    public static final int CURRENT_TEX_COORD_3 = vec4("u_CurrentTexCoord3");
    public static final int VIEWPORT = vec4("u_Viewport");
    public static final int CURRENT_NORMAL = vec3("u_CurrentNormal");
    public static final int CURRENT_LIGHTMAP_COORD = vec2("u_CurrentLightmapCoord");
    public static final int VIEWPORT_SIZE = vec2("u_ViewportSize");
    public static final int NORMAL_SCALE = flt("u_NormalScale");
    public static final int MATERIAL_SHININESS = flt("u_MaterialShininess");
    public static final int ALPHA_REF = flt("u_AlphaRef");
    public static final int LINE_WIDTH = flt("u_LineWidth");
    public static final int LINE_STIPPLE = integer("u_LineStipple");

    public static final int TEX_GEN_OBJ_PLANE_S = vec4("u_TexGenObjPlaneS");
    public static final int TEX_GEN_OBJ_PLANE_T = vec4("u_TexGenObjPlaneT");
    public static final int TEX_GEN_OBJ_PLANE_R = vec4("u_TexGenObjPlaneR");
    public static final int TEX_GEN_OBJ_PLANE_Q = vec4("u_TexGenObjPlaneQ");
    public static final int TEX_GEN_EYE_PLANE_S = vec4("u_TexGenEyePlaneS");
    public static final int TEX_GEN_EYE_PLANE_T = vec4("u_TexGenEyePlaneT");
    public static final int TEX_GEN_EYE_PLANE_R = vec4("u_TexGenEyePlaneR");
    public static final int TEX_GEN_EYE_PLANE_Q = vec4("u_TexGenEyePlaneQ");

    public static final int CLIP_PLANES = vec4Array("u_ClipPlane", 8);

    public static final int LIGHT_MODEL_AMBIENT = vec4("u_LightModelAmbient");
    public static final int MATERIAL_EMISSION = vec4("u_MaterialEmission");
    public static final int MATERIAL_AMBIENT = vec4("u_MaterialAmbient");
    public static final int MATERIAL_DIFFUSE = vec4("u_MaterialDiffuse");
    public static final int MATERIAL_SPECULAR = vec4("u_MaterialSpecular");
    public static final int LIGHT0_AMBIENT = vec4("u_Light0Ambient");
    public static final int LIGHT0_DIFFUSE = vec4("u_Light0Diffuse");
    public static final int LIGHT0_SPECULAR = vec4("u_Light0Specular");
    public static final int LIGHT0_POSITION = vec4("u_Light0Position");
    public static final int LIGHT1_AMBIENT = vec4("u_Light1Ambient");
    public static final int LIGHT1_DIFFUSE = vec4("u_Light1Diffuse");
    public static final int LIGHT1_SPECULAR = vec4("u_Light1Specular");
    public static final int LIGHT1_POSITION = vec4("u_Light1Position");

    public static final int SCENE_COLOR = vec4("u_SceneColor");
    public static final int LIGHT_PROD0_AMBIENT = vec3("u_LightProd0Ambient");
    public static final int LIGHT_PROD0_DIFFUSE = vec3("u_LightProd0Diffuse");
    public static final int LIGHT_PROD0_SPECULAR = vec3("u_LightProd0Specular");
    public static final int LIGHT_PROD1_AMBIENT = vec3("u_LightProd1Ambient");
    public static final int LIGHT_PROD1_DIFFUSE = vec3("u_LightProd1Diffuse");
    public static final int LIGHT_PROD1_SPECULAR = vec3("u_LightProd1Specular");

    public static final int TEX_ENV_COLOR_0 = vec4("u_TexEnvColor0");
    public static final int TEX_ENV_COLOR_1 = vec4("u_TexEnvColor1");
    public static final int TEX_ENV_COLOR_2 = vec4("u_TexEnvColor2");
    public static final int TEX_ENV_COLOR_3 = vec4("u_TexEnvColor3");
    public static final int OVERLAY_COLOR = vec4("u_OverlayColor");
    public static final int FOG_PARAMS = vec4("u_FogParams");
    public static final int FOG_COLOR = vec4("u_FogColor");

    public static final int SIZE;
    public static final String GLSL_DECL;

    static {
        SIZE = (cursor + 15) & -16;
        DECL.append("};\n");
        GLSL_DECL = DECL.toString();
    }

    private FFPUniformBlock() {}
}
