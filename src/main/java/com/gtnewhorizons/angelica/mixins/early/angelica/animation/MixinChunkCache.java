package com.gtnewhorizons.angelica.mixins.early.angelica.animation;

import com.gtnewhorizons.angelica.mixins.interfaces.ITexturesCache;
import net.minecraft.util.IIcon;
import net.minecraft.world.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;

@Mixin(ChunkCache.class)
public class MixinChunkCache implements ITexturesCache {

    @Unique
    private final HashSet<IIcon> renderedIcons = new HashSet<>();

    @Override
    public HashSet<IIcon> getRenderedTextures() {
        return renderedIcons;
    }
}
