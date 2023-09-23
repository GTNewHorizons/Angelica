package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.util.ObjectIntIdentityMap;
import org.embeddedt.archaicfix.helpers.UnexpectionalObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("rawtypes")
@Mixin(ObjectIntIdentityMap.class)
public class MixinObjectIntIdentityMap {

    @Shadow protected List field_148748_b;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initIdArray(CallbackInfo ci) {
        this.field_148748_b = new UnexpectionalObjectArrayList();
    }

    /**
     * @author embeddedt
     * @reason Avoid unnecessary range checks
     * @param id ID
     * @return object if ID is valid, else null
     */
    @Overwrite
    public Object func_148745_a(int id) {
        return ((UnexpectionalObjectArrayList)field_148748_b).getOrNull(id);
    }
}
