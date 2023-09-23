package org.embeddedt.archaicfix.mixins.client.occlusion;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.shader.TesselatorVertexState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import org.embeddedt.archaicfix.occlusion.IWorldRenderer;
import org.embeddedt.archaicfix.occlusion.OcclusionWorker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer implements IWorldRenderer {
    @Shadow public boolean isWaitingOnOcclusionQuery;

    @Shadow public World worldObj;

    @Shadow public int posX;

    @Shadow public int posZ;

    @Shadow public List tileEntityRenderers;

    @Shadow private List tileEntities;

    @Shadow public boolean needsUpdate;

    @Shadow public boolean isInitialized;

    @Shadow private int bytesDrawn;

    @Shadow private TesselatorVertexState vertexState;

    private boolean arch$isInUpdateList;
    private boolean arch$isFrustumCheckPending;

    private OcclusionWorker.CullInfo arch$cullInfo;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.arch$cullInfo = new OcclusionWorker.CullInfo();
    }

    @Inject(method = "markDirty", at = @At("TAIL"))
    private void resetOcclusionFlag(CallbackInfo ci) {
        this.isWaitingOnOcclusionQuery = false;
    }

    @Inject(method = "updateRenderer", at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC, target = "Lnet/minecraft/world/chunk/Chunk;isLit:Z", ordinal = 0), cancellable = true)
    private void bailOnEmptyChunk(EntityLivingBase view, CallbackInfo ci) {
        if(worldObj.getChunkFromBlockCoords(posX, posZ) instanceof EmptyChunk) {
            if (tileEntityRenderers.size() > 0) {
                tileEntities.removeAll(tileEntityRenderers);
                tileEntityRenderers.clear();
            }
            needsUpdate = true;
            isInitialized = false;
            bytesDrawn = 0;
            vertexState = null;
            ci.cancel();
        }
    }

    @Override
    public boolean arch$isInUpdateList() {
        return arch$isInUpdateList;
    }

    @Override
    public void arch$setInUpdateList(boolean b) {
        arch$isInUpdateList = b;
    }

    @Override
    public OcclusionWorker.CullInfo arch$getCullInfo() {
        return arch$cullInfo;
    }
}
