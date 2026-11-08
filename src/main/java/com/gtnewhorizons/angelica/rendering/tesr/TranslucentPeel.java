package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.DepthPeel;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.texture.DepthBufferFormat;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.translucency.TranslucencyLayers;
import me.jellysquid.mods.sodium.client.gui.options.named.TranslucencyMode;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/// Depth peels the translucent geometry the block entity batchers have queued ([DepthPeel]), since batches group it by
/// layer and material rather than by depth. A flush whose translucent geometry comes from one block entity in one
/// batch that keeps submission order is not peeled.
///
/// Each of the first [#SLOTS_PER_PASS] peeled flushes of a pass keeps its own peel count, and later ones peel
/// [#MAX_PEELS]. Occlusion queries, read back frames later without stalling, lower a count one peel at a time while
/// its last peel draws nothing and reset it when the rest pass draws.
public final class TranslucentPeel {

    private static final int MAX_PEELS = 4;
    private static final int SLOTS_PER_PASS = 32;

    private static int width;
    private static int height;
    private static int depthVersion = -1;
    private static DepthBufferFormat depthFormat;
    private static Target[] peels;
    private static Target sceneDepth;
    private static StateSet clearState;
    private static final Slot[] slots = new Slot[2 * SLOTS_PER_PASS];
    private static int passFlushes;
    private static boolean replay;
    private static Object firstSource;
    private static Object firstBatch;
    private static int firstBlockEntity;
    private static int blockEntity;
    private static boolean mixed;

    private TranslucentPeel() {}

    static boolean enabled() {
        return ClientProxy.options().performance.translucencyMode == TranslucencyMode.FANCY && Iris.enabled
            && TesrBatchRenderer.deferredPipeline() == null;
    }

    public static void beginPass() {
        passFlushes = 0;
        resetBatches();
    }

    public static void beforeBlockEntity() {
        blockEntity++;
    }

    /// `source` and `batch` identify one draw: an `AngelicaBufferSource` and a layer, or [RetainedTesrGroups] and a
    /// group.
    public static void queued(RenderLayer layer, Object source, Object batch) {
        if (mixed || layer.getTransparencyType() == TransparencyType.OPAQUE) return;
        if (firstBatch == null) {
            firstSource = source;
            firstBatch = batch;
            firstBlockEntity = blockEntity;
        } else if (batch != firstBatch || source != firstSource || blockEntity != firstBlockEntity) {
            mixed = true;
        }
    }

    public static void queuedUnordered(RenderLayer layer) {
        if (layer.getTransparencyType() != TransparencyType.OPAQUE) mixed = true;
    }

    private static void resetBatches() {
        firstSource = null;
        firstBatch = null;
        mixed = false;
    }

    public static void flush() {
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
        BatchStateGuard.suspend();
        try {
            models.drawOpaque();
            tesrs.drawOpaque();
        } finally {
            BatchStateGuard.resume();
        }
        if (!enabled()) {
            if (peels != null) destroy();
            return;
        }
        models.queueShadows();
        final boolean peeling = mixed;
        resetBatches();
        if (!peeling) return;
        BatchStateGuard.suspend();
        try {
            final boolean modelsQueued = models.beginPeel();
            final boolean tesrsQueued = tesrs.beginPeel();
            if (modelsQueued || tesrsQueued) peel(modelsQueued, tesrsQueued);
        } finally {
            try {
                tesrs.endPeel();
            } finally {
                try {
                    models.endPeel();
                } finally {
                    BatchStateGuard.resume();
                }
            }
        }
    }

    private static void draw(boolean models, boolean tesrs) {
        if (models) ModelPartBatcher.INSTANCE.drawPeeled(replay);
        if (tesrs) TesrBatchRenderer.INSTANCE.drawPeeled(replay);
        replay = true;
    }

    private static void peel(boolean models, boolean tesrs) {
        replay = false;
        final int target = GLSMFramebuffer.getDrawFramebuffer();
        final int read = GLSMFramebuffer.getReadFramebuffer();
        if (!syncSize() || target != Minecraft.getMinecraft().getFramebuffer().framebufferObject && target != TranslucencyLayers.INSTANCE.openFramebuffer()) {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            draw(models, tesrs);
            return;
        }
        final int unit = GLSMTextures.getActiveTextureUnit();
        final int previousPeelTexture = GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.TEXTURE_UNIT).getBinding();
        final int previousSceneTexture = GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.SCENE_TEXTURE_UNIT).getBinding();
        final Slot slot = slot(passFlushes++);
        final int count = slot != null ? slot.update() : MAX_PEELS;
        final boolean query = slot != null && slot.beginQueries(count);
        boolean completed = false;
        try {
            blitDepth(target, sceneDepth.framebuffer.getId());
            bindTexture(DepthPeel.SCENE_TEXTURE_UNIT, sceneDepth.depth);
            Target bound = sceneDepth;
            for (int i = 0; i < count; i++) {
                final Target current = peels[i % 2];
                clearDepth(current);
                current.framebuffer.bind();
                bindTexture(DepthPeel.TEXTURE_UNIT, bound.depth);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                DepthPeel.setPass(DepthPeel.Pass.DEPTH, i == 0);
                if (query) slot.begin(i);
                draw(models, tesrs);
                if (query) slot.end();
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
                bindTexture(DepthPeel.TEXTURE_UNIT, current.depth);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                DepthPeel.setPass(DepthPeel.Pass.COLOR, false);
                draw(models, tesrs);
                bound = current;
            }
            DepthPeel.setPass(DepthPeel.Pass.REST, false);
            if (query) slot.begin(count);
            draw(models, tesrs);
            if (query) slot.end();
            if (DepthPeel.skippedApplicationDraws()) {
                DepthPeel.setPass(DepthPeel.Pass.APPLICATION, false);
                draw(models, tesrs);
            }
            completed = true;
        } finally {
            if (!completed && slot != null) slot.abandon();
            DepthPeel.setPass(DepthPeel.Pass.OFF, false);
            bindTexture(DepthPeel.TEXTURE_UNIT, previousPeelTexture);
            bindTexture(DepthPeel.SCENE_TEXTURE_UNIT, previousSceneTexture);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static Slot slot(int flush) {
        if (flush >= SLOTS_PER_PASS) return null;
        final int index = (MinecraftForgeClient.getRenderPass() == 1 ? SLOTS_PER_PASS : 0) + flush;
        if (slots[index] == null) slots[index] = new Slot();
        return slots[index];
    }

    private static void clearDepth(Target target) {
        if (clearState == null) clearState = StateSet.forMask(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        final int saved = GLSMAttribStack.pushState(clearState);
        try {
            GLSMRasterizer.disableScissorTest();
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(0.0);
            target.framebuffer.bind();
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GLSMAttribStack.popStateTo(saved);
        }
    }

    private static void blitDepth(int from, int to) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GLSMFramebuffer.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void bindTexture(int unit, int texture) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static boolean syncSize() {
        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
        final IRenderTargetExt ext = (IRenderTargetExt) main;
        if (peels != null && main.framebufferWidth == width && main.framebufferHeight == height && ext.iris$getDepthBufferVersion() == depthVersion) return true;
        destroyTargets();
        width = main.framebufferWidth;
        height = main.framebufferHeight;
        depthVersion = ext.iris$getDepthBufferVersion();
        final int mainDepth = ext.iris$getDepthTextureId();
        if (width <= 0 || height <= 0 || mainDepth <= 0) return false;
        depthFormat = DepthBufferFormat.fromGlEnumOrDefault(TextureInfoCache.INSTANCE.getInfo(mainDepth).getInternalFormat());
        sceneDepth = new Target(depthFormat.getGlInternalFormat(), depthFormat.getGlType(), depthFormat.getGlFormat());
        peels = new Target[] { Target.float32(), Target.float32() };
        return true;
    }

    public static void destroy() {
        destroyTargets();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) slots[i].delete();
            slots[i] = null;
        }
    }

    private static void destroyTargets() {
        if (peels != null) for (Target target : peels) target.destroy();
        if (sceneDepth != null) sceneDepth.destroy();
        peels = null;
        sceneDepth = null;
    }

    private static final class Slot {
        private int count = MAX_PEELS;
        private final int[] queries = new int[MAX_PEELS + 1];
        private int issued = -1;
        private boolean querying;

        int update() {
            if (issued < 0) return count;
            for (int i = 0; i <= issued; i++) {
                if (GLSMQueries.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return count;
            }
            int drawn = issued;
            for (int i = 0; i < issued; i++) {
                if (GLSMQueries.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT) == 0) {
                    drawn = i;
                    break;
                }
            }
            if (drawn == issued && GLSMQueries.glGetQueryObjecti(queries[issued], GL15.GL_QUERY_RESULT) != 0) {
                count = MAX_PEELS;
            } else if (drawn < count) {
                count = Math.max(1, count - 1);
            }
            issued = -1;
            return count;
        }

        boolean beginQueries(int peels) {
            if (issued >= 0 || RENDER_BACKEND.isSDLGPU()) return false;
            if (queries[0] == 0) {
                try {
                    for (int i = 0; i < queries.length; i++) queries[i] = GLSMQueries.glGenQueries();
                } catch (UnsupportedOperationException e) {
                    return false;
                }
            }
            issued = peels;
            return true;
        }

        void begin(int peel) {
            GLSMQueries.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, queries[peel]);
            querying = true;
        }

        void end() {
            querying = false;
            GLSMQueries.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
        }

        void abandon() {
            if (querying) end();
            issued = -1;
        }

        void delete() {
            if (queries[0] == 0) return;
            for (int query : queries) GLSMQueries.glDeleteQueries(query);
        }
    }

    private static final class Target {
        final int depth;
        final GlFramebuffer framebuffer = new GlFramebuffer();

        Target(int internalFormat, int format, int type) {
            depth = texture(internalFormat, format, type);
            framebuffer.addDepthAttachment(depth);
            framebuffer.noDrawBuffers();
            RenderSystem.readBuffer(framebuffer.getId(), GL11.GL_NONE);
        }

        static Target float32() {
            return new Target(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        }

        private static int texture(int internalFormat, int format, int type) {
            final int previous = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int texture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            return texture;
        }

        void destroy() {
            framebuffer.destroy();
            GLSMTextures.glDeleteTextures(depth);
        }
    }
}
