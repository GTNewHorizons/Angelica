package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.item;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemBlock.class)
public abstract class MixinItemBlock extends Item {

    @Final
    @Shadow
    public Block field_150939_a;

    @Override
    public int getColorFromItemStack(final ItemStack itemStack, final int meta) {
        final Block block = this.field_150939_a;
        if (block != null) {
            return block.getRenderColor(meta);
        }
        return super.getColorFromItemStack(itemStack, meta);
    }
}
