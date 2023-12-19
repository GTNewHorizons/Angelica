package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.renderpass;

import java.util.HashSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.prupe.mcpatcher.renderpass.RenderPass;

/**
 * Note: class is also modified by WorldRendererTransformer
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Shadow
    public boolean[] skipRenderPass;
    @Shadow
    public boolean needsUpdate;
    @Shadow
    public boolean isInitialized;

    @Shadow
    public abstract void setPosition(int x, int y, int z);

    @Inject(method = "<init>(Lnet/minecraft/world/World;Ljava/util/List;IIII)V", at = @At("RETURN"))
    private void modifyWorldRendererConstructor1(World world, List<TileEntity> tileEntities, int x, int y, int z,
        int glRenderList, CallbackInfo ci) {
        skipRenderPass = new boolean[4];
        this.setPosition(x, y, z);
        this.needsUpdate = false;
    }

    /**
     * Cancel first call as skipRenderPass isn't ready yet
     */
    @Redirect(
        method = "<init>(Lnet/minecraft/world/World;Ljava/util/List;IIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;setPosition(III)V"))
    private void modifyWorldRendererConstructor2(WorldRenderer renderer, int x, int y, int z) {}

    @ModifyArg(
        method = "setPosition(III)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V", remap = false),
        index = 0)
    private int modifySetPosition(int list) {
        return list + 2;
    }

    @ModifyConstant(
        method = "updateRenderer(Lnet/minecraft/entity/EntityLivingBase;)V",
        constant = @Constant(intValue = 2))
    private int adjustRenderpassSizeUpdateRenderer(int constant) {
        return 4;
    }

    @Inject(method = "updateRenderer(Lnet/minecraft/entity/EntityLivingBase;)V", at = @At("RETURN"))
    private void finishRenderPassUpdateRenderer(EntityLivingBase entityLivingBase, CallbackInfo ci) {
        RenderPass.finish();
    }

    // Dangerous territory with this amount of locals
    // Idea doesn't like it, but it does work :shrugs:
    @SuppressWarnings({ "InvalidInjectorMethodSignature", "rawtypes" })
    @Inject(
        method = "updateRenderer(Lnet/minecraft/entity/EntityLivingBase;)V",
        at = @At(value = "JUMP", ordinal = 4, shift = At.Shift.AFTER),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectRenderPassStartUpdateRenderer(EntityLivingBase entity, CallbackInfo ci, int i, int j, int k,
        int l, int i1, int j1, HashSet hashset, Minecraft minecraft, EntityLivingBase entity1, int l1, int i2, int j2,
        byte b0, ChunkCache chunkcache, RenderBlocks renderblocks, int k2) {
        RenderPass.start(k2);
    }

    @Redirect(
        method = "updateRenderer(Lnet/minecraft/entity/EntityLivingBase;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;canRenderInPass(I)Z", remap = false))
    private boolean redirectCanRenderInThisPassUpdateRenderer(Block block, int pass) {
        return RenderPass.canRenderInThisPass(block.getRenderBlockPass() == pass);
    }

    /*
     * spotless:off
    In the decompilation the flag2 checked is reversed. However, this crashes the game when entering a world so more
    investigation is necessary

    if (flag2)
    {
        this.postRenderBlocks(k2, p_147892_1_);
    }
    else
    {
        flag1 = false;
    }

    this is achieved by
    @ModifyVariable(method = "updateRenderer(Lnet/minecraft/entity/EntityLivingBase;)V", at = @At(value = "LOAD", ordinal = 1), ordinal = 2)
    private boolean reverseBool1(boolean input) {
        return !input;
    }
    spotless:on
     */

    @ModifyConstant(method = "setDontDraw()V", constant = @Constant(intValue = 2))
    private int modifySetDontDraw(int length) {
        // Initial draw to setPosition runs before skipRenderPass length is changed, if 4 is used it will throw an out
        // of bounds
        return this.skipRenderPass.length;
    }

    @ModifyArg(
        method = "callOcclusionQueryList()V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false),
        index = 0)
    private int modifyCallOcclusionQueryList(int list) {
        return list + 2;
    }

    /**
     * @author Mist475 (adapted from Paul Rupe)
     * @reason entire check is different except null check
     */
    @Overwrite
    public boolean skipAllRenderPasses() {
        return this.isInitialized && RenderPass.skipAllRenderPasses(this.skipRenderPass);
    }
}
