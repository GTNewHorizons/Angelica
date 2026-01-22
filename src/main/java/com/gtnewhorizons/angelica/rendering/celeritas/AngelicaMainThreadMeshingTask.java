package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.rendering.celeritas.threading.RenderPassHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.util.position.SectionPos;
import org.embeddedt.embeddium.impl.util.task.CancellationToken;
import org.joml.Vector3d;

public class AngelicaMainThreadMeshingTask extends AngelicaChunkBuilderMeshingTask {

    private IBlockAccess blockAccess;
    private SmoothBiomeColorCache biomeColorCache;

    public AngelicaMainThreadMeshingTask(RenderSection render, int time, Vector3d camera) {
        super(render, time, camera);
        initBlockAccess();
    }

    private void initBlockAccess() {
        final int minX = this.render.getOriginX();
        final int minY = this.render.getOriginY();
        final int minZ = this.render.getOriginZ();

        final var world = Minecraft.getMinecraft().theWorld;
        this.biomeColorCache = ((WorldClientExtension) world).celeritas$getSmoothBiomeColorCache();
        this.biomeColorCache.update(new SectionPos(this.render.getChunkX(), this.render.getChunkY(), this.render.getChunkZ()));
        this.blockAccess = new ChunkCache(world, minX - 1, minY - 1, minZ - 1, minX + 17, minY + 17, minZ + 17, 1);
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        final AngelicaChunkBuildContext buildContext = (AngelicaChunkBuildContext) context;

        final int minX = this.render.getOriginX();
        final int minY = this.render.getOriginY();
        final int minZ = this.render.getOriginZ();
        buildContext.setupLightPipeline(blockAccess, minX, minY, minZ);

        return super.execute(context, cancellationToken);
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
}
