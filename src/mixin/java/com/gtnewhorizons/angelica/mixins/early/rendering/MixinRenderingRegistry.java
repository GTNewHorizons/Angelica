package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import com.gtnewhorizons.angelica.rendering.IsbrhDispatch;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = RenderingRegistry.class, remap = false)
public class MixinRenderingRegistry implements IRenderingRegistryExt {
    @Shadow private Map<Integer, ISimpleBlockRenderingHandler> blockRenderers;

    @Override
    public ISimpleBlockRenderingHandler getISBRH(int modelId) {
        return this.blockRenderers.get(modelId);
    }

    /**
     * @author Angelica
     * @reason zero-alloc dispatch
     */
    @Overwrite
    public boolean renderWorldBlock(RenderBlocks renderer, IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        ISimpleBlockRenderingHandler h = IsbrhDispatch.resolve(this.blockRenderers, modelId);
        return h != null && IsbrhDispatch.renderWorldBlock(h, renderer, world, x, y, z, block, modelId);
    }

    @Surround(method = { "renderInventoryBlock", "renderItemAsFull3DBlock" }, at = @At(value="INVOKE", target="Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private void angelica$resolveHandler() {
        @Surround.Skip final boolean skip = true;
    }

    @Surround.Skipped
    private Object angelica$resolvedHandler(Map<Integer, ISimpleBlockRenderingHandler> map, Object modelId) {
        return IsbrhDispatch.resolve(map, (Integer) modelId);
    }

    @Inject(method = {
        "registerBlockHandler(Lcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V",
        "registerBlockHandler(ILcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V" }, at = @At("TAIL"))
    private static void angelica$invalidateOnRegister(CallbackInfo ci) {
        IsbrhDispatch.invalidate();
    }
}
