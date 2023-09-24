package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedList;

@Mixin(StructureStart.class)
public class MixinStructureStart {
    @Redirect(method = "generateStructure", at = @At(value = "FIELD", target = "Lnet/minecraft/world/gen/structure/StructureStart;components:Ljava/util/LinkedList;", ordinal = 0))
    private LinkedList<StructureComponent> debugStructureCME(StructureStart instance) {
        return new LinkedList<>((LinkedList<StructureComponent>)instance.getComponents());
    }
}
