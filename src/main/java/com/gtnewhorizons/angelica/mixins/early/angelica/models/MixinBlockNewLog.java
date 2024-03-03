package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.api.BlockPos;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.QuadView;
import com.gtnewhorizons.angelica.models.template.Column3Rot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockNewLog;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.gtnewhorizons.angelica.models.VanillaModels.*;

@Mixin(BlockNewLog.class)
public abstract class MixinBlockNewLog implements QuadProvider {

    @Override
    public List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

        if (meta > 11) {
            return (switch (meta) {
                case 12 -> ACACIA_WOOD;
                case 13 -> DARK_OAK_WOOD;
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            }).getQuads(world, pos, block, meta, dir, random, color, quadPool);
        }

        Column3Rot ret = switch (meta % 4) {
            case 0 -> ACACIA_LOG;
            case 1 -> DARK_OAK_LOG;
            default -> throw new IllegalStateException("Unexpected value: " + meta);
        };

        return (switch (meta / 4) {
            case 0 -> ret.up();
            case 1 -> ret.x();
            case 2 -> ret.z();
            default -> throw new IllegalStateException("Unexpected value: " + meta);
        }).getQuads(world, pos, block, meta, dir, random, color, quadPool);
    }
}
