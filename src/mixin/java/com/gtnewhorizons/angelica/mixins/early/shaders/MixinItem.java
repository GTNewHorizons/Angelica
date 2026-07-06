package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public class MixinItem implements IrisItemLightProvider {

}
