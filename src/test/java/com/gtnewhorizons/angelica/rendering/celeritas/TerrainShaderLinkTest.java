package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderParser;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderBindingPoints;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class TerrainShaderLinkTest {

    private static final String VSH = "sodium:blocks/block_layer_opaque.vsh";
    private static final String FSH = "angelica:blocks/block_layer_opaque.fsh";

    private static final int MIPPED_LEVELS = 4;

    enum Filter {
        PLAIN(false, false, false),
        TEXEL_SNAP(true, false, false),
        RGSS(true, true, false),
        ANISOTROPIC(true, false, true),
        RGSS_ANISOTROPIC(true, true, true);

        final boolean texelSnap, rgss, anisotropic;

        Filter(boolean texelSnap, boolean rgss, boolean anisotropic) {
            this.texelSnap = texelSnap;
            this.rgss = rgss;
            this.anisotropic = anisotropic;
        }
    }

    private static final Map<String, ChunkVertexType> VERTEX_TYPES = new LinkedHashMap<>();
    static {
        VERTEX_TYPES.put("VANILLA_LIKE", ChunkMeshFormats.VANILLA_LIKE);
        VERTEX_TYPES.put("COMPACT", ChunkMeshFormats.COMPACT);
    }

    static Stream<Arguments> variants() {
        final List<Arguments> out = new ArrayList<>();
        for (ChunkFogMode fog : ChunkFogMode.values()) {
            for (String vertex : VERTEX_TYPES.keySet()) {
                for (Filter filter : Filter.values()) {
                    for (boolean noMips : new boolean[] {false, true}) {
                        out.add(Arguments.of(fog, vertex, filter, noMips));
                    }
                }
            }
        }
        return out.stream();
    }

    private static ShaderConstants constants(ChunkFogMode fog, ChunkVertexType vertexType, Filter filter, boolean noMips) {
        final ShaderConstants.Builder b = ShaderConstants.builder();
        b.addAll(fog.getDefines());
        b.add("USE_FRAGMENT_DISCARD");
        b.addAll(AngelicaRenderPassConfiguration.samplerDefines(filter.rgss, filter.texelSnap, filter.anisotropic, noMips ? 0 : MIPPED_LEVELS));
        vertexType.getDefines().forEach(b::add);
        b.addAll(QuadPrimitiveType.TRIANGULATED.getDefines());
        return b.build();
    }

    private static int compile(int type, String name, ShaderConstants constants) {
        final String src = ShaderParser.parseShader(ShaderLoader.getShaderSource(name), ShaderLoader::getShaderSource, constants);
        final int shader = GLStateManager.glCreateShader(type);
        GLStateManager.glShaderSource(shader, src);
        GLStateManager.glCompileShader(shader);
        if (GLStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            final String log = GLStateManager.glGetShaderInfoLog(shader, 8192);
            GLStateManager.glDeleteShader(shader);
            throw new AssertionError(name + " failed to compile with " + constants.getDefineStrings() + ":\n" + log);
        }
        return shader;
    }

    @ParameterizedTest(name = "fog={0} vertex={1} filter={2} noMips={3}")
    @MethodSource("variants")
    void links(ChunkFogMode fog, String vertex, Filter filter, boolean noMips) {
        final ChunkVertexType vertexType = VERTEX_TYPES.get(vertex);
        final ShaderConstants constants = constants(fog, vertexType, filter, noMips);

        final int vsh = compile(GL20.GL_VERTEX_SHADER, VSH, constants);
        int fsh = 0;
        int program = 0;
        try {
            fsh = compile(GL20.GL_FRAGMENT_SHADER, FSH, constants);
            program = GLStateManager.glCreateProgram();
            GLStateManager.glAttachShader(program, vsh);
            GLStateManager.glAttachShader(program, fsh);
            int i = 0;
            for (GlVertexAttribute attr : vertexType.getVertexFormat().getAttributes()) {
                GLStateManager.glBindAttribLocation(program, i++, attr.getName());
            }
            GLStateManager.glBindFragDataLocation(program, ChunkShaderBindingPoints.FRAG_COLOR, "fragColor");
            GLStateManager.glLinkProgram(program);
            final int linked = program;
            assertEquals(GL11.GL_TRUE, GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS),
                () -> "terrain program failed to link with " + constants.getDefineStrings() + ":\n" + GLStateManager.glGetProgramInfoLog(linked, 8192));
        } finally {
            if (program != 0) GLStateManager.glDeleteProgram(program);
            if (fsh != 0) GLStateManager.glDeleteShader(fsh);
            GLStateManager.glDeleteShader(vsh);
        }
    }
}
