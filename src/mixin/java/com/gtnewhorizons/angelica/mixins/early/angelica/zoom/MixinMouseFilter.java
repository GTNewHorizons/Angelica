package com.gtnewhorizons.angelica.mixins.early.angelica.zoom;

import com.gtnewhorizons.angelica.zoom.IMouseFilterExt;
import net.minecraft.util.MouseFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MouseFilter.class)
public class MixinMouseFilter implements IMouseFilterExt {

    @Shadow
    private float field_76336_a;

    @Shadow
    private float field_76334_b;

    @Shadow
    private float field_76335_c;

    @Override
    public void angelica$reset() {
        this.field_76336_a = 0.0F;
        this.field_76334_b = 0.0F;
        this.field_76335_c = 0.0F;
    }
}
