package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;

import java.util.Collection;

final class SurroundInjectionPoint extends InjectionPoint {

    @Override
    public boolean checkPriority(int targetPriority, int ownerPriority) {
        return true;
    }

    @Override
    public boolean find(String desc, InsnList insns, Collection<AbstractInsnNode> nodes) {
        return nodes.add(insns.getFirst());
    }
}
