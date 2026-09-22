package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.rendering.RenderFailures;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.DrawState;
import com.gtnewhorizons.angelica.shadercompat.ShaderGlint;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

public final class RenderLayer implements BlendingStateHolder {

    private static final int TESR_BUFFER_SIZE = 65536;

    private static final ObjectOpenCustomHashSet<RenderLayer> CACHE = new ObjectOpenCustomHashSet<>(HashStrategy.INSTANCE);

    static final class Hook {
        final Runnable begin;
        final Runnable end;

        Hook(Runnable begin, Runnable end) {
            this.begin = begin;
            this.end = end;
        }
    }

    private static final Hook[] GLINT_HOOKS = new Hook[ShaderGlint.TINT_SLOTS + 1];
    private static final Hook BEACON_BEAM_HOOK = new Hook(
        () -> GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.BEACON_BEAM),
        GbufferPrograms::teardownSpecialRenderCondition);
    private static final Reference2ObjectOpenHashMap<TesrShader, Hook> SHADER_HOOKS = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<PassOverride, Hook> PASS_HOOKS = new Reference2ObjectOpenHashMap<>();

    private String name;
    @Getter private final VertexFormat vertexFormat;
    @Getter private final int drawMode;
    @Getter private final int expectedBufferSize;
    @Getter private final DrawState state;
    @Getter private final ResourceLocation textureId;
    @Getter private final boolean unfilteredAtlas;
    private final TransparencyType transparencyType;
    private final Hook hook;
    private final boolean noPass;
    private final int hash;

    private RenderLayer(String name, VertexFormat vertexFormat, int drawMode, int expectedBufferSize, DrawState state,
        ResourceLocation textureId, boolean unfilteredAtlas, TransparencyType transparencyType, Hook hook,
        boolean noPass) {
        this.name = name;
        this.vertexFormat = vertexFormat;
        this.drawMode = drawMode;
        this.expectedBufferSize = expectedBufferSize;
        this.state = state;
        this.textureId = textureId;
        this.unfilteredAtlas = unfilteredAtlas;
        this.transparencyType = transparencyType;
        this.hook = hook;
        this.noPass = noPass;
        int h = System.identityHashCode(vertexFormat);
        h = h * 31 + drawMode;
        h = h * 31 + expectedBufferSize;
        h = h * 31 + System.identityHashCode(state);
        h = h * 31 + Objects.hashCode(textureId);
        h = h * 31 + (unfilteredAtlas ? 1 : 0);
        h = h * 31 + transparencyType.ordinal();
        h = h * 31 + System.identityHashCode(hook);
        h = h * 31 + (noPass ? 1 : 0);
        this.hash = h;
    }

    public static RenderLayer tesr(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit) {
        return build(texture, material, pass, offsetFactor, offsetUnits, glintSlot, cull, lit, false);
    }

    public static RenderLayer tesrNoPass(ResourceLocation texture, TesrMaterial material, int cull, boolean lit) {
        return build(texture, material, PassOverride.NONE, 0.0f, 0.0f, ShaderGlint.NO_TINT, cull, lit, true);
    }

    private static RenderLayer build(ResourceLocation texture, TesrMaterial material, PassOverride pass, float offsetFactor, float offsetUnits, int glintSlot, int cull, boolean lit, boolean noPass) {
        final DrawState state = DrawState.forMaterial(material, cull, lit, offsetFactor, offsetUnits);
        final Hook hook = hookFor(material, noPass ? PassOverride.NONE : pass, glintSlot);
        final TransparencyType transparency = transparencyFor(state.getBlend());
        final RenderLayer candidate = new RenderLayer(null, BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL,
            GL11.GL_QUADS, TESR_BUFFER_SIZE, state, texture, material.isUnfilteredAtlas(), transparency, hook,
            noPass);
        final RenderLayer retained = intern(candidate);
        if (retained == candidate) {
            retained.name = "angelica_tesr_" + (noPass ? "nopass_" : "")
                + material.transparency().name().toLowerCase(Locale.ROOT) + (noPass ? "" : pass.nameSuffix())
                + (offsetFactor == 0.0f && offsetUnits == 0.0f ? "" : "_offset" + offsetFactor + "_" + offsetUnits)
                + "_cull" + state.getCull() + (state.isLit() ? "_lit" : "")
                + (glintSlot == ShaderGlint.NO_TINT ? "" : "_tint" + glintSlot);
        }
        return retained;
    }

    private static RenderLayer intern(RenderLayer layer) {
        return CACHE.addOrGet(layer);
    }

    public static void clearInterningAndHooks() {
        CACHE.clear();
        SHADER_HOOKS.clear();
        PASS_HOOKS.clear();
        for (int i = 0; i < GLINT_HOOKS.length; i++) {
            GLINT_HOOKS[i] = null;
        }
    }

    public static int cacheSize() {
        return CACHE.size();
    }

    private static TransparencyType transparencyFor(int blend) {
        return switch (blend) {
            case DrawState.OPAQUE -> TransparencyType.OPAQUE;
            case DrawState.GLINT -> TransparencyType.DECAL;
            default -> TransparencyType.GENERAL_TRANSPARENT;
        };
    }

    private static Hook hookFor(TesrMaterial material, PassOverride pass, int glintSlot) {
        switch (material.special()) {
            case GLINT -> {
                return glintHook(glintSlot);
            }
            case BEACON_BEAM -> {
                return BEACON_BEAM_HOOK;
            }
            case NONE -> {
                final TesrShader shader = material.shader();
                if (shader != null) {
                    return shaderHook(shader);
                }
            }
        }
        return pass == PassOverride.NONE ? null : passHook(pass);
    }

    private static Hook glintHook(int slot) {
        Hook hook = GLINT_HOOKS[slot + 1];
        if (hook == null) {
            hook = new Hook(() -> {
                GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.GLINT);
                ShaderGlint.bindTintedGlint(slot);
            }, GbufferPrograms::teardownSpecialRenderCondition);
            GLINT_HOOKS[slot + 1] = hook;
        }
        return hook;
    }

    private static Hook shaderHook(TesrShader shader) {
        Hook hook = SHADER_HOOKS.get(shader);
        if (hook == null) {
            hook = new Hook(shader.bind(), shader.release());
            SHADER_HOOKS.put(shader, hook);
        }
        return hook;
    }

    private static Hook passHook(PassOverride pass) {
        Hook hook = PASS_HOOKS.get(pass);
        if (hook == null) {
            hook = new Hook(pass::apply, pass::clear);
            PASS_HOOKS.put(pass, hook);
        }
        return hook;
    }

    @Override
    public TransparencyType getTransparencyType() {
        return transparencyType;
    }

    public void startDrawing() {
        boolean filtering = false;
        boolean hookActive = false;
        try {
            state.apply();
            if (textureId != null) {
                GLStateManager.enableTexture();
                Minecraft.getMinecraft().getTextureManager().bindTexture(textureId);
                if (unfilteredAtlas) {
                    filtering = true;
                    TextureUtil.func_152777_a(false, false, 1.0F);
                }
            } else {
                GLStateManager.disableTexture();
            }
            if (hook != null) {
                hookActive = true;
                hook.begin.run();
            }
        } catch (Throwable failure) {
            if (hookActive) {
                try {
                    hook.end.run();
                } catch (Throwable cleanup) {
                    RenderFailures.suppress(failure, cleanup);
                }
            }
            if (filtering) {
                try {
                    TextureUtil.func_147945_b();
                } catch (Throwable cleanup) {
                    RenderFailures.suppress(failure, cleanup);
                }
            }
            throw failure;
        }
    }

    public void endDrawing() {
        Throwable failure = null;
        try {
            if (hook != null) hook.end.run();
        } catch (Throwable t) {
            failure = t;
        } finally {
            if (textureId != null && unfilteredAtlas) {
                try {
                    TextureUtil.func_147945_b();
                } catch (Throwable cleanup) {
                    failure = RenderFailures.suppress(failure, cleanup);
                }
            }
        }
        if (failure != null) RenderFailures.rethrow(failure);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return this == object;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "RenderLayer[" + name + ", " + state + ", texture=" + textureId + ']';
    }


    private enum HashStrategy implements Hash.Strategy<RenderLayer> {
        INSTANCE;

        @Override
        public int hashCode(@Nullable RenderLayer layer) {
            return layer == null ? 0 : layer.hash;
        }

        @Override
        public boolean equals(@Nullable RenderLayer a, @Nullable RenderLayer b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return a.vertexFormat == b.vertexFormat && a.drawMode == b.drawMode
                && a.expectedBufferSize == b.expectedBufferSize && a.state == b.state
                && Objects.equals(a.textureId, b.textureId) && a.unfilteredAtlas == b.unfilteredAtlas
                && a.transparencyType == b.transparencyType && a.hook == b.hook
                && a.noPass == b.noPass;
        }
    }
}
