package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GlTestSupport;
import com.gtnewhorizons.angelica.glsm.ffp.CubeInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlAttribBindingTest {

    private static final String INSTANCED_VERTEX = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        layout(location = %d) in vec4 iris_InstEntity;
        layout(location = %d) in vec4 iris_CubeTex;
        void main() { gl_Position = vec4(a_Position, 1.0) + iris_InstEntity + iris_CubeTex; }
        """.formatted(InstancedAttribs.LOC_ENTITY, CubeInstancedAttribs.LOC_CUBE_TEX);

    private static final String IMMEDIATE_VERTEX = """
        #version 330 core
        layout(location = 0) in vec3 a_Position;
        layout(location = %d) in vec4 mc_Entity;
        layout(location = %d) in vec4 iris_Entity;
        void main() { gl_Position = vec4(a_Position, 1.0) + mc_Entity + iris_Entity; }
        """.formatted(ImmediateExtendedAttribHandler.LOC_MC_ENTITY, ImmediateExtendedAttribHandler.LOC_ENTITY_IDS);

    @Test
    void instancedEntityIdsStayAtTheInstanceSlot() {
        final int program = linkWithIrisBindings(INSTANCED_VERTEX);
        try {
            assertEquals(InstancedAttribs.LOC_ENTITY, GLStateManager.glGetAttribLocation(program, "iris_InstEntity"));
            assertEquals(CubeInstancedAttribs.LOC_CUBE_TEX, GLStateManager.glGetAttribLocation(program, "iris_CubeTex"));
        } finally {
            GLStateManager.glDeleteProgram(program);
        }
    }

    @Test
    void immediateEntityIdsDoNotMoveOntoMcEntity() {
        final int program = linkWithIrisBindings(IMMEDIATE_VERTEX);
        try {
            assertEquals(ImmediateExtendedAttribHandler.LOC_MC_ENTITY, GLStateManager.glGetAttribLocation(program, "mc_Entity"));
            assertEquals(ImmediateExtendedAttribHandler.LOC_ENTITY_IDS, GLStateManager.glGetAttribLocation(program, "iris_Entity"));
        } finally {
            GLStateManager.glDeleteProgram(program);
        }
    }

    private static int linkWithIrisBindings(String vertexSource) {
        final int program = GLStateManager.glCreateProgram();
        GLStateManager.glAttachShader(program, GlTestSupport.compile(GL20.GL_VERTEX_SHADER, vertexSource));
        GLStateManager.glAttachShader(program, GlTestSupport.compile(GL20.GL_FRAGMENT_SHADER, GlTestSupport.TRIVIAL_FS));
        GLStateManager.glBindAttribLocation(program, InstancedAttribs.LOC_ENTITY, "iris_InstEntity");
        GLStateManager.glBindAttribLocation(program, ImmediateExtendedAttribHandler.LOC_MC_ENTITY, "mc_Entity");
        GLStateManager.glLinkProgram(program);
        return program;
    }
}
