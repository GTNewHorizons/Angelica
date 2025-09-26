package com.gtnewhorizons.angelica.mixins.early.angelica.fontrenderer;

import com.gtnewhorizons.angelica.mixins.interfaces.ResourceAccessor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(Minecraft.class)
public class MixinMCResourceAccessor implements ResourceAccessor {
    @Shadow
    private List defaultResourcePacks;

    @Override
    public List angelica$getDefaultResourcePacks() {
        return defaultResourcePacks;
    }
}
