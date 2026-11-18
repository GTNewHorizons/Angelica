package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Exits;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;

record CallSiteSpec(MethodNode target, InjectionNode node, MethodNode entry, String entryDesc, Type[] operands, Type result, Exits exits, String description, int[] entryLocals, int[][] locals) {}
