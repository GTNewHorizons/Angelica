package com.gtnewhorizons.angelica.rendering.translucency;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.texture.DepthBufferFormat;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.render.PanoramaRenderer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/// Draws each [TranslucentLayer] into its own target, seeded with the main depth, and composites the drawn layers over
/// the main framebuffer per pixel, farthest first. Layers hold premultiplied color with coverage alpha (GLSM layer
/// blending), and the composite writes the nearest layer depth.
///
/// The terrain layer has a second sheet for fragments behind the block-entity layer, so a tile entity inside a
/// block's own translucent model is covered only by the faces in front of it.
///
/// Glows (blended block-entity draws that write no depth) would sort at the opaque depth behind them, so
/// [DepthEcho] records their depth separately, and it is merged into the block-entity layer before anything
/// reads that depth.
public final class TranslucencyLayers {

    private static final int LAYERS = TranslucentLayer.values().length;
    private static final int FIRST_LAYER_UNIT = 1;
    private static final int TERRAIN_BEHIND_UNIT = FIRST_LAYER_UNIT + 2 * LAYERS;
    private static final int SPLIT_DEPTH_UNIT = TERRAIN_BEHIND_UNIT + 1;
    private static final int[] SINGLE_BUFFER = { 0 };
    private static final int[] SPLIT_BUFFERS = { 0, 1 };
    private static final int[] BEHIND_BUFFER = { 1 };

    public static final TranslucencyLayers INSTANCE = new TranslucencyLayers(new MinecraftTarget());

    interface MainTarget {
        int framebuffer();
        int depthTexture();
        int width();
        int height();
        int depthVersion();
    }

    private final MainTarget main;
    private final Target[] layers = new Target[LAYERS];
    private Target depthSnapshot;
    private int width;
    private int height;
    private int depthVersion = -1;
    private DepthBufferFormat depthFormat;

    private boolean decided;
    private boolean active;
    private int drawnMask;
    private TranslucentLayer open;
    private int savedDraw;
    private int savedRead;
    private boolean alphaMasked;

    private boolean splitting;
    private boolean behindDrawn;

    private Target glowDepth;
    private boolean replayingGlows;

    private CompositeProgram program;
    private CompositeProgram behindProgram;
    private GlProgram<Object> glowMergeProgram;
    private int vao;
    private StateSet prepareState;
    private StateSet compositeState;
    private StateSet clearDepthState;
    private final Runnable compositePass = this::drawComposite;
    private final Runnable glowMergePass = this::drawGlowMerge;

    TranslucencyLayers(MainTarget main) {
        this.main = main;
    }

    /// Whether layering applies is decided on the first [#active] call, once Iris has prepared the frame's pipeline.
    public void beginFrame() {
        decided = false;
        resetFrame();
    }

    void beginFrame(boolean active) {
        this.active = active;
        decided = true;
        resetFrame();
    }

    private void resetFrame() {
        drawnMask = 0;
        behindDrawn = false;
        DepthEcho.takeEchoed();
    }

    public boolean active() {
        if (!decided) {
            active = ClientProxy.options().performance.translucencyMode.composites() && Iris.enabled
                && Iris.getPipelineManager().getPipelineNullable() instanceof FixedFunctionWorldRenderingPipeline;
            decided = true;
        }
        return active;
    }

    public boolean beginIfActive(TranslucentLayer layer) {
        if (!active()) return false;
        begin(layer);
        return true;
    }

    public void begin(TranslucentLayer layer) {
        if (open != null) throw new IllegalStateException("Translucency layer " + open + " is still open");
        BatchStateGuard.beforeChange();
        savedDraw = GLSMFramebuffer.getDrawFramebuffer();
        savedRead = GLSMFramebuffer.getReadFramebuffer();
        syncSize();
        if (layer == TranslucentLayer.TERRAIN) mergeGlowDepth();
        final Target target = layer(layer);
        final int bit = 1 << layer.ordinal();
        if ((drawnMask & bit) == 0) {
            drawnMask |= bit;
            prepare(target);
            if (layer == TranslucentLayer.BLOCK_ENTITIES) clearDepth(glowDepth());
        }
        target.framebuffer.bind();
        if (layer == TranslucentLayer.BLOCK_ENTITIES) DepthEcho.arm(glowDepth.framebuffer.getId(), target.framebuffer.getId());
        open = layer;
        GLSMBlend.setLayerBlending(true);
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        alphaMasked = !mask.alpha;
        if (alphaMasked) GLSMBlend.glColorMask(mask.red, mask.green, mask.blue, true);
    }

    public void end() {
        BatchStateGuard.beforeChange();
        DepthEcho.disarm();
        if (alphaMasked) {
            final ColorMaskStack mask = GLSMBlend.getColorMask();
            GLSMBlend.glColorMask(mask.red, mask.green, mask.blue, false);
        }
        GLSMBlend.setLayerBlending(false);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
        open = null;
    }

    public boolean redirectsMainBind() {
        return open != null;
    }

    public int openFramebuffer() {
        return open != null ? layers[open.ordinal()].framebuffer.getId() : 0;
    }

    public void bindOpenLayer() {
        (replayingGlows ? glowDepth : layers[open.ordinal()]).framebuffer.bind();
    }

    public boolean beginGlowReplay() {
        if (!DepthEcho.armed()) return false;
        BatchStateGuard.beforeChange();
        glowDepth.framebuffer.bind();
        replayingGlows = true;
        DepthEcho.beginReplay();
        return true;
    }

    public void endGlowReplay() {
        BatchStateGuard.beforeChange();
        DepthEcho.endReplay();
        replayingGlows = false;
        layers[open.ordinal()].framebuffer.bind();
    }

    public void beginTerrainSplit(int program) {
        splitting = open == TranslucentLayer.TERRAIN && (drawnMask & (1 << TranslucentLayer.BLOCK_ENTITIES.ordinal())) != 0;
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "u_SplitEnabled"), splitting ? 1 : 0);
        if (!splitting) return;
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, "u_SplitDepth"), SPLIT_DEPTH_UNIT);
        final Target terrain = layers[TranslucentLayer.TERRAIN.ordinal()];
        if (!behindDrawn) {
            behindDrawn = true;
            terrain.attachBehind(width, height);
            terrain.framebuffer.drawBuffers(BEHIND_BUFFER);
            clear(terrain);
        }
        final int unit = GLSMTextures.getActiveTextureUnit();
        bindTexture(SPLIT_DEPTH_UNIT, layers[TranslucentLayer.BLOCK_ENTITIES.ordinal()].depth);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        terrain.framebuffer.drawBuffers(SPLIT_BUFFERS);
    }

    public void endTerrainSplit() {
        if (!splitting) return;
        splitting = false;
        layers[TranslucentLayer.TERRAIN.ordinal()].framebuffer.drawBuffers(SINGLE_BUFFER);
        final int unit = GLSMTextures.getActiveTextureUnit();
        bindTexture(SPLIT_DEPTH_UNIT, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
    }

    public void composite() {
        if (drawnMask == 0) return;
        BatchStateGuard.beforeChange();
        syncSize();
        if (drawnMask == 0) return;
        try {
            mergeGlowDepth();
            fullscreenPass(compositePass);
        } finally {
            drawnMask = 0;
            behindDrawn = false;
        }
    }

    private void drawComposite() {
        final Target snapshot = depthSnapshot();
        blit(main.framebuffer(), snapshot.framebuffer.getId(), GL11.GL_DEPTH_BUFFER_BIT);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebuffer());
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
        bindTexture(0, snapshot.depth);
        for (int i = 0; i < LAYERS; i++) {
            if ((drawnMask & (1 << i)) == 0) continue;
            bindTexture(FIRST_LAYER_UNIT + i, layers[i].color);
            bindTexture(FIRST_LAYER_UNIT + LAYERS + i, layers[i].depth);
        }
        if (behindDrawn) bindTexture(TERRAIN_BEHIND_UNIT, layers[TranslucentLayer.TERRAIN.ordinal()].behind);
        final CompositeProgram composite = behindDrawn ? behindProgram : program;
        GLSMPrograms.glUseProgram(composite.program.handle());
        GLSMPrograms.glUniform1i(composite.maskUniform, drawnMask);
    }

    private void mergeGlowDepth() {
        if (!DepthEcho.takeEchoed() || (drawnMask & (1 << TranslucentLayer.BLOCK_ENTITIES.ordinal())) == 0) return;
        fullscreenPass(glowMergePass);
    }

    private void drawGlowMerge() {
        layers[TranslucentLayer.BLOCK_ENTITIES.ordinal()].framebuffer.bind();
        GLSMBlend.disableBlend();
        GLSMBlend.glColorMask(false, false, false, false);
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
        bindTexture(0, glowDepth.depth);
        GLSMPrograms.glUseProgram(glowMergeProgram.handle());
    }

    private void fullscreenPass(Runnable setup) {
        final int draw = GLSMFramebuffer.getDrawFramebuffer();
        final int read = GLSMFramebuffer.getReadFramebuffer();
        final int activeProgram = GLSMPrograms.getActiveProgram();
        final int activeVao = GLSMQueries.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        if (program == null) initProgram();
        final int depth = GLSMAttribStack.pushState(compositeState);
        try {
            GLSMRasterizer.disableScissorTest();
            GLSMRasterizer.disableCull();
            GLSMDepthStencilAlpha.enableDepthTest();
            GLSMDepthStencilAlpha.glDepthMask(true);
            setup.run();
            GLSMRasterizer.glViewport(0, 0, width, height);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GLSMVertexInput.glBindVertexArray(activeVao);
            GLSMPrograms.glUseProgram(activeProgram);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GLSMAttribStack.popStateTo(depth);
        }
    }

    public void destroy() {
        destroyTargets();
        if (program != null) {
            program.program.delete();
            behindProgram.program.delete();
            glowMergeProgram.delete();
            GLSMVertexInput.glDeleteVertexArrays(vao);
            program = null;
            behindProgram = null;
            glowMergeProgram = null;
        }
    }

    private void initProgram() {
        program = new CompositeProgram("angelica:translucency_composite", ShaderConstants.EMPTY);
        behindProgram = new CompositeProgram("angelica:translucency_composite_behind", ShaderConstants.builder().add("TERRAIN_BEHIND").build());
        glowMergeProgram = PanoramaRenderer.loadProgram("angelica:translucency_glow_depth", "angelica:panorama_blur", "angelica:translucency_glow_depth", ctx -> ctx);
        final int merge = glowMergeProgram.handle();
        GLSMPrograms.glUseProgram(merge);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(merge, "u_GlowDepth"), 0);
        GLSMPrograms.glUseProgram(0);
        vao = GLSMVertexInput.glGenVertexArrays();
        compositeState = StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS);
    }

    private void prepare(Target target) {
        clear(target);
        blit(main.framebuffer(), target.framebuffer.getId(), depthFormat.isCombinedStencil() ? GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT : GL11.GL_DEPTH_BUFFER_BIT);
    }

    private void clear(Target target) {
        if (prepareState == null) prepareState = StateSet.forMask(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        final int depth = GLSMAttribStack.pushState(prepareState);
        try {
            GLSMRasterizer.disableScissorTest();
            GLSMBlend.glColorMask(true, true, true, true);
            target.framebuffer.bind();
            GLSMBlend.glClearColor(0f, 0f, 0f, 0f);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } finally {
            GLSMAttribStack.popStateTo(depth);
        }
    }

    private void clearDepth(Target target) {
        if (clearDepthState == null) clearDepthState = StateSet.forMask(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        final int depth = GLSMAttribStack.pushState(clearDepthState);
        try {
            GLSMRasterizer.disableScissorTest();
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(1.0);
            target.framebuffer.bind();
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GLSMAttribStack.popStateTo(depth);
        }
    }

    private void blit(int from, int to, int mask) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GLSMFramebuffer.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST);
    }

    private static void bindTexture(int unit, int texture) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private void syncSize() {
        if (main.width() == width && main.height() == height && main.depthVersion() == depthVersion) return;
        destroyTargets();
        width = main.width();
        height = main.height();
        depthVersion = main.depthVersion();
        depthFormat = DepthBufferFormat.fromGlEnumOrDefault(TextureInfoCache.INSTANCE.getInfo(main.depthTexture()).getInternalFormat());
    }

    private Target layer(TranslucentLayer layer) {
        final int i = layer.ordinal();
        if (layers[i] == null) layers[i] = new Target(width, height, depthFormat, true);
        return layers[i];
    }

    private Target glowDepth() {
        if (glowDepth == null) glowDepth = new Target(width, height, depthFormat, false);
        return glowDepth;
    }

    private Target depthSnapshot() {
        if (depthSnapshot == null) depthSnapshot = new Target(width, height, depthFormat, false);
        return depthSnapshot;
    }

    private void destroyTargets() {
        for (int i = 0; i < LAYERS; i++) {
            if (layers[i] != null) layers[i].destroy();
            layers[i] = null;
        }
        if (depthSnapshot != null) depthSnapshot.destroy();
        depthSnapshot = null;
        if (glowDepth != null) glowDepth.destroy();
        glowDepth = null;
        DepthEcho.disarm();
        resetFrame();
    }

    private static final class CompositeProgram {
        final GlProgram<Object> program;
        final int maskUniform;

        CompositeProgram(String name, ShaderConstants constants) {
            program = PanoramaRenderer.loadProgram(name, "angelica:panorama_blur", "angelica:translucency_composite", constants, ctx -> ctx);
            final int handle = program.handle();
            GLSMPrograms.glUseProgram(handle);
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_Depth"), 0);
            for (int i = 0; i < LAYERS; i++) {
                GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_Layer" + i), FIRST_LAYER_UNIT + i);
                GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_LayerDepth" + i), FIRST_LAYER_UNIT + LAYERS + i);
            }
            GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_TerrainBehind"), TERRAIN_BEHIND_UNIT);
            maskUniform = GLSMPrograms.glGetUniformLocation(handle, "u_LayerMask");
            GLSMPrograms.glUseProgram(0);
        }
    }

    private static final class Target {
        final int color;
        final int depth;
        final GlFramebuffer framebuffer;
        int behind;

        Target(int width, int height, DepthBufferFormat depthFormat, boolean withColor) {
            depth = texture(width, height, depthFormat.getGlInternalFormat(), depthFormat.getGlType(), depthFormat.getGlFormat());
            framebuffer = new GlFramebuffer();
            framebuffer.addDepthAttachment(depth);
            if (withColor) {
                color = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
                framebuffer.addColorAttachment(0, color);
                framebuffer.drawBuffers(SINGLE_BUFFER);
                framebuffer.readBuffer(0);
            } else {
                color = 0;
                framebuffer.noDrawBuffers();
                RenderSystem.readBuffer(framebuffer.getId(), GL11.GL_NONE);
            }
        }

        void attachBehind(int width, int height) {
            if (behind != 0) return;
            behind = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            framebuffer.addColorAttachment(1, behind);
        }

        private static int texture(int width, int height, int internalFormat, int format, int type) {
            final int previous = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int texture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            return texture;
        }

        void destroy() {
            framebuffer.destroy();
            if (color != 0) GLSMTextures.glDeleteTextures(color);
            if (behind != 0) GLSMTextures.glDeleteTextures(behind);
            GLSMTextures.glDeleteTextures(depth);
        }
    }

    private static final class MinecraftTarget implements MainTarget {
        private static Framebuffer fb() {
            return Minecraft.getMinecraft().getFramebuffer();
        }

        @Override public int framebuffer() { return fb().framebufferObject; }
        @Override public int depthTexture() { return ((IRenderTargetExt) fb()).iris$getDepthTextureId(); }
        @Override public int width() { return fb().framebufferWidth; }
        @Override public int height() { return fb().framebufferHeight; }
        @Override public int depthVersion() { return ((IRenderTargetExt) fb()).iris$getDepthBufferVersion(); }
    }
}
