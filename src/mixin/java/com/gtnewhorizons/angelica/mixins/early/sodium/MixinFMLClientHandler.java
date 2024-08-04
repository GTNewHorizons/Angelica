package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.Tags;
import cpw.mods.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Collections;
import java.util.List;

@Mixin(value=FMLClientHandler.class, remap=false)
public class MixinFMLClientHandler {
    /**
     * @author mitchej123
     * @reason Hack in Additional FML Branding
     */
    @Overwrite
    public List<String> getAdditionalBrandingInformation() {
        return Collections.singletonList(String.format("Angelica %s", Tags.VERSION));
    }

}
