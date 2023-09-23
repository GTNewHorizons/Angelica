package org.embeddedt.archaicfix.mixins.common.core;

import cpw.mods.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zone.rong.rongasm.api.LoliStringPool;

@Mixin(value = ASMDataTable.ASMData.class, remap = false)
public class MixinASMData {
    @Shadow private String annotationName;

    @Shadow private String className;

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lcpw/mods/fml/common/discovery/ASMDataTable$ASMData;annotationName:Ljava/lang/String;"))
    private void canonicalizeAnnotation(ASMDataTable.ASMData instance, String value) {
        this.annotationName = value == null ? null : LoliStringPool.canonicalize(value);
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lcpw/mods/fml/common/discovery/ASMDataTable$ASMData;className:Ljava/lang/String;"))
    private void canonicalizeClassName(ASMDataTable.ASMData instance, String value) {
        this.className = value == null ? null : LoliStringPool.canonicalize(value);
    }
}
