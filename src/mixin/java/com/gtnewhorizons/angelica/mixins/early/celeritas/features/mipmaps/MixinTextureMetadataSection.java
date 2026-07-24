package com.gtnewhorizons.angelica.mixins.early.celeritas.features.mipmaps;

import com.gtnewhorizons.angelica.mixins.interfaces.TextureMetadataExtension;
import com.gtnewhorizons.angelica.utils.MipmapStrategy;
import net.minecraft.client.resources.data.TextureMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TextureMetadataSection.class)
public class MixinTextureMetadataSection implements TextureMetadataExtension {

    @Unique
    private MipmapStrategy angelica$mipmapStrategy;

    @Override
    public void angelica$setMipmapStrategy(MipmapStrategy strategy) {
        this.angelica$mipmapStrategy = strategy;
    }

    @Override
    public MipmapStrategy angelica$getMipmapStrategy() {
        return this.angelica$mipmapStrategy;
    }
}
