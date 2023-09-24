package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.gen.structure.MapGenStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;

@Mixin(MapGenStructure.class)
public abstract class MixinMapGenStructure {

    @Redirect(method = { "func_143028_c", "func_142038_b", "func_151545_a", "generateStructuresInChunk" }, at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection getStructureMapValues(Map structureMap) {
        return ImmutableList.copyOf(structureMap.values());
    }
}
