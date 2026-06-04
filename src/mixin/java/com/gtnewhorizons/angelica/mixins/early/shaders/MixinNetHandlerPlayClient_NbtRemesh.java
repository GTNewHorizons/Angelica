package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient_NbtRemesh {

    // Needed or TEs will be missed when meshing
    @Inject(method = "handleUpdateTileEntity", at = @At("RETURN"))
    private void angelica$remeshOnTileEntityUpdate(S35PacketUpdateTileEntity packet, CallbackInfo ci) {
        final NbtConditionalIdMap<Block> teMap = BlockRenderingSettings.INSTANCE.getBlockNbtMap();
        if (teMap == null || teMap.isEmpty()) {
            return;
        }

        final World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return;
        }

        final int x = packet.func_148856_c/*getX*/() ;
        final int y = packet.func_148855_d/*getY*/();
        final int z = packet.func_148854_e/*getZ*/();
        final Block block = world.getBlock(x, y, z);
        if (block == null || !teMap.hasConditions(block)) {
            return;
        }

        world.markBlockForUpdate(x, y, z);
    }
}
