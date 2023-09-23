package org.embeddedt.archaicfix.mixins.common.core;

import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ModCandidate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import speiger.src.collections.objects.sets.ObjectOpenHashSet;
import zone.rong.rongasm.api.LoliStringPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/* based on LoliASM */
@Mixin(ModCandidate.class)
public class MixinModCandidate {
    @Shadow(remap = false) private List<String> packages;
    @Shadow(remap = false) private Set<String> foundClasses;
    @Shadow(remap = false) private ASMDataTable table;
    private Set<String> packageSet;

    @Redirect(method = "<init>(Ljava/io/File;Ljava/io/File;Lcpw/mods/fml/common/discovery/ContainerType;ZZ)V", at = @At(value = "FIELD", target = "Lcpw/mods/fml/common/discovery/ModCandidate;packages:Ljava/util/List;", remap = false), remap = false)
    private void avoidPackageList(ModCandidate instance, List<String> value) {
        this.packages = null;
        packageSet = new ObjectOpenHashSet<>();
    }

    /**
     * @author embeddedt
     * @reason more efficient storage
     */
    @Overwrite(remap = false)
    public void addClassEntry(String name) {
        String className = name.substring(0, name.lastIndexOf('.'));
        this.foundClasses.add(className);
        className = className.replace('/','.');
        int pkgIdx = className.lastIndexOf('.');
        if (pkgIdx > -1) {
            String pkg = LoliStringPool.canonicalize(className.substring(0, pkgIdx));
            packageSet.add(pkg);
            table.registerPackage((ModCandidate)(Object)this, pkg);
        }
    }

    /**
     * @author embeddedt
     * @reason avoid storing packages as a list
     */
    @Overwrite(remap = false)
    public List<String> getContainedPackages() {
        return new ArrayList<>(packageSet);
    }
}
