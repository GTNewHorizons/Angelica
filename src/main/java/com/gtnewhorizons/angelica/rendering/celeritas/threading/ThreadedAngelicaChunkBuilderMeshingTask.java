package com.gtnewhorizons.angelica.rendering.celeritas.threading;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.AngelicaBlockSafetyRegistry;
import com.gtnewhorizons.angelica.rendering.RenderThreadContext;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.WorldClientExtension;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ChunkRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext;

public class ThreadedAngelicaChunkBuilderMeshingTask extends AngelicaChunkBuilderMeshingTask {
    /** Vanilla render types (0-41) are thread-safe. ISBRHs (42+) need @ThreadSafeISBRH. */
    private static final int MAX_VANILLA_RENDER_TYPE = 41;

    private final @Nullable ChunkRenderContext renderContext;

    private IBlockAccess blockAccess;
    private SmoothBiomeColorCache biomeColorCache;
    private boolean enteredLocalMode;

    public ThreadedAngelicaChunkBuilderMeshingTask(RenderSection render, @Nullable ChunkRenderContext renderContext, int time, Vector3d camera) {
        super(render, time, camera);
        this.renderContext = renderContext;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        final AngelicaChunkBuildContext buildContext = (AngelicaChunkBuildContext) context;
        initBlockAccess(buildContext);
        return super.execute(context, cancellationToken);
    }

    private void initBlockAccess(AngelicaChunkBuildContext buildContext) {
        final int minX = this.render.getOriginX();
        final int minY = this.render.getOriginY();
        final int minZ = this.render.getOriginZ();

        if (this.renderContext != null) {
            final WorldSlice worldSlice = buildContext.getWorldSlice();
            worldSlice.copyData(this.renderContext);
            this.blockAccess = worldSlice;
            this.biomeColorCache = worldSlice.getBiomeColorCache();
            buildContext.setupLightPipeline(minX, minY, minZ);
        } else {
            // Fallback: direct world access (main thread only)
            final var world = Minecraft.getMinecraft().theWorld;
            this.blockAccess = new ChunkCache(world, minX - 1, minY - 1, minZ - 1, minX + 17, minY + 17, minZ + 17, 1);
            this.biomeColorCache = ((WorldClientExtension) world).celeritas$getSmoothBiomeColorCache();
            this.biomeColorCache.update(new SectionPos(this.render.getChunkX(), this.render.getChunkY(), this.render.getChunkZ()));
            buildContext.setupLightPipeline(this.blockAccess, minX, minY, minZ);
        }
    }

    @Override
    protected Tessellator getTessellator() {
        return Tessellator.instance;
    }

    @Override
    protected void setRenderPass(int pass) {
        RenderPassHelper.setWorldRenderPass(pass);
    }

    @Override
    protected IBlockAccess getBlockAccess() {
        return blockAccess;
    }

    @Override
    protected SmoothBiomeColorCache getBiomeColorCache() {
        return biomeColorCache;
    }

    @Override
    protected boolean isThreaded() {
        return enteredLocalMode;
    }

    @Override
    protected boolean canRenderOffThread(Block block) {
        final int renderType = block.getRenderType();
        return (renderType >= 0 && renderType <= MAX_VANILLA_RENDER_TYPE)
            || AngelicaBlockSafetyRegistry.canBlockRenderOffThread(block, true, true);
    }

    @Override
    protected void onEnterExecute() {
        enteredLocalMode = !TessellatorManager.isOnMainThread();
        if (enteredLocalMode) {
            TessellatorManager.enterLocalMode();
            if (blockAccess instanceof WorldSlice worldSlice) {
                RenderThreadContext.set(worldSlice);
            }
        }
    }

    @Override
    protected void onExitExecute() {
        if (enteredLocalMode) {
            RenderThreadContext.clear();
            RenderPassHelper.resetWorldRenderPass();
            TessellatorManager.exitLocalMode();
        }
    }

    @Override
    protected void addExtraCrashInfo(CrashReportCategory category) {
        category.addCrashSection("Thread", Thread.currentThread().getName());
    }
}
