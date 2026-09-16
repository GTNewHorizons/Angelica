package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import com.gtnewhorizons.angelica.rendering.ArmorTexturePaths;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(RenderBiped.class)
public class MixinRenderBiped_ArmorResource {
    @Shadow @Final private static Map<String, ResourceLocation> field_110859_k/*armorTextureCache*/;

    @Overwrite(remap = false)
    public static ResourceLocation getArmorResource(Entity entity, ItemStack stack, int slot, String type) {
        final ItemArmor item = (ItemArmor) stack.getItem();
        final String prefix = RenderBiped.bipedArmorFilenamePrefix[item.renderIndex];
        final String path = ForgeHooksClient.getArmorTexture(entity, stack, ArmorTexturePaths.path(prefix, item.renderIndex, slot, type), slot, type);
        ResourceLocation rl = field_110859_k/*armorTextureCache*/.get(path);
        if (rl == null) {
            rl = new ResourceLocation(path);
            field_110859_k/*armorTextureCache*/.put(path, rl);
        }
        return rl;
    }
}
