package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaderUniforms;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class TesrShaderPrograms {

    private static final Logger LOG = LogManager.getLogger("AngelicaTesrShaders");

    private static final int UNKNOWN = 0;
    private static final int READY = 1;
    private static final int FAILED = 2;

    private static final Reference2ObjectOpenHashMap<TesrShader, Entry> ENTRIES = new Reference2ObjectOpenHashMap<>();

    public static long bakedBinds;
    public static long instancedBinds;
    public static long fallbacks;
    public static long linkFailures;

    private TesrShaderPrograms() {}

    static final class Variant {
        final boolean instanced;
        final String label;
        int state;
        int program;

        Variant(boolean instanced) {
            this.instanced = instanced;
            this.label = instanced ? "instanced" : "baked";
        }
    }

    public static final class Entry {
        final TesrShader shader;
        final Variant baked = new Variant(false);
        final Variant instanced = new Variant(true);
        private boolean programBound;
        private int savedProgram;
        private boolean wantsLightmap;
        private boolean lightmapBound;
        private int savedLightmapBinding;

        Entry(TesrShader shader) {
            this.shader = shader;
        }
    }

    public static boolean packActive() {
        return Iris.enabled && Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline drp && drp.shouldOverrideShaders();
    }

    public static Entry entry(TesrShader shader) {
        Entry entry = ENTRIES.get(shader);
        if (entry == null) {
            entry = new Entry(shader);
            ENTRIES.put(shader, entry);
        }
        return entry;
    }

    static Entry entryFor(TesrMaterial material) {
        final TesrShader shader = material.shader();
        return shader == null ? null : entry(shader);
    }

    public static boolean bindBaked(Entry entry, boolean lightmap) {
        entry.wantsLightmap = lightmap;
        if (!use(entry, entry.baked)) return false;
        bakedBinds++;
        return true;
    }

    static boolean bindInstanced(Entry entry) {
        if (!use(entry, entry.instanced)) return false;
        instancedBinds++;
        return true;
    }

    public static void release(Entry entry) {
        if (entry.lightmapBound) {
            entry.lightmapBound = false;
            bindLightmapUnit(entry.savedLightmapBinding);
        }
        if (entry.programBound) {
            entry.programBound = false;
            GLSMPrograms.glUseProgram(entry.savedProgram);
        }
    }

    static boolean instancedAvailable(Entry entry) {
        return entry.instanced.state == READY;
    }

    static void invalidateAll() {
        for (final Entry entry : ENTRIES.values()) {
            reset(entry.baked);
            reset(entry.instanced);
        }
    }

    private static void reset(Variant variant) {
        if (variant.program != 0) GLSMPrograms.glDeleteProgram(variant.program);
        variant.program = 0;
        variant.state = UNKNOWN;
    }

    private static boolean use(Entry entry, Variant variant) {
        ensureLinked(entry);
        if (variant.state != READY) {
            fallbacks++;
            return false;
        }
        if (!entry.programBound) {
            entry.programBound = true;
            entry.savedProgram = GLSMPrograms.getActiveProgram();
        }
        GLSMPrograms.glUseProgram(variant.program);
        if (entry.wantsLightmap && !entry.lightmapBound) {
            entry.lightmapBound = true;
            entry.savedLightmapBinding = GLSMTextures.getBoundTextureForServerState(DrawState.LIGHTMAP_UNIT);
            bindLightmapUnit(((EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer).getLightmapTexture().getGlTextureId());
        }
        final TesrShaderUniforms uniforms = entry.shader.uniforms();
        if (uniforms != null) uniforms.angelica$bound(variant.program, variant.instanced);
        return true;
    }

    private static void bindLightmapUnit(int texture) {
        final int active = GLSMTextures.getActiveTextureUnit();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + DrawState.LIGHTMAP_UNIT);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + active);
    }

    private static void ensureLinked(Entry entry) {
        final boolean baked = entry.baked.state == UNKNOWN;
        final boolean instanced = entry.instanced.state == UNKNOWN;
        if (!baked && !instanced) return;
        if (GLStateManager.isRecordingDisplayList()) return;
        final TesrShader shader = entry.shader;
        final String vertexBody;
        final String fragmentBody;
        try {
            vertexBody = read(shader.vertex());
            fragmentBody = read(shader.fragment());
        } catch (IOException | RuntimeException e) {
            if (baked) fail(entry, entry.baked, "could not be read", e.toString());
            if (instanced) fail(entry, entry.instanced, "could not be read", e.toString());
            return;
        }
        if (baked) link(entry, false, vertexBody, fragmentBody);
        if (instanced) link(entry, true, vertexBody, fragmentBody);
    }

    private static String read(ResourceLocation location) throws IOException {
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

    static void link(Entry entry, boolean instanced, String vertexBody, String fragmentBody) {
        final Variant variant = instanced ? entry.instanced : entry.baked;
        reset(variant);
        final String name = entry.shader.name();
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compile(entry, variant, GL20.GL_VERTEX_SHADER, TesrShaderPrelude.vertex(instanced) + TesrShaderPrelude.stripVersion(name, vertexBody));
            if (vertex == 0) return;
            fragment = compile(entry, variant, GL20.GL_FRAGMENT_SHADER, TesrShaderPrelude.fragment() + TesrShaderPrelude.stripVersion(name, fragmentBody));
            if (fragment == 0) return;
            program = GLSMPrograms.glCreateProgram();
            GLSMPrograms.glAttachShader(program, vertex);
            GLSMPrograms.glAttachShader(program, fragment);
            GLSMPrograms.glLinkProgram(program);
            if (GLSMPrograms.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
                fail(entry, variant, "failed to link", GLSMPrograms.glGetProgramInfoLog(program));
                return;
            }
            final int previous = GLSMPrograms.getActiveProgram();
            GLSMPrograms.glUseProgram(program);
            try {
                setSampler(program, TesrShaderPrelude.LAYER_SAMPLER, 0);
                setSampler(program, TesrShaderPrelude.LIGHTMAP_SAMPLER, DrawState.LIGHTMAP_UNIT);
                final TesrShaderUniforms uniforms = entry.shader.uniforms();
                if (uniforms != null) uniforms.angelica$linked(program, instanced);
            } finally {
                GLSMPrograms.glUseProgram(previous);
            }
            variant.program = program;
            variant.state = READY;
            LOG.info("TESR shader {} ({} variant) linked: program {}", name, variant.label, program);
            program = 0;
        } catch (RuntimeException e) {
            fail(entry, variant, "failed to build", e.toString());
        } finally {
            if (vertex != 0) GLSMPrograms.glDeleteShader(vertex);
            if (fragment != 0) GLSMPrograms.glDeleteShader(fragment);
            if (program != 0) GLSMPrograms.glDeleteProgram(program);
        }
    }

    private static int compile(Entry entry, Variant variant, int type, String source) {
        final int shader = GLSMPrograms.glCreateShader(type);
        GLSMPrograms.glShaderSource(shader, source);
        GLSMPrograms.glCompileShader(shader);
        if (GLSMPrograms.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) return shader;
        fail(entry, variant, type == GL20.GL_VERTEX_SHADER ? "vertex stage failed to compile" : "fragment stage failed to compile", GLSMPrograms.glGetShaderInfoLog(shader));
        GLSMPrograms.glDeleteShader(shader);
        return 0;
    }

    private static void setSampler(int program, String name, int unit) {
        final int location = GLSMPrograms.glGetUniformLocation(program, name);
        if (location >= 0) GLSMPrograms.glUniform1i(location, unit);
    }

    private static void fail(Entry entry, Variant variant, String what, String detail) {
        variant.state = FAILED;
        linkFailures++;
        LOG.warn("TESR shader {} ({} variant) {}; variant disabled until the next resource reload: {}",
            entry.shader.name(), variant.label, what, detail);
    }
}
