package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedList;
import java.util.Random;

@Mixin(StructureStart.class)
public class MixinStructureStart {
    @Redirect(method = "generateStructure", at = @At(value = "FIELD", target = "Lnet/minecraft/world/gen/structure/StructureStart;components:Ljava/util/LinkedList;", ordinal = 0))
    private LinkedList<StructureComponent> debugStructureCME(StructureStart instance) {
        return new LinkedList<>((LinkedList<StructureComponent>)instance.getComponents());
    }
}
