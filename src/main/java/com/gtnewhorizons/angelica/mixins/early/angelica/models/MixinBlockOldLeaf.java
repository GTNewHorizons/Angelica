package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.utils.AssetLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.gtnewhorizons.angelica.models.VanillaModels.*;

@Mixin(BlockOldLeaf.class)
public abstract class MixinBlockOldLeaf implements QuadProvider {

    @Inject(method = "registerBlockIcons", at = @At(value = "TAIL"))
    public void angelica$injectTextures(IIconRegister reg, CallbackInfo ci) {
        for (String s : AssetLoader.texs) {
            reg.registerIcon(s);
        }
    }

    @Override
    public int getColor(IBlockAccess world, BlockPos pos, Block block, int meta, Random random) {
        return QuadProvider.getDefaultColor(world, pos, block);
    }

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        return (switch (meta % 4) {
            case 0 -> OAK_LEAVES;
            case 1 -> SPRUCE_LEAVES;
            case 2 -> BIRCH_LEAVES;
            case 3 -> JUNGLE_LEAVES;
            default -> throw new IllegalStateException("Unexpected value: " + meta);
        }).getQuads(world, pos, block, meta, dir, random, color, quadPool);
    }
}
