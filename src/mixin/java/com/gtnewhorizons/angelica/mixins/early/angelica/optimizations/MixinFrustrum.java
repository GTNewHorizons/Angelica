package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.renderer.culling.Frustrum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Frustrum.class)
public abstract class MixinFrustrum implements FrustumExtended {
    @Shadow
    public abstract boolean isBoxInFrustum(double p_78548_1_, double p_78548_3_, double p_78548_5_, double p_78548_7_, double p_78548_9_, double p_78548_11_);

    @Override
    public boolean fastAabbTest(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return isBoxInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
